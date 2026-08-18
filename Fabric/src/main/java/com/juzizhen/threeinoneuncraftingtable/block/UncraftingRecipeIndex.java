package com.juzizhen.threeinoneuncraftingtable.block;

import com.juzizhen.threeinoneuncraftingtable.ThreeInOneUncraftingTable;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmithingRecipe;
import net.minecraft.recipe.SmithingTrimRecipe;
import net.minecraft.registry.RegistryWrapper;
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
 */
public class UncraftingRecipeIndex {
    private record Indexed(RecipeEntry<?> entry, ItemStack result) {
    }

    // 脚本配方模组添加配方的默认命名空间；适配其他同款模组时在此追加命名空间与对应开关即可
    private static final String KUBEJS_NAMESPACE = "kubejs";
    private static final String CRAFTTWEAKER_NAMESPACE = "crafttweaker";

    private static RecipeManager cachedRecipeManager;
    private static UncraftingRecipeIndex cachedIndex;

    private final Map<Item, List<Indexed>> byResultItem = new HashMap<>();
    private final List<RecipeEntry<?>> trimRecipes = new ArrayList<>();

    public static UncraftingRecipeIndex get(ServerWorld world) {
        RecipeManager recipeManager = world.getRecipeManager();
        if (cachedIndex == null || cachedRecipeManager != recipeManager) {
            cachedIndex = build(world, recipeManager);
            cachedRecipeManager = recipeManager;
        }
        return cachedIndex;
    }

    private static UncraftingRecipeIndex build(ServerWorld world, RecipeManager recipeManager) {
        UncraftingRecipeIndex index = new UncraftingRecipeIndex();
        RegistryWrapper.WrapperLookup registryAccess = world.getRegistryManager();

        // 带纹饰的物品只匹配锻造纹饰配方：收集全部纹饰配方，匹配时返回第一个被开关允许的
        for (RecipeEntry<SmithingRecipe> recipeEntry : recipeManager.listAllOfType(RecipeType.SMITHING)) {
            if (recipeEntry.value() instanceof SmithingTrimRecipe) {
                index.trimRecipes.add(recipeEntry);
            }
        }

        // 按原扫描顺序建索引：合成 → 锻造 → 切石，保证匹配结果的顺序与原实现一致
        index.addAll(recipeManager.listAllOfType(RecipeType.CRAFTING), registryAccess);
        index.addAll(recipeManager.listAllOfType(RecipeType.SMITHING), registryAccess);
        index.addAll(recipeManager.listAllOfType(RecipeType.STONECUTTING), registryAccess);
        return index;
    }

    private void addAll(List<? extends RecipeEntry<? extends Recipe<?>>> recipeEntries, RegistryWrapper.WrapperLookup registryAccess) {
        for (RecipeEntry<? extends Recipe<?>> recipeEntry : recipeEntries) {
            // 配方结果仅在索引构建时计算一次并缓存，避免每次扫描重复分配 ItemStack
            ItemStack result = recipeEntry.value().getResult(registryAccess);
            byResultItem.computeIfAbsent(result.getItem(), item -> new ArrayList<>()).add(new Indexed(recipeEntry, result));
        }
    }

    /** 首个被开关允许的锻造纹饰配方；无配方或全部被排除时返回 null */
    public RecipeEntry<?> getFirstTrimRecipe() {
        for (RecipeEntry<?> recipeEntry : trimRecipes) {
            if (isScriptRecipeAllowed(recipeEntry.id().getNamespace())) {
                return recipeEntry;
            }
        }
        return null;
    }

    /** 保留原 ItemStack.areItemsEqual 匹配语义（物品 + 组件均相等）；被开关排除的脚本配方不参与 */
    public void collectMatching(ItemStack input, List<RecipeEntry<?>> out) {
        List<Indexed> candidates = byResultItem.get(input.getItem());
        if (candidates == null) return;
        for (Indexed indexed : candidates) {
            if (!isScriptRecipeAllowed(indexed.entry().id().getNamespace())) continue;
            if (ItemStack.areItemsEqual(input, indexed.result())) {
                out.add(indexed.entry());
            }
        }
    }

    /** 脚本配方兼容过滤：对应命名空间的配方仅在配置开关开启时参与拆解，其余配方（原版/其他模组原生）不受影响 */
    private static boolean isScriptRecipeAllowed(String namespace) {
        if (KUBEJS_NAMESPACE.equals(namespace)) {
            return ThreeInOneUncraftingTable.CONFIG.enableKubeJSRecipes;
        }
        if (CRAFTTWEAKER_NAMESPACE.equals(namespace)) {
            return ThreeInOneUncraftingTable.CONFIG.enableCraftTweakerRecipes;
        }
        return true;
    }
}
