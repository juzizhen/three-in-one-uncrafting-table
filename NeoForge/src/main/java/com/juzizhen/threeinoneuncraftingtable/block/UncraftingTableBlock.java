package com.juzizhen.threeinoneuncraftingtable.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class UncraftingTableBlock extends BaseEntityBlock {
    public static final MapCodec<UncraftingTableBlock> CODEC = simpleCodec(UncraftingTableBlock::new);

    public UncraftingTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            // UncraftingTableBlockEntity 已实现 MenuProvider，直接传入即可，无需匿名包装
            if (level.getBlockEntity(pos) instanceof UncraftingTableBlockEntity blockEntity) {
                player.openMenu(blockEntity, buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UncraftingTableBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // 方块被替换/破坏时掉落容器内容；必须先掉落物品再调用 super（super 会移除方块实体）
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof UncraftingTableBlockEntity blockEntity) {
            if (!level.isClientSide()) {
                var inventory = blockEntity.getInventory();
                if (blockEntity.outputGetCount > 0) {
                    // 已取过产物（经验已扣、输入已消耗）：掉落全部槽位（剩余产物 + 残留输入 + 书本）；
                    // 散落每个槽位后立即显式清空，不依赖 dropItemStack 内部 split 的隐式副作用，
                    // 避免界面仍开着时后续 closeInventory 再次发放造成双份归还（stillValid 仅判距离，方块被破坏不会自动关屏）
                    for (int i = 0; i < inventory.getSlots(); i++) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), inventory.getStackInSlot(i));
                        inventory.setStackInSlot(i, ItemStack.EMPTY);
                    }
                } else {
                    // 未取过产物：输出槽仅是配方预览（输入未被消耗），掉落会造成刷物品；
                    // 与 closeInventory 语义一致，仅掉落输入槽与书本槽，散落后同样显式清槽
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                            inventory.getStackInSlot(UncraftingTableBlockEntity.SLOT_INPUT));
                    inventory.setStackInSlot(UncraftingTableBlockEntity.SLOT_INPUT, ItemStack.EMPTY);
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                            inventory.getStackInSlot(UncraftingTableBlockEntity.SLOT_BOOK));
                    inventory.setStackInSlot(UncraftingTableBlockEntity.SLOT_BOOK, ItemStack.EMPTY);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
