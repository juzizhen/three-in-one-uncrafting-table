package com.juzizhen.threeinoneuncraftingtable.mixin;

import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 1.21.11 起无序合成配方的原料列表字段无公开访问器（接口上的 getIngredients 已移除），
 * 通过 Accessor 读取，保持与旧版 getIngredients() 相同的列表语义。
 */
@Mixin(ShapelessRecipe.class)
public interface ShapelessIngredientsAccessor {
    @Accessor("ingredients")
    List<Ingredient> getIngredients();
}
