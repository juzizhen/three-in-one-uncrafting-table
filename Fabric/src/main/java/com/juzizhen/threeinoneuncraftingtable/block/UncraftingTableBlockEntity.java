package com.juzizhen.threeinoneuncraftingtable.block;

import com.juzizhen.threeinoneuncraftingtable.ThreeInOneUncraftingTable;
import com.juzizhen.threeinoneuncraftingtable.mixin.SmithingTransformRecipeAccessor;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.item.trim.ArmorTrimMaterial;
import net.minecraft.recipe.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class UncraftingTableBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, Inventory {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_BOOK = 1;
    public static final int SLOT_OUTPUT_START = 2;
    public static final int SLOT_OUTPUT_END = 10;
    final List<RecipeEntry<?>> matchingRecipes = new ArrayList<>();
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(11, ItemStack.EMPTY);
    public int outputCounter = 0;
    public int outputGetCount = 0;
    public boolean noOutputs = true;
    public int onSlotClickIndex = 0;
    public int experienceCost = 0;
    private int selectedRecipeIndex = 0;
    private int inputConsumed = 0;
    // 批量槽位变更时抑制逐次方块更新广播，批末合并为一次（可嵌套）
    private int batchDepth = 0;
    private boolean batchDirty = false;

    public UncraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ThreeInOneUncraftingTable.UNCRAFTING_TABLE_BLOCK_ENTITY, pos, state);
    }

    public boolean hasOutputItems() {
        boolean hasOutputItems = false;
        for (int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END; i++) {
            if (!getStack(i).isEmpty()) {
                hasOutputItems = true;
                break;
            }
        }
        return hasOutputItems;
    }

    void searchRecipeToOutput(ItemStack currentInput) {
        runBatched(() -> {
            matchingRecipes.clear();
            findMatchingRecipes(currentInput);

            if (!matchingRecipes.isEmpty()) {
                RecipeEntry<?> entry = matchingRecipes.get(selectedRecipeIndex);
                Recipe<?> recipe = entry.value();
                int inputCount = currentInput.getCount();

                if (recipe instanceof CraftingRecipe craftingRecipe && ThreeInOneUncraftingTable.CONFIG.enableCrafting) {
                    fillCraftingOutput(craftingRecipe, inputCount);
                } else if (recipe instanceof SmithingRecipe smithingRecipe && ThreeInOneUncraftingTable.CONFIG.enableSmithing) {
                    fillSmithingOutput(smithingRecipe, inputCount);
                } else if (recipe instanceof StonecuttingRecipe stonecuttingRecipe && ThreeInOneUncraftingTable.CONFIG.enableStonecutting) {
                    fillStonecuttingOutput(stonecuttingRecipe, inputCount);
                }
            }
        });
    }

    public void onInputChanged(boolean isBookInput) {
        if (world == null || world.isClient) return;
        ItemStack currentInput = getStack(SLOT_INPUT);
        boolean hasOutputItems = hasOutputItems();

        if (isBookInput) {
            if (hasOutputItems && outputGetCount == 0) {
                searchRecipeToOutput(currentInput);
            }
            markDirty();
            return;
        }

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

        if (!hasOutputItems && getStack(SLOT_INPUT).isEmpty()) {
            initialization();
        }

        markDirty();
        world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    public void onOutputChanged(ItemStack stack, PlayerEntity player) {
        if (world == null || world.isClient) return;
        // 空堆不触发任何消耗/补货逻辑，避免在输入被消耗前错误地重新填充输出槽（刷物品）
        if (stack.isEmpty()) return;
        boolean hasOutputItems;
        ItemStack currentInput = getStack(SLOT_INPUT);

        if (!stack.isEmpty()) {
            if (outputGetCount == 0) {
                outputGetCount++;
                if (experienceCost > 0 && !player.isCreative()) {
                    player.addExperienceLevels(-experienceCost);
                    experienceCost = 0;
                }
                if (currentInput.hasEnchantments() && !getStack(SLOT_BOOK).isEmpty()
                        && ThreeInOneUncraftingTable.CONFIG.enableEnchantmentTransfer) {
                    ItemStack book = getStack(SLOT_BOOK);
                    if (book.getItem() == Items.BOOK) {
                        ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);

                        ItemEnchantmentsComponent sourceEnchantments = currentInput.getEnchantments();
                        ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
                        for (RegistryEntry<Enchantment> enchantmentEntry : sourceEnchantments.getEnchantments()) {
                            int lvl = sourceEnchantments.getLevel(enchantmentEntry);
                            builder.add(enchantmentEntry, lvl);
                        }
                        enchantedBook.set(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());
                        setStack(SLOT_BOOK, enchantedBook);
                    }
                }
                int consumed = inputConsumed > 0 ? inputConsumed : currentInput.getCount();
                if (currentInput.getCount() > consumed) {
                    currentInput.setCount(currentInput.getCount() - consumed);
                } else {
                    setStack(SLOT_INPUT, ItemStack.EMPTY);
                }
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

            if (noOtherOutputs && !getStack(SLOT_INPUT).isEmpty()) {
                clearOutputSlots();
                searchRecipeToOutput(currentInput);
            }
        }


        if (!hasOutputItems && getStack(SLOT_INPUT).isEmpty()) {
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
        experienceCost = 0;
        inputConsumed = 0;
        markDirty();
    }

    public void closeInventory(PlayerEntity player) {
        if (world == null || world.isClient) return;
        runBatched(() -> {
            if (outputGetCount > 0) {
                // 已取过产物（经验已扣、输入已消耗）：归还剩余产物与剩余输入（不足以再拆解一批的残留）
                for (int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END; i++) {
                    returnToPlayer(player, i);
                }
                returnToPlayer(player, SLOT_INPUT);
            } else {
                // 未取过产物：输出槽仅是预览，直接清空，只归还原料，避免原料+产物同时返还造成刷物品
                clearOutputSlots();
                returnToPlayer(player, SLOT_INPUT);
            }
            // 书本原样归还（关闭时不做附魔转移）
            returnToPlayer(player, SLOT_BOOK);
            initialization();
        });
    }

    private void returnToPlayer(PlayerEntity player, int slot) {
        ItemStack stack = getStack(slot);
        if (!stack.isEmpty()) {
            if (!player.getInventory().insertStack(stack)) {
                player.dropItem(stack, false);
            }
            setStack(slot, ItemStack.EMPTY);
        }
    }

    private void updateOutputSlots() {
        runBatched(() -> {
            clearOutputSlots();
            if (matchingRecipes.isEmpty() || selectedRecipeIndex >= matchingRecipes.size()) return;

            RecipeEntry<?> entry = matchingRecipes.get(selectedRecipeIndex);
            Recipe<?> recipe = entry.value();
            ItemStack input = getStack(SLOT_INPUT);
            int inputCount = input.getCount();

            if (recipe instanceof CraftingRecipe craftingRecipe && ThreeInOneUncraftingTable.CONFIG.enableCrafting) {
                fillCraftingOutput(craftingRecipe, inputCount);
            } else if (recipe instanceof SmithingRecipe smithingRecipe && ThreeInOneUncraftingTable.CONFIG.enableSmithing) {
                fillSmithingOutput(smithingRecipe, inputCount);
            } else if (recipe instanceof StonecuttingRecipe stonecuttingRecipe && ThreeInOneUncraftingTable.CONFIG.enableStonecutting) {
                fillStonecuttingOutput(stonecuttingRecipe, inputCount);
            }
        });
    }

    private void findMatchingRecipes(ItemStack input) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        matchingRecipes.clear();

        UncraftingRecipeIndex recipeIndex = UncraftingRecipeIndex.get(serverWorld);

        ArmorTrim trim = input.get(DataComponentTypes.TRIM);
        if (trim != null) {
            RecipeEntry<?> trimRecipe = recipeIndex.getFirstTrimRecipe();
            if (trimRecipe != null) {
                matchingRecipes.add(trimRecipe);
                return;
            }
        }

        recipeIndex.collectMatching(input, matchingRecipes);
    }

    private void fillCraftingOutput(CraftingRecipe recipe, int inputCount) {
        if (world == null) return;
        int baseXpCost = ThreeInOneUncraftingTable.CONFIG.baseXpCost;
        float xpCostMultiplier = ThreeInOneUncraftingTable.CONFIG.xpCostMultiplier;
        List<Ingredient> ingredients = recipe.getIngredients();
        ItemStack recipeOutput = recipe.getResult(world.getRegistryManager());
        int recipeOutputCount = Math.max(1, recipeOutput.getCount());
        int multiplier = inputCount / recipeOutputCount;

        if (multiplier <= 0) return;
        int cost = Math.max(0, Math.round(xpCostMultiplier * (baseXpCost * multiplier * 0.8F - multiplier)));
        ItemStack input = getStack(SLOT_INPUT);
        if (input.isDamageable()) {
            int damage = input.getDamage();
            int maxDamage = input.getMaxDamage();
            float lostRatio = (float) damage / (float) maxDamage;
            cost += (int) Math.ceil(cost * lostRatio * 1.25);
        }
        if (input.hasEnchantments() && !getStack(SLOT_BOOK).isEmpty()
                && ThreeInOneUncraftingTable.CONFIG.enableEnchantmentTransfer) {
            ItemEnchantmentsComponent enchantments = input.getEnchantments();
            for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
                int lvl = enchantments.getLevel(entry);
                cost += Math.round(2 * (1.0F + (lvl - 1) * 0.5F) * xpCostMultiplier);
            }
        }
        experienceCost = cost;
        inputConsumed = multiplier * recipeOutputCount;

        int totalOutputItems = 0;

        if (recipe instanceof ShapedRecipe shaped) {
            int width = shaped.getWidth();
            int height = shaped.getHeight();

            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    int ingredientIndex = row * width + col;
                    if (ingredientIndex >= ingredients.size()) continue;

                    Ingredient ing = ingredients.get(ingredientIndex);
                    ItemStack[] matching = ing.getMatchingStacks();

                    int slotIndex = SLOT_OUTPUT_START + row * 3 + col;

                    if (matching.length > 0) {
                        ItemStack stack = matching[0].copy();
                        stack.setCount(multiplier);
                        setStack(slotIndex, stack);
                        totalOutputItems += stack.getCount();
                    } else {
                        setStack(slotIndex, ItemStack.EMPTY);
                    }
                }
            }
        } else {
            for (int i = 0; i < ingredients.size() && i < 9; i++) {
                Ingredient ing = ingredients.get(i);
                ItemStack[] matching = ing.getMatchingStacks();
                int slotIndex = SLOT_OUTPUT_START + i;

                if (matching.length > 0) {
                    ItemStack stack = matching[0].copy();
                    stack.setCount(multiplier);
                    setStack(slotIndex, stack);
                    totalOutputItems += stack.getCount();
                } else {
                    setStack(slotIndex, ItemStack.EMPTY);
                }
            }
        }

        outputCounter = totalOutputItems;
    }

    private void fillSmithingOutput(Recipe<?> recipe, int inputCount) {
        if (world == null) return;
        int baseXpCost = ThreeInOneUncraftingTable.CONFIG.baseXpCost;
        float xpCostMultiplier = ThreeInOneUncraftingTable.CONFIG.xpCostMultiplier;

        ItemStack inputStack = getStack(SLOT_INPUT);
        if (inputStack.isEmpty()) return;

        ArmorTrim trim = inputStack.get(DataComponentTypes.TRIM);

        if (trim != null) {
            if (inputCount <= 0) return;
            experienceCost = Math.round(ThreeInOneUncraftingTable.CONFIG.baseXpCost
                    * ThreeInOneUncraftingTable.CONFIG.xpCostMultiplier * inputCount);
            inputConsumed = inputCount;

            // 槽位 0: 纹饰模板
            Item templateItem = trim.getPattern().value().templateItem().value();
            ItemStack templateStack = new ItemStack(templateItem, inputCount);
            setStack(SLOT_OUTPUT_START, templateStack);

            // 槽位 1: 抹除纹饰的原装备
            ItemStack baseStack = inputStack.copy();
            baseStack.setCount(inputCount);
            baseStack.remove(DataComponentTypes.TRIM);
            // 有书且启用附魔转移时去除附魔（拿走时由 onOutputChanged 转移到书）；否则保留附魔在装备上
            ItemStack bookSlot = getStack(SLOT_BOOK);
            if (!bookSlot.isEmpty() && bookSlot.getItem() == Items.BOOK
                    && ThreeInOneUncraftingTable.CONFIG.enableEnchantmentTransfer) {
                baseStack.remove(DataComponentTypes.ENCHANTMENTS);
            }
            setStack(SLOT_OUTPUT_START + 1, baseStack);

            // 槽位 2: 纹饰矿物材料
            ArmorTrimMaterial trimMaterial = trim.getMaterial().value();
            Item materialItem = trimMaterial.ingredient().value();
            ItemStack materialStack = new ItemStack(materialItem, inputCount);
            setStack(SLOT_OUTPUT_START + 2, materialStack);

            outputCounter = templateStack.getCount() + baseStack.getCount() + materialStack.getCount();
            return;
        }

        ItemStack recipeOutput = recipe.getResult(world.getRegistryManager());
        int recipeOutputCount = Math.max(1, recipeOutput.getCount());
        int multiplier = inputCount / recipeOutputCount;

        if (multiplier <= 0) return;
        int cost = Math.max(0, Math.round(baseXpCost * xpCostMultiplier * multiplier * 1.5F));
        ItemStack input = getStack(SLOT_INPUT);
        if (input.isDamageable()) {
            int damage = input.getDamage();
            int maxDamage = input.getMaxDamage();
            float lostRatio = (float) damage / (float) maxDamage;
            cost += (int) Math.ceil(cost * lostRatio * 1.25);
        }
        if (input.hasEnchantments() && !getStack(SLOT_BOOK).isEmpty()
                && ThreeInOneUncraftingTable.CONFIG.enableEnchantmentTransfer) {
            ItemEnchantmentsComponent enchantments = input.getEnchantments();
            for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
                int lvl = enchantments.getLevel(entry);
                cost += Math.round(2 * (1.0F + (lvl - 1) * 0.5F) * xpCostMultiplier);
            }
        }
        experienceCost = cost;
        inputConsumed = multiplier * recipeOutputCount;

        int totalOutputItems = 0;
        Ingredient[] parts = new Ingredient[3];

        if (recipe instanceof SmithingTransformRecipe transform) {
            SmithingTransformRecipeAccessor accessor = (SmithingTransformRecipeAccessor) transform;
            parts[0] = accessor.getTemplate();
            parts[1] = accessor.getBase();
            parts[2] = accessor.getAddition();
        } else {
            return;
        }

        for (int i = 0; i < 3; i++) {
            Ingredient ing = parts[i];
            if (ing != null && ing.getMatchingStacks().length > 0) {
                ItemStack stack = ing.getMatchingStacks()[0].copy();
                stack.setCount(multiplier);
                setStack(SLOT_OUTPUT_START + i, stack);
                totalOutputItems += stack.getCount();
            } else {
                setStack(SLOT_OUTPUT_START + i, ItemStack.EMPTY);
            }
        }

        outputCounter = totalOutputItems;
    }

    private void fillStonecuttingOutput(StonecuttingRecipe recipe, int inputCount) {
        if (world == null) return;
        int baseXpCost = ThreeInOneUncraftingTable.CONFIG.baseXpCost;
        float xpCostMultiplier = ThreeInOneUncraftingTable.CONFIG.xpCostMultiplier;
        ItemStack recipeOutput = recipe.getResult(world.getRegistryManager());
        int recipeOutputCount = Math.max(1, recipeOutput.getCount());
        int multiplier = inputCount / recipeOutputCount;

        if (multiplier <= 0) return;
        int cost = Math.max(0, Math.round(baseXpCost * xpCostMultiplier * multiplier * 0.2F));
        ItemStack input = getStack(SLOT_INPUT);
        if (input.isDamageable()) {
            int damage = input.getDamage();
            int maxDamage = input.getMaxDamage();
            float lostRatio = (float) damage / (float) maxDamage;
            cost += (int) Math.ceil(cost * lostRatio * 1.25);
        }
        if (input.hasEnchantments() && !getStack(SLOT_BOOK).isEmpty()
                && ThreeInOneUncraftingTable.CONFIG.enableEnchantmentTransfer) {
            ItemEnchantmentsComponent enchantments = input.getEnchantments();
            for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
                int lvl = enchantments.getLevel(entry);
                cost += Math.round(2 * (1.0F + (lvl - 1) * 0.5F) * xpCostMultiplier);
            }
        }
        experienceCost = cost;
        inputConsumed = multiplier * recipeOutputCount;

        int totalOutputItems = 0;

        if (!recipe.getIngredients().isEmpty()) {
            Ingredient ing = recipe.getIngredients().getFirst();
            ItemStack[] matching = ing.getMatchingStacks();
            if (matching.length > 0) {
                ItemStack stack = matching[0].copy();
                stack.setCount(multiplier);
                setStack(SLOT_OUTPUT_START, stack);
                totalOutputItems += stack.getCount();
            }
        }

        outputCounter = totalOutputItems;
    }

    void clearOutputSlots() {
        runBatched(() -> {
            for (int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END; i++) {
                setStack(i, ItemStack.EMPTY);
            }
        });
    }

    public void cycleRecipe(int delta) {
        if (matchingRecipes.isEmpty()) return;
        selectedRecipeIndex = (selectedRecipeIndex + delta) % matchingRecipes.size();
        if (selectedRecipeIndex < 0) selectedRecipeIndex += matchingRecipes.size();
        updateOutputSlots();
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
        // 与 NeoForge 端及原版容器保持一致：8 格（平方距离 64）
        return player.squaredDistanceTo((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clear() {
        items.clear();
        markDirty();
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (world == null) return;
        if (batchDepth > 0) {
            // 批量变更中：只记录脏标记，广播由 runBatched 在批末统一发出
            batchDirty = true;
        } else {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    private void runBatched(Runnable action) {
        batchDepth++;
        try {
            action.run();
        } finally {
            batchDepth--;
        }
        // 批末将累计的方块更新广播合并为一次，避免逐槽位变更时约 18 次的重复广播
        if (batchDepth == 0 && batchDirty) {
            batchDirty = false;
            if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new UncraftingScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("container.three_in_one_uncrafting_table.uncrafting_table");
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity serverPlayerEntity) {
        return this.pos;
    }
}
