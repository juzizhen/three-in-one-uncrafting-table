package com.juzizhen.threeinoneuncraftingtable.block;

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

    private static RecipeManager cachedRecipeManager;
    private static UncraftingRecipeIndex cachedIndex;

    private final Map<Item, List<Indexed>> byResultItem = new HashMap<>();
    private RecipeEntry<?> firstTrimRecipe;

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

        // 保留原逻辑：带纹饰的物品只匹配迭代顺序中的第一个锻造纹饰配方
        for (RecipeEntry<SmithingRecipe> recipeEntry : recipeManager.listAllOfType(RecipeType.SMITHING)) {
            if (recipeEntry.value() instanceof SmithingTrimRecipe) {
                index.firstTrimRecipe = recipeEntry;
                break;
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

    public RecipeEntry<?> getFirstTrimRecipe() {
        return firstTrimRecipe;
    }

    /** 保留原 ItemStack.areItemsEqual 匹配语义（物品 + 组件均相等） */
    public void collectMatching(ItemStack input, List<RecipeEntry<?>> out) {
        List<Indexed> candidates = byResultItem.get(input.getItem());
        if (candidates == null) return;
        for (Indexed indexed : candidates) {
            if (ItemStack.areItemsEqual(input, indexed.result())) {
                out.add(indexed.entry());
            }
        }
    }
}
