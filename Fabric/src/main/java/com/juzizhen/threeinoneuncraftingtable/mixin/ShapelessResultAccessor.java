package com.juzizhen.threeinoneuncraftingtable.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 1.21.11 起 Recipe 接口不再有 getResult；无序合成配方的 result 字段无公开访问器，
 * 通过 Accessor 读取（语义等价于旧版 getResult 的结果本体，调用方按需 copy）。
 * 注意：与有序配方的 Accessor 分开声明，避免 loom remap 时同名字段映射冲突。
 */
@Mixin(ShapelessRecipe.class)
public interface ShapelessResultAccessor {
    @Accessor("result")
    ItemStack getResult();
}
