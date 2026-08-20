package com.juzizhen.threeinoneuncraftingtable.block;

import com.juzizhen.threeinoneuncraftingtable.ThreeInOneUncraftingTable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class UncraftingScreenHandler extends AbstractContainerMenu {

    public final UncraftingTableBlockEntity blockEntity;

    public UncraftingScreenHandler(int containerId, Inventory playerInventory, UncraftingTableBlockEntity blockEntity) {
        super(ThreeInOneUncraftingTable.UNCRAFTING_SCREEN_HANDLER.get(), containerId);
        this.blockEntity = blockEntity;

        var handler = blockEntity.getInventory();

        this.addSlot(new BookSlotItemHandler(handler, blockEntity, UncraftingTableBlockEntity.SLOT_BOOK, 20, 35));
        this.addSlot(new InputSlotItemHandler(handler, blockEntity, UncraftingTableBlockEntity.SLOT_INPUT, 45, 35));

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int slotIndex = UncraftingTableBlockEntity.SLOT_OUTPUT_START + y * 3 + x;
                this.addSlot(new OutputSlotItemHandler(handler, blockEntity, slotIndex, 106 + x * 18, 17 + y * 18));
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

        this.addDataSlots(new ContainerData() {
            @Override
            public int get(int index) {
                return blockEntity.experienceCost;
            }

            @Override
            public void set(int index, int value) {
                blockEntity.experienceCost = value;
            }

            @Override
            public int getCount() {
                return 1;
            }
        });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (player.level().isClientSide()) {
            return ItemStack.EMPTY;
        }

        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack originalStack = slot.getItem();
        ItemStack movedStack = originalStack.copy();

        boolean triggerOutputChange = false;

        if (slotIndex >= 0 && slotIndex <= 1) {
            if (!this.moveItemStackTo(originalStack, 11, 38, false)) {
                if (!this.moveItemStackTo(originalStack, 38, 47, false)) {
                    return ItemStack.EMPTY;
                }
            }
        } else if (slotIndex >= 2 && slotIndex <= 10) {
            if (blockEntity.experienceCost > 0 && !player.isCreative() && player.experienceLevel < blockEntity.experienceCost) {
                return ItemStack.EMPTY;
            }
            if (!this.moveItemStackTo(originalStack, 11, 38, false)) {
                if (!this.moveItemStackTo(originalStack, 38, 47, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (originalStack.getCount() < movedStack.getCount()) {
                triggerOutputChange = true;
            }
        } else {
            boolean success = false;

            if (originalStack.is(Items.BOOK)) {
                Slot bookSlot = this.slots.getFirst();
                if (!bookSlot.hasItem()) {
                    bookSlot.set(originalStack.split(1));
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

        if (triggerOutputChange) {
            // 输出槽：只按实际取出的数量调用一次 onOutputChanged。
            // 不能调用 slot.onTake：OutputSlotItemHandler.onTake 会用剩余堆（可能为空）再次触发 onOutputChanged，
            // 此时输入尚未被消耗，补货逻辑会基于未消耗的输入再生成一批输出，导致刷物品。
            ItemStack takenStack = movedStack.copy();
            takenStack.setCount(movedStack.getCount() - originalStack.getCount());
            blockEntity.onOutputChanged(takenStack, player);
            return ItemStack.EMPTY;
        }

        slot.onTake(player, originalStack);

        return movedStack;
    }

    @Override
    public void clicked(int slotIndex, int button, ClickType clickType, Player player) {
        if (slotIndex >= 0 && slotIndex < this.slots.size()) {
            blockEntity.onSlotClickIndex = slotIndex;
        }
        super.clicked(slotIndex, button, clickType, player);
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
            if (player.level().isClientSide()) return true;
            for (int i = UncraftingTableBlockEntity.SLOT_OUTPUT_START; i <= UncraftingTableBlockEntity.SLOT_OUTPUT_END; i++) {
                Slot slot = this.slots.get(i);
                if (slot.hasItem()) {
                    quickMoveStack(player, i);
                }
            }
            Slot bookSlot = this.slots.getFirst();
            if (bookSlot.hasItem() && bookSlot.getItem().is(Items.ENCHANTED_BOOK)) {
                quickMoveStack(player, 0);
            }
            return true;
        }

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

    // 按用户要求保持与 1.21.1 原实现一致的 SlotItemHandler 槽位联动逻辑，不迁移到新容器 API；
    // forRemoval 告警经实测 javac 与 IDEA 均由 removal key 抑制（双 key 写法会被 IDEA 误报“冗余禁止”）
    @SuppressWarnings("removal")
    private static class OutputSlotItemHandler extends net.neoforged.neoforge.items.SlotItemHandler {
        private final UncraftingTableBlockEntity blockEntity;

        public OutputSlotItemHandler(net.neoforged.neoforge.items.ItemStackHandler handler,
                                     UncraftingTableBlockEntity blockEntity, int index, int x, int y) {
            super(handler, index, x, y);
            this.blockEntity = blockEntity;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            if (blockEntity.experienceCost > 0 && !player.isCreative() && player.experienceLevel < blockEntity.experienceCost) {
                return false;
            }
            // 与 Fabric 端保持一致：光标持有物品时禁止拿取（禁止替换式点击）
            return player.containerMenu.getCarried().isEmpty();
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            super.onTake(player, stack);
            blockEntity.onOutputChanged(stack, player);
        }
    }

    // 同 OutputSlotItemHandler：保持与原实现一致的 SlotItemHandler 联动，抑制 forRemoval 告警
    @SuppressWarnings("removal")
    private static class InputSlotItemHandler extends net.neoforged.neoforge.items.SlotItemHandler {
        private final UncraftingTableBlockEntity blockEntity;

        public InputSlotItemHandler(net.neoforged.neoforge.items.ItemStackHandler handler,
                                    UncraftingTableBlockEntity blockEntity, int index, int x, int y) {
            super(handler, index, x, y);
            this.blockEntity = blockEntity;
        }

        @Override
        public void set(ItemStack newStack) {
            super.set(newStack);
            blockEntity.onInputChanged(false);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            super.onTake(player, stack);
            blockEntity.onInputChanged(false);
        }
    }

    // 同 OutputSlotItemHandler：保持与原实现一致的 SlotItemHandler 联动，抑制 forRemoval 告警
    @SuppressWarnings("removal")
    private static class BookSlotItemHandler extends net.neoforged.neoforge.items.SlotItemHandler {
        private final UncraftingTableBlockEntity blockEntity;

        public BookSlotItemHandler(net.neoforged.neoforge.items.ItemStackHandler handler,
                                   UncraftingTableBlockEntity blockEntity, int index, int x, int y) {
            super(handler, index, x, y);
            this.blockEntity = blockEntity;
        }

        @Override
        public void set(ItemStack newStack) {
            super.set(newStack);
            blockEntity.onInputChanged(true);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            super.onTake(player, stack);
            blockEntity.onInputChanged(true);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(Items.BOOK);
        }
    }
}
