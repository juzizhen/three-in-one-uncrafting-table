package com.juzizhen.threeinoneuncraftingtable.mixin;

import net.minecraft.item.equipment.trim.ArmorTrimPattern;
import net.minecraft.recipe.SmithingTrimRecipe;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 1.21.11 起纹饰图案按配方持有（pattern 字段包私有、无公开访问器），
 * 通过 Accessor 读取以构建「纹饰图案 -> 锻造模板物品」反查表。
 */
@Mixin(SmithingTrimRecipe.class)
public interface SmithingTrimRecipePatternAccessor {
    @Accessor("pattern")
    RegistryEntry<ArmorTrimPattern> getPattern();
}
