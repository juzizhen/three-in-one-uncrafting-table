package com.juzizhen.uncraftingrecipetable.block;

import com.juzizhen.uncraftingrecipetable.UncraftingRecipeTable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

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
                this.addSlot(new Slot(blockEntity,
                        UncraftingTableBlockEntity.SLOT_OUTPUT_START + y * 3 + x,
                        106 + x * 18, 17 + y * 18) {
                    @Override
                    public boolean canInsert(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public void onTakeItem(PlayerEntity player, ItemStack stack) {
                        super.onTakeItem(player, stack);
                        blockEntity.consumeInput();
                    }
                });
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
        ItemStack movedStack;
        Slot clickedSlot = this.slots.get(index);

        if (!clickedSlot.hasStack()) {
            return ItemStack.EMPTY;
        }

        ItemStack originalStack = clickedSlot.getStack();
        movedStack = originalStack.copy();

        if (index >= 0 && index <= 1) {
            if (!this.insertItem(originalStack, 11, 47, true)) {
                return ItemStack.EMPTY;
            }
        }
        else if (index >= 2 && index <= 10) {
            if (!this.insertItem(originalStack, 11, 47, true)) {
                return ItemStack.EMPTY;
            }

            for (int i = UncraftingTableBlockEntity.SLOT_OUTPUT_START;
                 i <= UncraftingTableBlockEntity.SLOT_OUTPUT_END; i++) {
                if (i == index) continue;

                ItemStack remainder = this.slots.get(i).getStack();
                if (!remainder.isEmpty()) {
                    if (!player.getInventory().insertStack(remainder)) {
                        player.dropItem(remainder, false);
                    }
                    this.slots.get(i).setStack(ItemStack.EMPTY);
                }
            }

            blockEntity.consumeInput();
        }
        else {
            ItemStack toMove = originalStack.copy();
            boolean success = false;

            if (toMove.isOf(Items.BOOK)) {
                if (this.insertItem(toMove, 0, 1, false)) {
                    success = true;
                } else if (this.insertItem(toMove, 1, 2, false)) {
                    success = true;
                }
            } else {
                if (this.insertItem(toMove, 1, 2, false)) {
                    success = true;
                }
            }

            if (!success) {
                return ItemStack.EMPTY;
            }

            originalStack.setCount(toMove.getCount());
        }

        if (originalStack.isEmpty()) {
            clickedSlot.setStack(ItemStack.EMPTY);
        } else {
            clickedSlot.markDirty();
        }

        clickedSlot.onQuickTransfer(originalStack, movedStack);

        return movedStack;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        for (int i = UncraftingTableBlockEntity.SLOT_OUTPUT_START;
             i <= UncraftingTableBlockEntity.SLOT_OUTPUT_END; i++) {
            blockEntity.setStack(i, ItemStack.EMPTY);
        }
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

    private static class InputSlot extends Slot {
        private final UncraftingTableBlockEntity blockEntity;

        public InputSlot(UncraftingTableBlockEntity blockEntity, int index, int x, int y) {
            super(blockEntity, index, x, y);
            this.blockEntity = blockEntity;
        }

        @Override
        public void setStack(ItemStack stack) {
            super.setStack(stack);
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