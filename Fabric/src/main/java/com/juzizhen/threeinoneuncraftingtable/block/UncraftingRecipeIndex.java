package com.juzizhen.threeinoneuncraftingtable.block;

import com.juzizhen.threeinoneuncraftingtable.ThreeInOneUncraftingTable;
import com.juzizhen.threeinoneuncraftingtable.mixin.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProvidesTrimMaterialComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.trim.ArmorTrimMaterial;
import net.minecraft.item.equipment.trim.ArmorTrimPattern;
import net.minecraft.recipe.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「产物物品 -> 可拆解配方」索引。
 * 原实现在每次输入变化/补货时全量遍历三类配方，且每个配方调用 getResult 新建 ItemStack；
 * 这里在配方加载后一次性构建索引并缓存配方结果，查询时不再分配任何 ItemStack。
 * 配方管理器（RecipeManager）在每次数据包重载时会被服务器重建，用其实例身份做缓存失效。
 * 1.21.2+ 纹饰系统重构适配：smithing_trim 按图案拆成多个配方，且 ArmorTrimPattern 不再持有模板物品、
 * ArmorTrimMaterial 不再持有原料物品，这里额外构建「纹饰图案 -> 锻造模板物品」「纹饰材料 -> 材料物品」
 * 两张反查表供拆解还原使用（材料经 PROVIDES_TRIM_MATERIAL 组件反查物品注册表）。
 */
public class UncraftingRecipeIndex {
    // 脚本配方模组添加配方的默认命名空间；适配其他同款模组时在此追加命名空间与对应开关即可
    private static final String KUBEJS_NAMESPACE = "kubejs";
    private static final String CRAFTTWEAKER_NAMESPACE = "crafttweaker";
    private static ServerRecipeManager cachedRecipeManager;
    private static UncraftingRecipeIndex cachedIndex;
    private final Map<Item, List<Indexed>> byResultItem = new HashMap<>();
    private final List<RecipeEntry<?>> trimRecipes = new ArrayList<>();
    // 纹饰反查表：纹饰图案 -> 锻造模板物品；纹饰材料 -> 提供该材料的物品
    private final Map<RegistryKey<ArmorTrimPattern>, ItemStack> templateByPattern = new HashMap<>();
    private final Map<RegistryKey<ArmorTrimMaterial>, Item> materialItemByKey = new HashMap<>();

    public static UncraftingRecipeIndex get(ServerWorld world) {
        ServerRecipeManager recipeManager = world.getRecipeManager();
        if (cachedIndex == null || cachedRecipeManager != recipeManager) {
            cachedIndex = build(world, recipeManager);
            cachedRecipeManager = recipeManager;
        }
        return cachedIndex;
    }

    private static UncraftingRecipeIndex build(ServerWorld world, ServerRecipeManager recipeManager) {
        UncraftingRecipeIndex index = new UncraftingRecipeIndex();

        List<RecipeEntry<?>> craftingRecipes = new ArrayList<>();
        List<RecipeEntry<?>> smithingRecipes = new ArrayList<>();
        List<RecipeEntry<?>> stonecuttingRecipes = new ArrayList<>();

        // 1.21.11：listAllOfType 已移除，改为遍历 values() 并按配方类型分桶
        for (RecipeEntry<?> recipeEntry : recipeManager.values()) {
            Recipe<?> recipe = recipeEntry.value();

            // 带纹饰的物品只匹配锻造纹饰配方：收集全部纹饰配方，匹配时返回第一个被开关允许的
            if (recipe instanceof SmithingTrimRecipe trimRecipe) {
                index.trimRecipes.add(recipeEntry);
                index.recordTemplateItem(recipeEntry, trimRecipe);
            }

            if (recipe.getType() == RecipeType.CRAFTING) {
                craftingRecipes.add(recipeEntry);
            } else if (recipe.getType() == RecipeType.SMITHING) {
                smithingRecipes.add(recipeEntry);
            } else if (recipe.getType() == RecipeType.STONECUTTING) {
                stonecuttingRecipes.add(recipeEntry);
            }
        }

        // 按原扫描顺序建索引：合成 → 锻造 → 切石，保证匹配结果的顺序与原实现一致
        index.addAll(craftingRecipes);
        index.addAll(smithingRecipes);
        index.addAll(stonecuttingRecipes);
        index.buildMaterialItemMap(world.getRegistryManager());
        return index;
    }

