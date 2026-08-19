package com.juzizhen.threeinoneuncraftingtable.mixin;

import net.minecraft.recipe.SmithingTransformRecipe;
import net.minecraft.recipe.TransmuteRecipeResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 1.21.11 起锻造升级配方的模板/基底/附加原料已有公开访问器 template()/base()/addition()，
 * 原三字段 Accessor 随之移除；result 字段（TransmuteRecipeResult）仍无公开访问器，保留读取。
 */
@Mixin(SmithingTransformRecipe.class)
public interface SmithingTransformRecipeAccessor {
    @Accessor("result")
    TransmuteRecipeResult getResult();
}
