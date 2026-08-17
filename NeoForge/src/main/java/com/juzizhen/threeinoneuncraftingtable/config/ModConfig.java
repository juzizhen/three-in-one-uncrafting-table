package com.juzizhen.threeinoneuncraftingtable.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * NeoForge official standard config (ModConfigSpec + TOML).
 * The generated config file is config/three_in_one_uncrafting_table-common.toml,
 * and the comment content below is automatically written into the config file by FML.
 */
public class ModConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue BASE_XP_COST;
    public static final ModConfigSpec.DoubleValue XP_COST_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue ENABLE_CRAFTING;
    public static final ModConfigSpec.BooleanValue ENABLE_SMITHING;
    public static final ModConfigSpec.BooleanValue ENABLE_STONECUTTING;
    public static final ModConfigSpec.BooleanValue ENABLE_ENCHANTMENT_TRANSFER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        BASE_XP_COST = builder
                .comment("Base experience level cost for uncrafting",
                        "Crafting recipe: baseXpCost × count × 0.8 − count; Smithing recipe: baseXpCost × count × 1.5; Stonecutting recipe: baseXpCost × count × 0.2",
                        "Durability loss and enchantments (when enchantment transfer is enabled and a book is inserted) add extra cost")
                .defineInRange("baseXpCost", 5, 0, 1000);

        XP_COST_MULTIPLIER = builder
                .comment("Global multiplier for experience cost, 1.0 means the original cost without any modifier")
                .defineInRange("xpCostMultiplier", 1.0D, 0.0D, 100.0D);

        ENABLE_CRAFTING = builder
                .comment("Whether to allow uncrafting crafting table recipes (shaped/shapeless)")
                .define("enableCrafting", true);

        ENABLE_SMITHING = builder
                .comment("Whether to allow uncrafting smithing table recipes (including uncrafting armor with trims)")
                .define("enableSmithing", true);

        ENABLE_STONECUTTING = builder
                .comment("Whether to allow uncrafting stonecutter recipes")
                .define("enableStonecutting", true);

        ENABLE_ENCHANTMENT_TRANSFER = builder
                .comment("Whether to allow enchantment transfer: when a book is inserted, uncrafting an enchanted item transfers its enchantments to an enchanted book, with extra experience cost",
                        "When disabled, enchantments are simply lost on uncrafting, and no enchantment-related extra experience cost is applied")
                .define("enableEnchantmentTransfer", true);

        SPEC = builder.build();
    }
}