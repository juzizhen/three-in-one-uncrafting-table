package com.juzizhen.threeinoneuncraftingtable.block;

import com.juzizhen.threeinoneuncraftingtable.config.ModConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ProvidesTrimMaterial;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 「产物物品 -> 可拆解配方」索引。
 * 原实现在每次输入变化/补货时全量遍历三类配方，且每个配方调用 getResultItem 新建 ItemStack；
 * 这里在配方加载后一次性构建索引并缓存配方结果，查询时不再分配任何 ItemStack。
 * 配方管理器（RecipeManager）在每次数据包重载时会被服务器重建，用其实例身份做缓存失效。
 * <p>
 * 1.21.2+ 配方系统重构适配说明：
 * - RecipeManager#getAllRecipesFor 已移除，改用 NeoForge 暴露的 RecipeMap#byType（按配方类型取全量集合，顺序与原实现一致）。
 * - Recipe#getResultItem(RegistryAccess) 已移除，改经 AccessTransformer 直读各配方的 result 字段。
 * - RecipeHolder#id() 返回 ResourceKey&lt;Recipe&lt;?&gt;&gt;，命名空间经 id().identifier().getNamespace() 读取。
 * - 1.21.5+ 纹饰系统重构：TrimPattern 不再有 templateItem、TrimMaterial 不再有 ingredient，
 * 这里额外构建「纹饰图案 -> 锻造模板物品」「纹饰材料 -> 材料物品」两张反查表供拆解还原使用。
 */
public class UncraftingRecipeIndex {
    // 脚本配方模组添加配方的默认命名空间；适配其他同款模组时在此追加命名空间与对应开关即可
    private static final String KUBEJS_NAMESPACE = "kubejs";
    private static final String CRAFTTWEAKER_NAMESPACE = "crafttweaker";

    // 首次访问前为 null，显式标注可空（包处于 @NullMarked 作用域）
    private static @Nullable RecipeManager cachedRecipeManager;
    private static @Nullable UncraftingRecipeIndex cachedIndex;

    private final Map<Item, List<RecipeHolder<?>>> byResultItem = new HashMap<>();
    private final List<RecipeHolder<?>> trimRecipes = new ArrayList<>();
    // 纹饰反查表：纹饰图案 -> 锻造模板物品；纹饰材料 -> 提供该材料的物品
    private final Map<ResourceKey<TrimPattern>, ItemStack> templateByPattern = new HashMap<>();
    private final Map<ResourceKey<TrimMaterial>, ItemStack> materialItemByKey = new HashMap<>();

    public static UncraftingRecipeIndex get(ServerLevel level) {
        RecipeManager recipeManager = level.recipeAccess();
        if (cachedIndex == null || cachedRecipeManager != recipeManager) {
            cachedIndex = build(level, recipeManager);
            cachedRecipeManager = recipeManager;
        }
        return cachedIndex;
    }

    private static UncraftingRecipeIndex build(ServerLevel level, RecipeManager recipeManager) {
        UncraftingRecipeIndex index = new UncraftingRecipeIndex();

        // 带纹饰的物品只匹配锻造纹饰配方：收集全部纹饰配方，匹配时返回第一个被开关允许的
        for (RecipeHolder<SmithingRecipe> holder : recipeManager.recipeMap().byType(RecipeType.SMITHING)) {
            if (holder.value() instanceof SmithingTrimRecipe trimRecipe) {
                index.trimRecipes.add(holder);
                index.recordTemplateItem(trimRecipe);
            }
        }

        // 按原扫描顺序建索引：合成 → 锻造 → 切石，保证匹配结果的顺序与原实现一致
        index.addAll(recipeManager.recipeMap().byType(RecipeType.CRAFTING));
        index.addAll(recipeManager.recipeMap().byType(RecipeType.SMITHING));
        index.addAll(recipeManager.recipeMap().byType(RecipeType.STONECUTTING));

        index.buildMaterialItemMap(level);
        return index;
    }

