package com.juzizhen.threeinoneuncraftingtable.block;

import com.juzizhen.threeinoneuncraftingtable.ThreeInOneUncraftingTable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class UncraftingScreenHandler extends AbstractContainerMenu {

    public final UncraftingTableBlockEntity blockEntity;

    // Server-side constructor
    public UncraftingScreenHandler(int containerId, Inventory playerInventory, UncraftingTableBlockEntity blockEntity) {
        super(ThreeInOneUncraftingTable.UNCRAFTING_SCREEN_HANDLER.get(), containerId);
        this.blockEntity = blockEntity;

        var handler = blockEntity.getInventory();

        // Slot 0: Book
        this.addSlot(new BookSlotItemHandler(handler, UncraftingTableBlockEntity.SLOT_BOOK, 20, 35));
        // Slot 1: Input
        this.addSlot(new InputSlotItemHandler(handler, UncraftingTableBlockEntity.SLOT_INPUT, 45, 35));

        // Slots 2-10: Output (3x3)
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int slotIndex = UncraftingTableBlockEntity.SLOT_OUTPUT_START + y * 3 + x;
                this.addSlot(new OutputSlotItemHandler(handler, slotIndex, 106 + x * 18, 17 + y * 18));
            }
        }

        // Player inventory (slots 11-37)
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                this.addSlot(new Slot(playerInventory, x + y * 9 + 9, 8 + x * 18, 84 + y * 18));
            }
        }
        // Hotbar (slots 38-46)
        for (int x = 0; x < 9; x++) {
            this.addSlot(new Slot(playerInventory, x, 8 + x * 18, 142));
        }
    }

    // Client-side constructor (reads BlockPos from network buffer)
    public UncraftingScreenHandler(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory,
                (UncraftingTableBlockEntity) playerInventory.player.level().getBlockEntity(data.readBlockPos()));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack originalStack = slot.getItem();
        ItemStack movedStack = originalStack.copy();

        if (slotIndex >= 0 && slotIndex <= 1) {
            // Book/Input → player inventory/hotbar
            if (!this.moveItemStackTo(originalStack, 11, 47, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex >= 2 && slotIndex <= 10) {
            // Output → player inventory/hotbar
            if (blockEntity.experienceCost > 0 && !player.isCreative() && player.experienceLevel < blockEntity.experienceCost) {
                return ItemStack.EMPTY;
            }
            if (!this.moveItemStackTo(originalStack, 11, 47, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player inventory → book slot or input slot
            boolean success = false;
            if (originalStack.is(Items.BOOK)) {
                Slot bookSlot = this.slots.getFirst();
                if (!bookSlot.hasItem()) {
                    ItemStack split = originalStack.split(1);
                    bookSlot.set(split);
                    bookSlot.setChanged();
                    success = true;
                }
            }
            if (!success) {
                if (this.moveItemStackTo(originalStack, 1, 2, false)) {
                    success = true;
                }
            }
            if (!success) {
                return ItemStack.EMPTY;
            }
        }

        if (originalStack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (originalStack.getCount() == movedStack.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, originalStack);
        return movedStack;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(
                blockEntity.getBlockPos().getX() + 0.5,
                blockEntity.getBlockPos().getY() + 0.5,
                blockEntity.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        blockEntity.closeInventory(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 2) {
            // Collect all: move output items to player inventory
            if (player.level().isClientSide()) return true;
            for (int i = UncraftingTableBlockEntity.SLOT_OUTPUT_START; i <= UncraftingTableBlockEntity.SLOT_OUTPUT_END; i++) {
                Slot slot = this.slots.get(i);
                if (slot.hasItem()) {
                    quickMoveStack(player, i);
                }
            }
            // Also move enchanted book from book slot
            Slot bookSlot = this.slots.getFirst();
            if (bookSlot.hasItem() && bookSlot.getItem().is(Items.ENCHANTED_BOOK)) {
                quickMoveStack(player, 0);
            }
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    public boolean hasRecipes() {
        Slot inputSlot = this.slots.get(1);
        if (!blockEntity.hasOutputItems() && !inputSlot.hasItem()) {
            return false;
        }
        if (!inputSlot.hasItem()) {
            return true;
        }
        for (int i = 2; i <= 10; i++) {
            if (!this.slots.get(i).getItem().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static class OutputSlotItemHandler extends net.neoforged.neoforge.items.SlotItemHandler {
        public OutputSlotItemHandler(net.neoforged.neoforge.items.ItemStackHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    private static class InputSlotItemHandler extends net.neoforged.neoforge.items.SlotItemHandler {
        public InputSlotItemHandler(net.neoforged.neoforge.items.ItemStackHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }
    }

    private static class BookSlotItemHandler extends net.neoforged.neoforge.items.SlotItemHandler {
        public BookSlotItemHandler(net.neoforged.neoforge.items.ItemStackHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(Items.BOOK);
        }
    }
}
