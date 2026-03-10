package com.juzizhen.uncraftingrecipetable.block;

import com.juzizhen.uncraftingrecipetable.UncraftingRecipeTable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class UncraftingScreenHandler extends ScreenHandler {
    private final UncraftingTableBlockEntity blockEntity;
    private final Inventory inventory;

    public UncraftingScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(UncraftingRecipeTable.UNCRAFTING_SCREEN_HANDLER, syncId);
        this.inventory = inventory;
        this.blockEntity = (UncraftingTableBlockEntity) inventory;

        this.addSlot(new BookSlot(blockEntity, UncraftingTableBlockEntity.SLOT_BOOK, 20, 35));
        this.addSlot(new InputSlot(blockEntity, UncraftingTableBlockEntity.SLOT_INPUT, 45, 35));

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int slotIndex = UncraftingTableBlockEntity.SLOT_OUTPUT_START + y * 3 + x;
                this.addSlot(new OutputSlot(blockEntity, slotIndex, 106 + x * 18, 17 + y * 18));
            }
        }

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                this.addSlot(new Slot(playerInventory, x + y * 9 + 9, 8 + x * 18, 84 + y * 18));
            }
        }
        for (int x = 0; x < 9; x++) {
            this.addSlot(new Slot(playerInventory, x, 8 + x * 18, 142));
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        if (player.getWorld().isClient()) {
            return ItemStack.EMPTY;
        }

        Slot slot = this.slots.get(index);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }

        ItemStack originalStack = slot.getStack();
        ItemStack movedStack = originalStack.copy();

        if (index >= 0 && index <= 1) {
            if (!this.insertItem(originalStack, 11, 38, false)) {
                if (!this.insertItem(originalStack, 38, 47, false)) {
                    return ItemStack.EMPTY;
                }
            }
        } else if (index >= 2 && index <= 10) {
            if (!this.insertItem(originalStack, 11, 38, false)) {
                if (!this.insertItem(originalStack, 38, 47, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (originalStack.getCount() < movedStack.getCount()) {
                int transferred = movedStack.getCount() - originalStack.getCount();
                blockEntity.consumeInputOnly(transferred, player);
            }
        } else {
            ItemStack stack = originalStack.copy();
            boolean success = false;
            if (this.insertItem(stack, 0, 1, false)) {
                success = true;
            } else if (this.insertItem(stack, 1, 2, false)) {
                success = true;
            }
            if (!success) {
                return ItemStack.EMPTY;
            }
            originalStack.setCount(stack.getCount());
        }

        if (originalStack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }

        slot.onQuickTransfer(originalStack, movedStack);
        return movedStack;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < this.slots.size()) {
            Slot clickedSlot = this.slots.get(slotIndex);
            blockEntity.onSlotClickIndex = clickedSlot.getIndex();
        }

        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        blockEntity.closeInventory(player);
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id == 0) {
            blockEntity.cycleRecipe(-1);
            return true;
        } else if (id == 1) {
            blockEntity.cycleRecipe(1);
            return true;
        }
        return super.onButtonClick(player, id);
    }

    private static class OutputSlot extends Slot {
        private final UncraftingTableBlockEntity blockEntity;

        public OutputSlot(UncraftingTableBlockEntity blockEntity, int index, int x, int y) {
            super(blockEntity, index, x, y);
            this.blockEntity = blockEntity;
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return false;
        }

        @Override
        public void onTakeItem(PlayerEntity player, ItemStack stack) {
            super.onTakeItem(player, stack);
            blockEntity.onOutputChanged(stack);
        }
    }

    private static class InputSlot extends Slot {
        private final UncraftingTableBlockEntity blockEntity;

        public InputSlot(UncraftingTableBlockEntity blockEntity, int index, int x, int y) {
            super(blockEntity, index, x, y);
            this.blockEntity = blockEntity;
        }

        @Override
        public void setStack(ItemStack newStack) {
            super.setStack(newStack);
            blockEntity.onInputChanged();
        }

        @Override
        public void onTakeItem(PlayerEntity player, ItemStack stack) {
            super.onTakeItem(player, stack);
            blockEntity.onInputChanged();
        }
    }

    private static class BookSlot extends Slot {
        public BookSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return stack.isOf(Items.BOOK);
        }
    }
}