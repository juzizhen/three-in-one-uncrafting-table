package com.juzizhen.threeinoneuncraftingtable.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * NeoForge 官方标准配置（ModConfigSpec + TOML）。
 * 生成的配置文件为 config/three_in_one_uncrafting_table-common.toml，
 * 下方 comment 内容会由 FML 自动写入配置文件作为注释。
 */
public class ModConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue BASE_XP_COST;
    public static final ModConfigSpec.DoubleValue XP_COST_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue ENABLE_CRAFTING;
    public static final ModConfigSpec.BooleanValue ENABLE_SMITHING;
    public static final ModConfigSpec.BooleanValue ENABLE_STONECUTTING;
    public static final ModConfigSpec.BooleanValue ENABLE_ENCHANTMENT_TRANSFER;
    public static final ModConfigSpec.BooleanValue ENABLE_KUBEJS_RECIPES;
    public static final ModConfigSpec.BooleanValue ENABLE_CRAFTTWEAKER_RECIPES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST_ITEMS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        BASE_XP_COST = builder
                .comment("Base experience level cost for uncrafting",
                        "Crafting recipe: baseXpCost x count x 0.8 - count; Smithing recipe: baseXpCost x count x 1.5; Stonecutting recipe: baseXpCost x count x 0.2",
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

        ENABLE_KUBEJS_RECIPES = builder
                .comment("Whether to allow uncrafting recipes added by KubeJS scripts (recipe ID namespace: kubejs)",
                        "When disabled, only vanilla and other mods' native recipes are used for uncrafting")
                .define("enableKubeJSRecipes", true);

        ENABLE_CRAFTTWEAKER_RECIPES = builder
                .comment("Whether to allow uncrafting recipes added by CraftTweaker scripts (recipe ID namespace: crafttweaker)",
                        "When disabled, only vanilla and other mods' native recipes are used for uncrafting")
                .define("enableCraftTweakerRecipes", true);

        BLACKLIST_ITEMS = builder
                .comment("Uncrafting blacklist: items in this list cannot be uncrafted",
                        "Format is the full item ID (e.g. minecraft:diamond_sword)",
                        "When placed in the input slot, no recipe will match and no output will be shown; the item is returned as-is when the GUI is closed")
                .defineListAllowEmpty("blacklistItems", List.of(), String::new, element -> element instanceof String);

        SPEC = builder.build();
    }
}