    /**
     * 经 AccessTransformer 直读各配方类型的 result 字段，返回产物物品；无法识别的配方类型返回 null（不建索引）
     */
    private static @Nullable Item resultItemOf(Recipe<?> recipe) {
        return switch (recipe) {
            case ShapedRecipe shaped -> shaped.result.getItem();
            case ShapelessRecipe shapeless -> shapeless.result.getItem();
            case SingleItemRecipe single -> single.result.getItem();
            case SmithingTransformRecipe transform -> transform.result.item().value();
            default -> null;
        };
    }

    /**
     * 脚本配方兼容过滤：对应命名空间的配方仅在配置开关开启时参与拆解，其余配方（原版/其他模组原生）不受影响
     */
    private static boolean isScriptRecipeAllowed(String namespace) {
        if (KUBEJS_NAMESPACE.equals(namespace)) {
            return ModConfig.ENABLE_KUBEJS_RECIPES.get();
        }
        if (CRAFTTWEAKER_NAMESPACE.equals(namespace)) {
            return ModConfig.ENABLE_CRAFTTWEAKER_RECIPES.get();
        }
        return true;
    }

    private void addAll(Collection<? extends RecipeHolder<? extends Recipe<?>>> holders) {
        for (RecipeHolder<? extends Recipe<?>> holder : holders) {
            // 配方结果物品仅在索引构建时计算一次并缓存，避免每次扫描重复分配 ItemStack
            Item resultItem = resultItemOf(holder.value());
            if (resultItem != null) {
                byResultItem.computeIfAbsent(resultItem, item -> new ArrayList<>()).add(holder);
            }
        }
    }

    /**
     * 记录纹饰配方「图案 -> 模板物品」反查项：取配方模板原料中的首个物品
     */
    @SuppressWarnings("deprecation") // items() 在 1.21.11 是读取模板原料首个物品的唯一可用途径，无未弃用替代
    private void recordTemplateItem(SmithingTrimRecipe recipe) {
        recipe.pattern.unwrapKey().ifPresent(patternKey ->
                recipe.templateIngredient().flatMap(template -> template.items().findFirst()).ifPresent(holder ->
                        templateByPattern.putIfAbsent(patternKey, new ItemStack(holder.value()))));
    }

    /**
     * 构建「纹饰材料 -> 提供该材料的物品」反查表：遍历物品注册表读取 PROVIDES_TRIM_MATERIAL 组件
     */
    private void buildMaterialItemMap(ServerLevel level) {
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack probe = new ItemStack(item);
            ProvidesTrimMaterial provides = probe.get(DataComponents.PROVIDES_TRIM_MATERIAL);
            if (provides != null) {
                provides.unwrap(level.registryAccess()).flatMap(Holder::unwrapKey).ifPresent(materialKey ->
                        materialItemByKey.putIfAbsent(materialKey, new ItemStack(item)));
            }
        }
    }

    /**
     * 按物品实际纹饰图案反查锻造模板物品；无对应配方时返回 null
     */
    public @Nullable ItemStack getTemplateItemStack(Holder<TrimPattern> pattern) {
        return pattern.unwrapKey().map(templateByPattern::get).orElse(null);
    }

    /**
     * 按物品实际纹饰材料反查提供该材料的物品；无对应物品时返回 null
     */
    public @Nullable ItemStack getMaterialItemStack(Holder<TrimMaterial> material) {
        return material.unwrapKey().map(materialItemByKey::get).orElse(null);
    }

    /**
     * 首个被开关允许的锻造纹饰配方；无配方或全部被排除时返回 null（包级 @NullMarked 下需显式标注）
     */
    public @Nullable RecipeHolder<?> getFirstTrimRecipe() {
        for (RecipeHolder<?> holder : trimRecipes) {
            if (isScriptRecipeAllowed(holder.id().identifier().getNamespace())) {
                return holder;
            }
        }
        return null;
    }

    /**
     * 保留原 input.is(产物物品) 匹配语义（仅比较物品类型）；被开关排除的脚本配方不参与
     */
    public void collectMatching(ItemStack input, List<RecipeHolder<?>> out) {
        List<RecipeHolder<?>> candidates = byResultItem.get(input.getItem());
        if (candidates == null) return;
        for (RecipeHolder<?> holder : candidates) {
            if (!isScriptRecipeAllowed(holder.id().identifier().getNamespace())) continue;
            out.add(holder);
        }
    }
}
