package com.juzizhen.threeinoneuncraftingtable.mixin;

import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.SmithingTrimRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SmithingTrimRecipe.class)
public interface SmithingTrimRecipeAccessor {
    @Accessor("template")
    Ingredient getTemplate();

    @Accessor("base")
    Ingredient getBase();

    @Accessor("addition")
    Ingredient getAddition();
}
