package com.juzizhen.uncraftingrecipetable.block;

import com.juzizhen.uncraftingrecipetable.UncraftingRecipeTable;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
    private int experienceCost = 0;

    public int outputCounter = 0;
    public int outputGetCount = 0;
    public boolean noOutputs = true;
    public int onSlotClickIndex = 0;

    private static final int XP_PER_RECIPE = 5;

    public UncraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(UncraftingRecipeTable.UNCRAFTING_TABLE_BLOCK_ENTITY, pos, state);
    }

    private boolean hasOutputItems() {
        boolean hasOutputItems = false;
        for (int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END; i++) {
            if (!getStack(i).isEmpty()) {
                hasOutputItems = true;
                break;
            }
        }
        return hasOutputItems;
    }

    private void searchRecipeToOutput(ItemStack currentInput) {
        findMatchingRecipes(currentInput);

        if (!matchingRecipes.isEmpty()) {
            Recipe<?> recipe = matchingRecipes.get(selectedRecipeIndex);
            int inputCount = currentInput.getCount();

            if (recipe instanceof CraftingRecipe craftingRecipe) {
                fillCraftingOutput(craftingRecipe, inputCount);
            } else if (recipe instanceof SmithingRecipe smithingRecipe) {
                fillSmithingOutput(smithingRecipe, inputCount);
            }
        }
    }

    public void onInputChanged() {
        if (world == null || world.isClient) return;
        ItemStack currentInput = getStack(SLOT_INPUT);
        boolean hasOutputItems = hasOutputItems();

        if (!hasOutputItems) {
            clearOutputSlots();
            matchingRecipes.clear();
            selectedRecipeIndex = 0;

            if (currentInput.isEmpty()) {
                markDirty();
                return;
            }

            searchRecipeToOutput(currentInput);
        } else {
            if (outputGetCount == 0) {
                clearOutputSlots();
            }
        }

        hasOutputItems = hasOutputItems();

        if (!hasOutputItems && getStack(SLOT_INPUT) == ItemStack.EMPTY) {
            initialization();
        }

        markDirty();
    }

    public void onOutputChanged(ItemStack stack) {
        if (world == null || world.isClient) return;
        boolean hasOutputItems;
        ItemStack currentInput = getStack(SLOT_INPUT);

        if (!stack.isEmpty()) {
            if (outputGetCount == 0) {
                outputGetCount++;
                setStack(SLOT_INPUT, ItemStack.EMPTY);
            } else if (outputGetCount < 0) {
                outputGetCount = 0;
            } else {
                outputGetCount++;
            }

            hasOutputItems = hasOutputItems();

            if (!hasOutputItems) {
                outputGetCount = 0;
            }
        }

        hasOutputItems = hasOutputItems();

        if (onSlotClickIndex >= SLOT_OUTPUT_START && onSlotClickIndex <= SLOT_OUTPUT_END) {
            int currentSlotIndex = onSlotClickIndex;

            boolean noOtherOutputs = false;
            for (int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END; i++) {
                if (i != currentSlotIndex && !getStack(i).isEmpty()) {
                    noOtherOutputs = false;
                    break;
                } else {
                    noOtherOutputs = true;
                }
            }

            if (noOtherOutputs && getStack(SLOT_INPUT) != ItemStack.EMPTY) {
                searchRecipeToOutput(currentInput);
            }
        }


        if (!hasOutputItems && getStack(SLOT_INPUT) == ItemStack.EMPTY) {
            initialization();
        }

        markDirty();
    }

    private void initialization() {
        setStack(SLOT_INPUT, ItemStack.EMPTY);
        clearOutputSlots();
        matchingRecipes.clear();
        selectedRecipeIndex = 0;
        noOutputs = true;
        outputGetCount = 0;
        markDirty();
    }

    public void closeInventory(PlayerEntity player) {
        if (outputGetCount > 0) {
            for (int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END; i++) {
                ItemStack stack = getStack(i);
                if (!stack.isEmpty()) {
                    if (!player.getInventory().insertStack(stack)) {
                        player.dropItem(stack, false);
                    }
                    setStack(i, ItemStack.EMPTY);
                }
            }
        } else {
            ItemStack input = getStack(SLOT_INPUT);
            if (!input.isEmpty()) {
                if (!player.getInventory().insertStack(input)) {
                    player.dropItem(input, false);
                }
                setStack(SLOT_INPUT, ItemStack.EMPTY);
            }
        }
        initialization();
    }

    private void updateOutputSlots() {
        clearOutputSlots();
        if (matchingRecipes.isEmpty() || selectedRecipeIndex >= matchingRecipes.size()) return;

        Recipe<?> recipe = matchingRecipes.get(selectedRecipeIndex);
        ItemStack input = getStack(SLOT_INPUT);
        int inputCount = input.getCount();

        if (recipe instanceof CraftingRecipe craftingRecipe) {
            fillCraftingOutput(craftingRecipe, inputCount);
        } else if (recipe instanceof SmithingRecipe smithingRecipe) {
            fillSmithingOutput(smithingRecipe, inputCount);
        }
    }

    private void findMatchingRecipes(ItemStack input) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        for (CraftingRecipe recipe : serverWorld.getRecipeManager().listAllOfType(RecipeType.CRAFTING)) {
            if (ItemStack.areItemsEqual(input, recipe.getOutput(serverWorld.getRegistryManager()))) {
                matchingRecipes.add(recipe);
            }
        }
        for (SmithingRecipe recipe : serverWorld.getRecipeManager().listAllOfType(RecipeType.SMITHING)) {
            if (ItemStack.areItemsEqual(input, recipe.getOutput(serverWorld.getRegistryManager()))) {
                matchingRecipes.add(recipe);
            }
        }
    }

    private void fillCraftingOutput(CraftingRecipe recipe, int inputCount) {
        if (world == null) return;
        List<Ingredient> ingredients = recipe.getIngredients();
        ItemStack recipeOutput = recipe.getOutput(world.getRegistryManager());
        int recipeOutputCount = Math.max(1, recipeOutput.getCount());
        int multiplier = inputCount / recipeOutputCount;

        if (multiplier <= 0) return;

        experienceCost = XP_PER_RECIPE * multiplier;

        int totalOutputItems = 0;

        for (int i = 0; i < ingredients.size() && i < 9; i++) {
            Ingredient ing = ingredients.get(i);
            ItemStack[] matching = ing.getMatchingStacks();
            if (matching.length > 0) {
                ItemStack stack = matching[0].copy();
                stack.setCount(multiplier);
                int slotIndex = SLOT_OUTPUT_START + (i / 3) * 3 + (i % 3);
                setStack(slotIndex, stack);
                totalOutputItems += stack.getCount();
            }
        }

        outputCounter = totalOutputItems;
    }

    private void fillSmithingOutput(SmithingRecipe recipe, int inputCount) {
        if (world == null) return;
        List<Ingredient> ingredients = recipe.getIngredients();
        ItemStack recipeOutput = recipe.getOutput(world.getRegistryManager());
        int recipeOutputCount = Math.max(1, recipeOutput.getCount());
        int multiplier = inputCount / recipeOutputCount;

        if (multiplier <= 0) return;

        experienceCost = XP_PER_RECIPE * multiplier;

        int totalOutputItems = 0;

        for (int i = 0; i < 3 && i < ingredients.size(); i++) {
            Ingredient ing = ingredients.get(i);
            ItemStack[] matching = ing.getMatchingStacks();
            if (matching.length > 0) {
                ItemStack stack = matching[0].copy();
                stack.setCount(multiplier);
                setStack(SLOT_OUTPUT_START + i, stack);
                totalOutputItems += stack.getCount();
            }
        }

        outputCounter = totalOutputItems;
    }

    void clearOutputSlots() {
        for (int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END; i++) {
            setStack(i, ItemStack.EMPTY);
        }
    }

    public void consumeInputOnly(int amount, PlayerEntity player) {
        if (world == null || world.isClient || amount <= 0) return;

        ItemStack input = getStack(SLOT_INPUT);
        if (input.isEmpty() || matchingRecipes.isEmpty()) return;

        Recipe<?> recipe = matchingRecipes.get(selectedRecipeIndex);
        int recipeOutputCount = getRecipeOutputCount();
        int toConsume = amount * recipeOutputCount;

        if (toConsume > input.getCount()) {
            int maxAmount = input.getCount() / recipeOutputCount;
            toConsume = maxAmount * recipeOutputCount;
            if (toConsume == 0) return;
        }

        int numberOfRecipes = toConsume / recipeOutputCount;
        player.addExperience(-XP_PER_RECIPE * numberOfRecipes);

        ItemStack book = getStack(SLOT_BOOK);
        if (!book.isEmpty() && book.isOf(Items.BOOK) && input.hasEnchantments()) {
            ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
            EnchantmentHelper.set(EnchantmentHelper.get(input), enchantedBook);
            setStack(SLOT_BOOK, enchantedBook);
        }

        input.decrement(toConsume);
        if (input.isEmpty()) {
            setStack(SLOT_INPUT, ItemStack.EMPTY);
        } else {
            setStack(SLOT_INPUT, input);
        }

        returnContainerItems((CraftingRecipe) recipe, numberOfRecipes, player);

        updateOutputSlots();
        markDirty();
    }

    private void returnContainerItems(CraftingRecipe recipe, int numberOfRecipes, PlayerEntity player) {
        CraftingInventory dummy = new CraftingInventory(new ScreenHandler(null, -1) {
            @Override
            public ItemStack quickMove(PlayerEntity player, int slot) {
                return null;
            }

            @Override
            public boolean canUse(PlayerEntity player) {
                return true;
            }
        }, 3, 3);

        List<Ingredient> ingredients = recipe.getIngredients();
        for (int i = 0; i < ingredients.size() && i < 9; i++) {
            Ingredient ing = ingredients.get(i);
            ItemStack[] matching = ing.getMatchingStacks();
            if (matching.length > 0) {
                ItemStack stack = matching[0].copy();
                stack.setCount(1);
                dummy.setStack(i, stack);
            }
        }

        DefaultedList<ItemStack> remaining = recipe.getRemainder(dummy);
        for (ItemStack r : remaining) {
            if (!r.isEmpty()) {
                ItemStack scaled = r.copy();
                scaled.setCount(scaled.getCount() * numberOfRecipes);
                if (!player.getInventory().insertStack(scaled)) {
                    player.dropItem(scaled, false);
                }
            }
        }
    }

    private static class DummyContainer extends ScreenHandler {
        public DummyContainer() { super(null, -1); }
        @Override public ItemStack quickMove(PlayerEntity player, int index) { return ItemStack.EMPTY; }
        @Override public boolean canUse(PlayerEntity player) { return true; }
    }

    private int getRecipeOutputCount() {
        if (matchingRecipes.isEmpty()) return 1;
        Recipe<?> recipe = matchingRecipes.get(selectedRecipeIndex);
        ItemStack output = null;
        if (world != null) {
            output = recipe.getOutput(world.getRegistryManager());
        }
        if (output != null) {
            return Math.max(1, output.getCount());
        }
        return 0;
    }

    public void cycleRecipe(int delta) {
        if (matchingRecipes.isEmpty()) return;
        selectedRecipeIndex = (selectedRecipeIndex + delta) % matchingRecipes.size();
        if (selectedRecipeIndex < 0) selectedRecipeIndex += matchingRecipes.size();
        updateOutputSlots();
    }

    @Override public int size() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getStack(int slot) { return items.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(items, slot, amount);
        if (!result.isEmpty()) markDirty();
        return result;
    }
    @Override public ItemStack removeStack(int slot) {
        ItemStack result = Inventories.removeStack(items, slot);
        if (!result.isEmpty()) markDirty();
        return result;
    }
    @Override public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > stack.getMaxCount()) stack.setCount(stack.getMaxCount());
        markDirty();
    }
    @Override public boolean canPlayerUse(PlayerEntity player) {
        return pos.isWithinDistance(player.getBlockPos(), 4.5);
    }
    @Override public void clear() { items.clear(); markDirty(); }
    @Override public void markDirty() {
        super.markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }
//    @Override protected void writeNbt(NbtCompound nbt) {
//        super.writeNbt(nbt);
//        Inventories.writeNbt(nbt, items);
//        nbt.putInt("SelectedRecipe", selectedRecipeIndex);
//    }
//    @Override public void readNbt(NbtCompound nbt) {
//        super.readNbt(nbt);
//        Inventories.readNbt(nbt, items);
//        selectedRecipeIndex = nbt.getInt("SelectedRecipe");
//    }
    @Override public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new UncraftingScreenHandler(syncId, playerInventory, this);
    }
    @Override public Text getDisplayName() {
        return Text.translatable("container.uncrafting_table");
    }
    @Override public void writeScreenOpeningData(ServerPlayerEntity serverPlayerEntity, PacketByteBuf packetByteBuf) {
        packetByteBuf.writeBlockPos(this.pos);
    }
}