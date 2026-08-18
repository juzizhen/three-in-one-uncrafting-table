package com.juzizhen.threeinoneuncraftingtable.block;

import com.juzizhen.threeinoneuncraftingtable.config.ModConfig;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「产物物品 -> 可拆解配方」索引。
 * 原实现在每次输入变化/补货时全量遍历三类配方，且每个配方调用 getResultItem 新建 ItemStack；
 * 这里在配方加载后一次性构建索引并缓存配方结果，查询时不再分配任何 ItemStack。
 * 配方管理器（RecipeManager）在每次数据包重载时会被服务器重建，用其实例身份做缓存失效。
 */
public class UncraftingRecipeIndex {
    // 脚本配方模组添加配方的默认命名空间；适配其他同款模组时在此追加命名空间与对应开关即可
    private static final String KUBEJS_NAMESPACE = "kubejs";
    private static final String CRAFTTWEAKER_NAMESPACE = "crafttweaker";

    private static RecipeManager cachedRecipeManager;
    private static UncraftingRecipeIndex cachedIndex;

    private final Map<Item, List<RecipeHolder<?>>> byResultItem = new HashMap<>();
    private final List<RecipeHolder<?>> trimRecipes = new ArrayList<>();

    public static UncraftingRecipeIndex get(ServerLevel level) {
        RecipeManager recipeManager = level.getRecipeManager();
        if (cachedIndex == null || cachedRecipeManager != recipeManager) {
            cachedIndex = build(level, recipeManager);
            cachedRecipeManager = recipeManager;
        }
        return cachedIndex;
    }

    private static UncraftingRecipeIndex build(ServerLevel level, RecipeManager recipeManager) {
        UncraftingRecipeIndex index = new UncraftingRecipeIndex();
        RegistryAccess registryAccess = level.registryAccess();

        // 带纹饰的物品只匹配锻造纹饰配方：收集全部纹饰配方，匹配时返回第一个被开关允许的
        for (RecipeHolder<SmithingRecipe> holder : recipeManager.getAllRecipesFor(RecipeType.SMITHING)) {
            if (holder.value() instanceof SmithingTrimRecipe) {
                index.trimRecipes.add(holder);
            }
        }

        // 按原扫描顺序建索引：合成 → 锻造 → 切石，保证匹配结果的顺序与原实现一致
        index.addAll(recipeManager.getAllRecipesFor(RecipeType.CRAFTING), registryAccess);
        index.addAll(recipeManager.getAllRecipesFor(RecipeType.SMITHING), registryAccess);
        index.addAll(recipeManager.getAllRecipesFor(RecipeType.STONECUTTING), registryAccess);
        return index;
    }

    private void addAll(Collection<? extends RecipeHolder<? extends Recipe<?>>> holders, RegistryAccess registryAccess) {
        for (RecipeHolder<? extends Recipe<?>> holder : holders) {
            // 配方结果仅在索引构建时计算一次并缓存，避免每次扫描重复分配 ItemStack
            ItemStack result = holder.value().getResultItem(registryAccess);
            byResultItem.computeIfAbsent(result.getItem(), item -> new ArrayList<>()).add(holder);
        }
    }

    /** 首个被开关允许的锻造纹饰配方；无配方或全部被排除时返回 null（包级 @MethodsReturnNonnullByDefault 下需显式标注） */
    public @Nullable RecipeHolder<?> getFirstTrimRecipe() {
        for (RecipeHolder<?> holder : trimRecipes) {
            if (isScriptRecipeAllowed(holder.id().getNamespace())) {
                return holder;
            }
        }
        return null;
    }

    /** 保留原 input.is(产物物品) 匹配语义（仅比较物品类型）；被开关排除的脚本配方不参与 */
    public void collectMatching(ItemStack input, List<RecipeHolder<?>> out) {
        List<RecipeHolder<?>> candidates = byResultItem.get(input.getItem());
        if (candidates == null) return;
        for (RecipeHolder<?> holder : candidates) {
            if (!isScriptRecipeAllowed(holder.id().getNamespace())) continue;
            out.add(holder);
        }
    }

    /** 脚本配方兼容过滤：对应命名空间的配方仅在配置开关开启时参与拆解，其余配方（原版/其他模组原生）不受影响 */
    private static boolean isScriptRecipeAllowed(String namespace) {
        if (KUBEJS_NAMESPACE.equals(namespace)) {
            return ModConfig.ENABLE_KUBEJS_RECIPES.get();
        }
        if (CRAFTTWEAKER_NAMESPACE.equals(namespace)) {
            return ModConfig.ENABLE_CRAFTTWEAKER_RECIPES.get();
        }
        return true;
    }
}
