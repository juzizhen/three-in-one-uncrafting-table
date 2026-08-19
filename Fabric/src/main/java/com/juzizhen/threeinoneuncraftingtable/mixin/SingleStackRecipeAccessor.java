package com.juzizhen.threeinoneuncraftingtable.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.SingleStackRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 1.21.11 起切石类配方（SingleStackRecipe 子类）的 result 字段仅提供 protected 访问器，
 * 跨包无法调用，通过 Accessor 读取（语义等价于旧版 getResult 的结果本体）。
 */
@Mixin(SingleStackRecipe.class)
public interface SingleStackRecipeAccessor {
    @Accessor("result")
    ItemStack getResult();
}