    /**
     * 1.21.11：Recipe 接口不再有 getResult，按配方类型经 Accessor 读取各自的结果字段
     */
    private static ItemStack getResult(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe) {
            return ((ShapedResultAccessor) recipe).getResult().copy();
        }
        if (recipe instanceof ShapelessRecipe) {
            return ((ShapelessResultAccessor) recipe).getResult().copy();
        }
        if (recipe instanceof SmithingTrimRecipe) {
            // 锻造纹饰配方的产物取决于输入装备，与原版一致以空堆参与索引（不匹配任何实际输入）
            return ItemStack.EMPTY;
        }
        if (recipe instanceof SmithingTransformRecipe) {
            TransmuteRecipeResult result = ((SmithingTransformRecipeAccessor) recipe).getResult();
            ItemStack stack = new ItemStack(result.itemEntry().value(), result.count());
            if (!result.components().isEmpty()) {
                stack.applyChanges(result.components());
            }
            return stack;
        }
        if (recipe instanceof SingleStackRecipe) {
            return ((SingleStackRecipeAccessor) recipe).getResult().copy();
        }
        // 特殊合成配方（染色、烟花等）无固定产物，与原版一致以空堆参与索引
        return ItemStack.EMPTY;
    }

    /**
     * 脚本配方兼容过滤：对应命名空间的配方仅在配置开关开启时参与拆解，其余配方（原版/其他模组原生）不受影响
     */
    private static boolean isScriptRecipeAllowed(String namespace) {
        if (KUBEJS_NAMESPACE.equals(namespace)) {
            return ThreeInOneUncraftingTable.CONFIG.enableKubeJSRecipes;
        }
        if (CRAFTTWEAKER_NAMESPACE.equals(namespace)) {
            return ThreeInOneUncraftingTable.CONFIG.enableCraftTweakerRecipes;
        }
        return true;
    }

    private void addAll(List<RecipeEntry<?>> recipeEntries) {
        for (RecipeEntry<?> recipeEntry : recipeEntries) {
            // 配方结果仅在索引构建时计算一次并缓存，避免每次扫描重复分配 ItemStack
            ItemStack result = getResult(recipeEntry.value());
            byResultItem.computeIfAbsent(result.getItem(), item -> new ArrayList<>()).add(new Indexed(recipeEntry, result));
        }
    }

    /**
     * 记录纹饰配方「图案 -> 模板物品」反查项：取配方模板原料中的首个物品（被开关排除的脚本配方不参与）
     */
    @SuppressWarnings("deprecation") // getMatchingItems() 在 1.21.11 是读取模板原料首个物品的唯一可用途径，无未弃用替代
    private void recordTemplateItem(RecipeEntry<?> recipeEntry, SmithingTrimRecipe recipe) {
        if (!isScriptRecipeAllowed(recipeEntry.id().getValue().getNamespace())) return;
        RegistryEntry<ArmorTrimPattern> pattern = ((SmithingTrimRecipePatternAccessor) recipe).getPattern();
        pattern.getKey().ifPresent(patternKey ->
                recipe.template().flatMap(template -> template.getMatchingItems().findFirst()).ifPresent(holder ->
                        templateByPattern.putIfAbsent(patternKey, new ItemStack(holder.value()))));
    }

    /**
     * 构建「纹饰材料 -> 提供该材料的物品」反查表：遍历物品注册表读取 PROVIDES_TRIM_MATERIAL 组件
     */
    private void buildMaterialItemMap(RegistryWrapper.WrapperLookup registries) {
        for (Item item : Registries.ITEM) {
            ProvidesTrimMaterialComponent provides = item.getComponents().get(DataComponentTypes.PROVIDES_TRIM_MATERIAL);
            if (provides != null) {
                provides.getMaterial(registries).flatMap(RegistryEntry::getKey).ifPresent(materialKey ->
                        materialItemByKey.putIfAbsent(materialKey, item));
            }
        }
    }

    /**
     * 按物品实际纹饰图案反查锻造模板物品；无对应配方时返回 null
     */
    public ItemStack getTemplateItemStack(RegistryEntry<ArmorTrimPattern> pattern) {
        return pattern.getKey().map(templateByPattern::get).orElse(null);
    }

    /**
     * 按物品实际纹饰材料反查提供该材料的物品；无对应物品时返回 null
     */
    public Item getMaterialItem(RegistryEntry<ArmorTrimMaterial> material) {
        return material.getKey().map(materialItemByKey::get).orElse(null);
    }

    /**
     * 首个被开关允许的锻造纹饰配方；无配方或全部被排除时返回 null
     */
    public RecipeEntry<?> getFirstTrimRecipe() {
        for (RecipeEntry<?> recipeEntry : trimRecipes) {
            if (isScriptRecipeAllowed(recipeEntry.id().getValue().getNamespace())) {
                return recipeEntry;
            }
        }
        return null;
    }

    /**
     * 保留原 ItemStack.areItemsEqual 匹配语义（物品 + 组件均相等）；被开关排除的脚本配方不参与
     */
    public void collectMatching(ItemStack input, List<RecipeEntry<?>> out) {
        List<Indexed> candidates = byResultItem.get(input.getItem());
        if (candidates == null) return;
        for (Indexed indexed : candidates) {
            if (!isScriptRecipeAllowed(indexed.entry().id().getValue().getNamespace())) continue;
            if (ItemStack.areItemsEqual(input, indexed.result())) {
                out.add(indexed.entry());
            }
        }
    }

    private record Indexed(RecipeEntry<?> entry, ItemStack result) {
    }
}
