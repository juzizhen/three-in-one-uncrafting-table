package com.juzizhen.uncraftingrecipetable.block;

import com.juzizhen.uncraftingrecipetable.UncraftingRecipeTable;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.*;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class UncraftingTableBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory, Inventory {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_BOOK = 1;
    public static final int SLOT_OUTPUT_START = 2;
    public static final int SLOT_OUTPUT_END = 10;

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(11, ItemStack.EMPTY);
    private final List<Recipe<?>> matchingRecipes = new ArrayList<>();
    private int selectedRecipeIndex = 0;

    public UncraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(UncraftingRecipeTable.UNCRAFTING_TABLE_BLOCK_ENTITY, pos, state);
    }

    public void onInputChanged() {
        if (world == null || world.isClient) return;

        for (int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END; i++) {
            ItemStack remainder = getStack(i);
            if (!remainder.isEmpty()) {
                PlayerEntity player = world.getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 5, false);
                if (player != null) {
                    if (!player.getInventory().insertStack(remainder)) {
                        player.dropItem(remainder, false);
                    }
                }
                setStack(i, ItemStack.EMPTY);
            }
        }

        ItemStack input = getStack(SLOT_INPUT);
        matchingRecipes.clear();
        if (!input.isEmpty()) {
            findMatchingRecipes(input);
        }
        selectedRecipeIndex = 0;
        updateOutputSlots();
    }

    private void findMatchingRecipes(ItemStack input) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        for (CraftingRecipe recipe : serverWorld.getRecipeManager().listAllOfType(RecipeType.CRAFTING)) {
            ItemStack output = recipe.getOutput(serverWorld.getRegistryManager());
            if (ItemStack.areItemsEqual(input, output)) {
                matchingRecipes.add(recipe);
            }
        }
        for (SmithingRecipe recipe : serverWorld.getRecipeManager().listAllOfType(RecipeType.SMITHING)) {
            ItemStack output = recipe.getOutput(serverWorld.getRegistryManager());
            if (ItemStack.areItemsEqual(input, output)) {
                matchingRecipes.add(recipe);
            }
        }
    }

    public void consumeInput() {
        ItemStack input = getStack(SLOT_INPUT);
        if (!input.isEmpty()) {
            int count = input.getCount();
            input.decrement(count);

            if (input.isEmpty()) {
                setStack(SLOT_INPUT, ItemStack.EMPTY);
            } else {
                setStack(SLOT_INPUT, input);
            }
            onInputChanged();
        }
    }

    private void updateOutputSlots() {
        for (int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END; i++) {
            setStack(i, ItemStack.EMPTY);
        }
        if (matchingRecipes.isEmpty() || selectedRecipeIndex < 0 || selectedRecipeIndex >= matchingRecipes.size()) {
            return;
        }
        Recipe<?> recipe = matchingRecipes.get(selectedRecipeIndex);
        if (recipe instanceof CraftingRecipe craftingRecipe) {
            fillCraftingOutput(craftingRecipe);
        } else if (recipe instanceof SmithingRecipe smithingRecipe) {
            fillSmithingOutput(smithingRecipe);
        }
    }

    private void fillCraftingOutput(CraftingRecipe recipe) {
        List<Ingredient> ingredients = recipe.getIngredients();
        ItemStack input = getStack(SLOT_INPUT);
        int inputCount = input.getCount();

        for (int i = 0; i < ingredients.size() && i < 9; i++) {
            Ingredient ing = ingredients.get(i);
            ItemStack[] matching = ing.getMatchingStacks();
            if (matching.length > 0) {
                ItemStack stack = matching[0].copy();
                stack.setCount(inputCount);
                int row = i / 3;
                int col = i % 3;
                int slotIndex = SLOT_OUTPUT_START + row * 3 + col;
                setStack(slotIndex, stack);
            }
        }
    }


    private void fillSmithingOutput(SmithingRecipe recipe) {
        List<Ingredient> ingredients = recipe.getIngredients();
        for (int i = 0; i < 3 && i < ingredients.size(); i++) {
            Ingredient ing = ingredients.get(i);
            ItemStack[] matching = ing.getMatchingStacks();
            if (matching.length > 0) {
                ItemStack stack = matching[0].copy();
                stack.setCount(1);
                setStack(SLOT_OUTPUT_START + i, stack);
            }
        }
    }

    public void cycleRecipe(int delta) {
        if (matchingRecipes.isEmpty()) return;
        selectedRecipeIndex = (selectedRecipeIndex + delta) % matchingRecipes.size();
        if (selectedRecipeIndex < 0) selectedRecipeIndex += matchingRecipes.size();
        updateOutputSlots();
    }

    public int getSelectedRecipeIndex() {
        return selectedRecipeIndex;
    }

    public int getRecipeCount() {
        return matchingRecipes.size();
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getStack(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(items, slot, amount);
        if (!result.isEmpty()) markDirty();
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack result = Inventories.removeStack(items, slot);
        if (!result.isEmpty()) markDirty();
        return result;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > stack.getMaxCount()) stack.setCount(stack.getMaxCount());
        markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return pos.isWithinDistance(player.getBlockPos(), 4.5);
    }

    @Override
    public void clear() {
        items.clear();
        markDirty();
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, items);
        nbt.putInt("SelectedRecipe", selectedRecipeIndex);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        Inventories.readNbt(nbt, items);
        selectedRecipeIndex = nbt.getInt("SelectedRecipe");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new UncraftingScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("container.uncrafting_table");
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity serverPlayerEntity, PacketByteBuf packetByteBuf) {
        packetByteBuf.writeBlockPos(this.pos);
    }
}