package com.juzizhen.threeinoneuncraftingtable.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class UncraftingTableBlock extends BlockWithEntity {
    public static final MapCodec<UncraftingTableBlock> CODEC = AbstractBlock.createCodec(UncraftingTableBlock::new);

    public UncraftingTableBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            NamedScreenHandlerFactory screenHandlerFactory = state.createScreenHandlerFactory(world, pos);
            if (screenHandlerFactory != null) {
                player.openHandledScreen(screenHandlerFactory);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new UncraftingTableBlockEntity(pos, state);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        // 方块被替换/破坏时掉落容器内容；必须先掉落物品再调用 super（super 会移除方块实体）
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof UncraftingTableBlockEntity blockEntity) {
            if (!world.isClient) {
                if (blockEntity.outputGetCount > 0) {
                    // 已取过产物（经验已扣、输入已消耗）：逐槽散落并显式清空（removeStack 取出即清槽），
                    // 不依赖 ItemScatterer 全槽重载的内部隐式副作用，
                    // 避免界面仍开着时后续 closeInventory 再次发放造成双份归还（canPlayerUse 仅判距离，方块被破坏不会自动关屏）
                    for (int i = 0; i < blockEntity.size(); i++) {
                        ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), blockEntity.removeStack(i));
                    }
                } else {
                    // 未取过产物：输出槽仅是配方预览（输入未被消耗），掉落会造成刷物品；
                    // 与 closeInventory 语义一致，仅掉落输入槽与书本槽，散落后同样显式清槽（removeStack 取出即清槽）
                    ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(),
                            blockEntity.removeStack(UncraftingTableBlockEntity.SLOT_INPUT));
                    ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(),
                            blockEntity.removeStack(UncraftingTableBlockEntity.SLOT_BOOK));
                }
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
