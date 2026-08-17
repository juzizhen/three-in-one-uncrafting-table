package com.juzizhen.threeinoneuncraftingtable.block;

import com.juzizhen.threeinoneuncraftingtable.ThreeInOneUncraftingTable;
import com.juzizhen.threeinoneuncraftingtable.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class UncraftingTableBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_BOOK = 1;
    public static final int SLOT_OUTPUT_START = 2;
    public static final int SLOT_OUTPUT_END = 10;

    // 批量槽位变更时抑制逐次方块更新广播，批末合并为一次（可嵌套）
    private int batchDepth = 0;
    private boolean batchDirty = false;

    private final ItemStackHandler inventory = new ItemStackHandler(11) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level == null || level.isClientSide()) return;
            if (batchDepth > 0) {
                // 批量变更中：只记录脏标记，广播由 runBatched 在批末统一发出
                batchDirty = true;
            } else {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    };

    public final List<RecipeHolder<?>> matchingRecipes = new ArrayList<>();
    public int outputCounter = 0;
    public int outputGetCount = 0;
    public boolean noOutputs = true;
    public int onSlotClickIndex = 0;
    public int experienceCost = 0;
    private int selectedRecipeIndex = 0;
    private int inputConsumed = 0;

    public UncraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ThreeInOneUncraftingTable.UNCRAFTING_TABLE_BLOCK_ENTITY.get(), pos, state);
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

    void searchRecipeToOutput(ItemStack currentInput) {
        runBatched(() -> {
            matchingRecipes.clear();
            findMatchingRecipes(currentInput);

            if (!matchingRecipes.isEmpty()) {
                RecipeHolder<?> entry = matchingRecipes.get(selectedRecipeIndex);
                Recipe<?> recipe = entry.value();
                int inputCount = currentInput.getCount();

                switch (recipe) {
                    case CraftingRecipe craftingRecipe when ModConfig.ENABLE_CRAFTING.get() ->
                            fillCraftingOutput(craftingRecipe, inputCount);
                    case SmithingRecipe smithingRecipe when ModConfig.ENABLE_SMITHING.get() ->
                            fillSmithingOutput(smithingRecipe, inputCount);
                    case StonecutterRecipe stonecutterRecipe when ModConfig.ENABLE_STONECUTTING.get() ->
                            fillStonecuttingOutput(stonecutterRecipe, inputCount);
                    default -> {
                    }
                }
            }
        });
    }

    public void onInputChanged(boolean isBookInput) {
        if (level == null || level.isClientSide()) return;
        ItemStack currentInput = inventory.getStackInSlot(SLOT_INPUT);
        boolean hasOutputItems = hasOutputItems();

        if (isBookInput) {
            if (hasOutputItems && outputGetCount == 0) {
                searchRecipeToOutput(currentInput);
            }
            setChanged();
            return;
        }

        if (!hasOutputItems) {
            clearOutputSlots();
            matchingRecipes.clear();
            selectedRecipeIndex = 0;

            if (currentInput.isEmpty()) {
                setChanged();
                return;
            }

            searchRecipeToOutput(currentInput);
        } else {
            if (outputGetCount == 0) {
                clearOutputSlots();
            }
        }

        hasOutputItems = hasOutputItems();

        if (!hasOutputItems && inventory.getStackInSlot(SLOT_INPUT).isEmpty()) {
            initialization();
        }

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void onOutputChanged(ItemStack stack, Player player) {
        if (level == null || level.isClientSide()) return;
        // 空堆不触发任何消耗/补货逻辑，避免在输入被消耗前错误地重新填充输出槽（刷物品）
        if (stack.isEmpty()) return;
        boolean hasOutputItems;
        ItemStack currentInput = inventory.getStackInSlot(SLOT_INPUT);

        if (!stack.isEmpty()) {
            if (outputGetCount == 0) {
                outputGetCount++;
                if (experienceCost > 0 && !player.isCreative()) {
                    player.giveExperienceLevels(-experienceCost);
                    experienceCost = 0;
                }
                if (currentInput.isEnchanted() && !inventory.getStackInSlot(SLOT_BOOK).isEmpty()
                        && ModConfig.ENABLE_ENCHANTMENT_TRANSFER.get()) {
                    ItemStack book = inventory.getStackInSlot(SLOT_BOOK);
                    if (book.getItem() == Items.BOOK) {
                        ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
                        ItemEnchantments sourceEnchantments = currentInput.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                        ItemEnchantments.Mutable builder = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                        for (Holder<net.minecraft.world.item.enchantment.Enchantment> entry : sourceEnchantments.keySet()) {
                            int lvl = sourceEnchantments.getLevel(entry);
                            builder.set(entry, lvl);
                        }
                        enchantedBook.set(DataComponents.STORED_ENCHANTMENTS, builder.toImmutable());
                        inventory.setStackInSlot(SLOT_BOOK, enchantedBook);
                    }
                }
                int consumed = inputConsumed > 0 ? inputConsumed : currentInput.getCount();
                if (currentInput.getCount() > consumed) {
                    currentInput.setCount(currentInput.getCount() - consumed);
                } else {
                    inventory.setStackInSlot(SLOT_INPUT, ItemStack.EMPTY);
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
                if (i != currentSlotIndex && !inventory.getStackInSlot(i).isEmpty()) {
                    noOtherOutputs = false;
                    break;
                } else {
                    noOtherOutputs = true;
                }
            }

            if (noOtherOutputs && !inventory.getStackInSlot(SLOT_INPUT).isEmpty()) {
                clearOutputSlots();
                searchRecipeToOutput(currentInput);
            }
        }

        if (!hasOutputItems && inventory.getStackInSlot(SLOT_INPUT).isEmpty()) {
            initialization();
        }

        setChanged();
    }

    private void initialization() {
        inventory.setStackInSlot(SLOT_INPUT, ItemStack.EMPTY);
        clearOutputSlots();
        matchingRecipes.clear();
        selectedRecipeIndex = 0;
        noOutputs = true;
        outputGetCount = 0;
        experienceCost = 0;
        inputConsumed = 0;
        setChanged();
    }

    public void closeInventory(Player player) {
        if (level == null || level.isClientSide()) return;
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

    private void returnToPlayer(Player player, int slot) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (!stack.isEmpty()) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    private void updateOutputSlots() {
        runBatched(() -> {
            clearOutputSlots();
            if (matchingRecipes.isEmpty() || selectedRecipeIndex >= matchingRecipes.size()) return;

            RecipeHolder<?> entry = matchingRecipes.get(selectedRecipeIndex);
            Recipe<?> recipe = entry.value();
            ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
            int inputCount = input.getCount();

            switch (recipe) {
                case CraftingRecipe craftingRecipe when ModConfig.ENABLE_CRAFTING.get() ->
                        fillCraftingOutput(craftingRecipe, inputCount);
                case SmithingRecipe smithingRecipe when ModConfig.ENABLE_SMITHING.get() ->
                        fillSmithingOutput(smithingRecipe, inputCount);
                case StonecutterRecipe stonecutterRecipe when ModConfig.ENABLE_STONECUTTING.get() ->
                        fillStonecuttingOutput(stonecutterRecipe, inputCount);
                default -> {
                }
            }
        });
    }

    private void findMatchingRecipes(ItemStack input) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        matchingRecipes.clear();

        UncraftingRecipeIndex recipeIndex = UncraftingRecipeIndex.get(serverLevel);

        ArmorTrim trim = input.get(DataComponents.TRIM);
        if (trim != null) {
            RecipeHolder<?> trimRecipe = recipeIndex.getFirstTrimRecipe();
            if (trimRecipe != null) {
                matchingRecipes.add(trimRecipe);
                return;
            }
        }

        recipeIndex.collectMatching(input, matchingRecipes);
    }

    private void fillCraftingOutput(CraftingRecipe recipe, int inputCount) {
        if (level == null) return;
        int baseXpCost = ModConfig.BASE_XP_COST.get();
        float xpCostMultiplier = ModConfig.XP_COST_MULTIPLIER.get().floatValue();
        List<Ingredient> ingredients = recipe.getIngredients();
        ItemStack recipeOutput = recipe.getResultItem(level.registryAccess());
        int recipeOutputCount = Math.max(1, recipeOutput.getCount());
        int multiplier = inputCount / recipeOutputCount;

        if (multiplier <= 0) return;
        int cost = Math.max(0, Math.round(xpCostMultiplier * (baseXpCost * multiplier * 0.8F - multiplier)));
        ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
        if (input.isDamageableItem()) {
            int damage = input.getDamageValue();
            int maxDamage = input.getMaxDamage();
            float lostRatio = (float) damage / (float) maxDamage;
            cost += (int) Math.ceil(cost * lostRatio * 1.25);
        }
        if (input.isEnchanted() && !inventory.getStackInSlot(SLOT_BOOK).isEmpty()
                && ModConfig.ENABLE_ENCHANTMENT_TRANSFER.get()) {
            ItemEnchantments enchantments = input.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            for (Holder<net.minecraft.world.item.enchantment.Enchantment> entry : enchantments.keySet()) {
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
                    ItemStack[] matching = ing.getItems();

                    int slotIndex = SLOT_OUTPUT_START + row * 3 + col;

                    if (matching.length > 0) {
                        ItemStack stack = matching[0].copy();
                        stack.setCount(multiplier);
                        inventory.setStackInSlot(slotIndex, stack);
                        totalOutputItems += stack.getCount();
                    } else {
                        inventory.setStackInSlot(slotIndex, ItemStack.EMPTY);
                    }
                }
            }
        } else {
            for (int i = 0; i < ingredients.size() && i < 9; i++) {
                Ingredient ing = ingredients.get(i);
                ItemStack[] matching = ing.getItems();
                int slotIndex = SLOT_OUTPUT_START + i;

                if (matching.length > 0) {
                    ItemStack stack = matching[0].copy();
                    stack.setCount(multiplier);
                    inventory.setStackInSlot(slotIndex, stack);
                    totalOutputItems += stack.getCount();
                } else {
                    inventory.setStackInSlot(slotIndex, ItemStack.EMPTY);
                }
            }
        }

        outputCounter = totalOutputItems;
    }

    private void fillSmithingOutput(Recipe<?> recipe, int inputCount) {
        if (level == null) return;
        int baseXpCost = ModConfig.BASE_XP_COST.get();
        float xpCostMultiplier = ModConfig.XP_COST_MULTIPLIER.get().floatValue();

        ItemStack inputStack = inventory.getStackInSlot(SLOT_INPUT);
        if (inputStack.isEmpty()) return;

        ArmorTrim trim = inputStack.get(DataComponents.TRIM);

        if (trim != null) {
            if (inputCount <= 0) return;
            experienceCost = Math.round(baseXpCost * xpCostMultiplier * inputCount);
            inputConsumed = inputCount;

            Item templateItem = trim.pattern().value().templateItem().value();
            ItemStack templateStack = new ItemStack(templateItem, inputCount);
            inventory.setStackInSlot(SLOT_OUTPUT_START, templateStack);

            ItemStack baseStack = inputStack.copy();
            baseStack.setCount(inputCount);
            baseStack.remove(DataComponents.TRIM);
            ItemStack bookSlot = inventory.getStackInSlot(SLOT_BOOK);
            if (!bookSlot.isEmpty() && bookSlot.getItem() == Items.BOOK
                    && ModConfig.ENABLE_ENCHANTMENT_TRANSFER.get()) {
                baseStack.remove(DataComponents.ENCHANTMENTS);
            }
            inventory.setStackInSlot(SLOT_OUTPUT_START + 1, baseStack);

            TrimMaterial trimMaterial = trim.material().value();
            Item materialItem = trimMaterial.ingredient().value();
            ItemStack materialStack = new ItemStack(materialItem, inputCount);
            inventory.setStackInSlot(SLOT_OUTPUT_START + 2, materialStack);

            outputCounter = templateStack.getCount() + baseStack.getCount() + materialStack.getCount();
            return;
        }

        ItemStack recipeOutput = recipe.getResultItem(level.registryAccess());
        int recipeOutputCount = Math.max(1, recipeOutput.getCount());
        int multiplier = inputCount / recipeOutputCount;

        if (multiplier <= 0) return;
        int cost = Math.max(0, Math.round(baseXpCost * xpCostMultiplier * multiplier * 1.5F));
        ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
        if (input.isDamageableItem()) {
            int damage = input.getDamageValue();
            int maxDamage = input.getMaxDamage();
            float lostRatio = (float) damage / (float) maxDamage;
            cost += (int) Math.ceil(cost * lostRatio * 1.25);
        }
        if (input.isEnchanted() && !inventory.getStackInSlot(SLOT_BOOK).isEmpty()
                && ModConfig.ENABLE_ENCHANTMENT_TRANSFER.get()) {
            ItemEnchantments enchantments = input.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            for (Holder<net.minecraft.world.item.enchantment.Enchantment> entry : enchantments.keySet()) {
                int lvl = enchantments.getLevel(entry);
                cost += Math.round(2 * (1.0F + (lvl - 1) * 0.5F) * xpCostMultiplier);
            }
        }
        experienceCost = cost;
        inputConsumed = multiplier * recipeOutputCount;

        int totalOutputItems = 0;
        Ingredient[] parts = new Ingredient[3];

        if (recipe instanceof SmithingTransformRecipe transform) {
            // template/base/addition 由 META-INF/accesstransformer.cfg 开放访问
            parts[0] = transform.template;
            parts[1] = transform.base;
            parts[2] = transform.addition;
        } else {
            return;
        }

        for (int i = 0; i < 3; i++) {
            Ingredient ing = parts[i];
            if (ing != null && !ing.isEmpty()) {
                ItemStack[] matching = ing.getItems();
                if (matching.length > 0) {
                    ItemStack stack = matching[0].copy();
                    stack.setCount(multiplier);
                    inventory.setStackInSlot(SLOT_OUTPUT_START + i, stack);
                    totalOutputItems += stack.getCount();
                } else {
                    inventory.setStackInSlot(SLOT_OUTPUT_START + i, ItemStack.EMPTY);
                }
            } else {
                inventory.setStackInSlot(SLOT_OUTPUT_START + i, ItemStack.EMPTY);
            }
        }

        outputCounter = totalOutputItems;
    }

    private void fillStonecuttingOutput(StonecutterRecipe recipe, int inputCount) {
        if (level == null) return;
        int baseXpCost = ModConfig.BASE_XP_COST.get();
        float xpCostMultiplier = ModConfig.XP_COST_MULTIPLIER.get().floatValue();
        ItemStack recipeOutput = recipe.getResultItem(level.registryAccess());
        int recipeOutputCount = Math.max(1, recipeOutput.getCount());
        int multiplier = inputCount / recipeOutputCount;

        if (multiplier <= 0) return;
        int cost = Math.max(0, Math.round(baseXpCost * xpCostMultiplier * multiplier * 0.2F));
        ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
        if (input.isDamageableItem()) {
            int damage = input.getDamageValue();
            int maxDamage = input.getMaxDamage();
            float lostRatio = (float) damage / (float) maxDamage;
            cost += (int) Math.ceil(cost * lostRatio * 1.25);
        }
        if (input.isEnchanted() && !inventory.getStackInSlot(SLOT_BOOK).isEmpty()
                && ModConfig.ENABLE_ENCHANTMENT_TRANSFER.get()) {
            ItemEnchantments enchantments = input.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            for (Holder<net.minecraft.world.item.enchantment.Enchantment> entry : enchantments.keySet()) {
                int lvl = enchantments.getLevel(entry);
                cost += Math.round(2 * (1.0F + (lvl - 1) * 0.5F) * xpCostMultiplier);
            }
        }
        experienceCost = cost;
        inputConsumed = multiplier * recipeOutputCount;

        int totalOutputItems = 0;

        if (!recipe.getIngredients().isEmpty()) {
            Ingredient ing = recipe.getIngredients().getFirst();
            ItemStack[] matching = ing.getItems();
            if (matching.length > 0) {
                ItemStack stack = matching[0].copy();
                stack.setCount(multiplier);
                inventory.setStackInSlot(SLOT_OUTPUT_START, stack);
                totalOutputItems += stack.getCount();
            }
        }

        outputCounter = totalOutputItems;
    }

    void clearOutputSlots() {
        runBatched(() -> {
            for (int i = SLOT_OUTPUT_START; i <= SLOT_OUTPUT_END; i++) {
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        });
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
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public void cycleRecipe(int delta) {
        if (matchingRecipes.isEmpty()) return;
        selectedRecipeIndex = (selectedRecipeIndex + delta) % matchingRecipes.size();
        if (selectedRecipeIndex < 0) selectedRecipeIndex += matchingRecipes.size();
        updateOutputSlots();
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
