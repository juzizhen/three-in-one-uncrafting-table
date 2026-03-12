package com.juzizhen.threeinoneuncraftingtable.block;

import com.juzizhen.threeinoneuncraftingtable.ThreeInOneUncraftingTable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class UncraftingScreenHandler extends ScreenHandler {
    final UncraftingTableBlockEntity blockEntity;
    private final Inventory inventory;

    public UncraftingScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(ThreeInOneUncraftingTable.UNCRAFTING_SCREEN_HANDLER, syncId);
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

        this.addProperty(new Property() {
            @Override
            public int get() {
                return blockEntity.experienceCost;
            }

            @Override
            public void set(int value) {
                blockEntity.experienceCost = value;
            }
        });
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

        boolean triggerOutputChange = false;

        if (index >= 0 && index <= 1) {
            if (!this.insertItem(originalStack, 11, 38, false)) {
                if (!this.insertItem(originalStack, 38, 47, false)) {
                    return ItemStack.EMPTY;
                }
            }
        } else if (index >= 2 && index <= 10) {
            if (blockEntity.experienceCost > 0 && !player.isCreative() && player.experienceLevel < blockEntity.experienceCost) {
                return ItemStack.EMPTY;
            }
            if (!this.insertItem(originalStack, 11, 38, false)) {
                if (!this.insertItem(originalStack, 38, 47, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (originalStack.getCount() < movedStack.getCount()) {
                triggerOutputChange = true;
            }
        } else {
            boolean success = false;

            if (originalStack.isOf(Items.BOOK)) {
                Slot bookSlot = this.slots.get(0);
                if (!bookSlot.hasStack()) {
                    bookSlot.setStack(originalStack.split(1));
                    bookSlot.markDirty();
                    success = true;

                }
            }

            if (!success) {
                if (this.insertItem(originalStack, 1, 2, false)) {
                    success = true;
                }
            }

            if (!success) {
                return ItemStack.EMPTY;
            }
        }

        if (originalStack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }

        if (originalStack.getCount() == movedStack.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onQuickTransfer(originalStack, movedStack);

        if (triggerOutputChange) {
            blockEntity.onOutputChanged(movedStack, player);
            return ItemStack.EMPTY;
        }

        return movedStack;
    }

    public boolean hasRecipes() {
        Slot inputSlot = this.slots.get(1);

        if (!blockEntity.hasOutputItems() && !inputSlot.hasStack()) {
            return false;
        }

        if (!inputSlot.hasStack()) {
            return true;
        }

        for (int i = 2; i <= 10; i++) {
            if (!this.slots.get(i).getStack().isEmpty()) {
                return true;
            }
        }

        return false;
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
        if (blockEntity.outputGetCount == 0) {
            if (id == 0) {
                if (blockEntity.matchingRecipes.size() > 1) {
                    blockEntity.cycleRecipe(-1);
                }
                return true;
            } else if (id == 1) {
                if (blockEntity.matchingRecipes.size() > 1) {
                    blockEntity.cycleRecipe(1);
                }
                return true;
            }
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
            blockEntity.onOutputChanged(stack, player);
        }

        @Override
        public boolean canTakeItems(PlayerEntity player) {
            if (blockEntity.experienceCost > 0 && !player.isCreative() && player.experienceLevel < blockEntity.experienceCost) {
                return false;
            }

            return player.currentScreenHandler.getCursorStack().isEmpty();
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
            blockEntity.onInputChanged(false);
        }

        @Override
        public void onTakeItem(PlayerEntity player, ItemStack stack) {
            super.onTakeItem(player, stack);
            blockEntity.onInputChanged(false);
        }
    }

    private static class BookSlot extends Slot {
        private final UncraftingTableBlockEntity blockEntity;

        public BookSlot(UncraftingTableBlockEntity blockEntity, int index, int x, int y) {
            super(blockEntity, index, x, y);
            this.blockEntity = blockEntity;
        }

        @Override
        public void setStack(ItemStack newStack) {
            super.setStack(newStack);
            blockEntity.onInputChanged(true);
        }

        @Override
        public int getMaxItemCount() {
            return 1;
        }

        @Override
        public void onTakeItem(PlayerEntity player, ItemStack stack) {
            super.onTakeItem(player, stack);
            blockEntity.onInputChanged(true);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return stack.isOf(Items.BOOK);
        }
    }
}