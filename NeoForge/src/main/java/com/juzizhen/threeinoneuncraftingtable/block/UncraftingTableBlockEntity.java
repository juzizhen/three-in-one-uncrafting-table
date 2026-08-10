package com.juzizhen.threeinoneuncraftingtable.block;

import com.juzizhen.threeinoneuncraftingtable.ThreeInOneUncraftingTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class UncraftingTableBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_BOOK = 1;
    public static final int SLOT_OUTPUT_START = 2;
    public static final int SLOT_OUTPUT_END = 10;
    private final ItemStackHandler inventory = new ItemStackHandler(11) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    };
    public int experienceCost = 0;

    public UncraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ThreeInOneUncraftingTable.UNCRAFTING_TABLE_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                            UncraftingTableBlockEntity blockEntity) {
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public boolean hasOutputItems() {
        for (int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END; i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public void closeInventory(Player player) {
        if (player.level().isClientSide()) return;

        // Return output items to player
        for (int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }

        // Return input
        ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
        if (!input.isEmpty()) {
            if (!player.getInventory().add(input)) {
                player.drop(input, false);
            }
            inventory.setStackInSlot(SLOT_INPUT, ItemStack.EMPTY);
        }

        // Return book
        ItemStack book = inventory.getStackInSlot(SLOT_BOOK);
        if (!book.isEmpty()) {
            if (!player.getInventory().add(book)) {
                player.drop(book, false);
            }
            inventory.setStackInSlot(SLOT_BOOK, ItemStack.EMPTY);
        }

        experienceCost = 0;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putInt("experienceCost", experienceCost);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        experienceCost = tag.getInt("experienceCost");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new UncraftingScreenHandler(containerId, playerInventory, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container." + ThreeInOneUncraftingTable.MOD_ID + ".uncrafting_table");
    }
}
