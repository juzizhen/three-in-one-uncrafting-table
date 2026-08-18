package com.juzizhen.threeinoneuncraftingtable.config;

import net.neoforged.neoforge.common.ModConfigSpec;

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

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        BASE_XP_COST = builder
                .comment("拆解的基础经验等级消耗",
                        "合成配方：baseXpCost × 数量 × 0.8 − 数量；锻造配方：baseXpCost × 数量 × 1.5；切石配方：baseXpCost × 数量 × 0.2",
                        "耐久损耗与附魔（启用附魔转移且放入书本时）会产生额外消耗")
                .defineInRange("baseXpCost", 5, 0, 1000);

        XP_COST_MULTIPLIER = builder
                .comment("经验消耗的全局倍率，1.0 为默认不加成的原始消耗")
                .defineInRange("xpCostMultiplier", 1.0D, 0.0D, 100.0D);

        ENABLE_CRAFTING = builder
                .comment("是否允许拆解工作台合成配方（有序/无序合成）")
                .define("enableCrafting", true);

        ENABLE_SMITHING = builder
                .comment("是否允许拆解锻造台配方（含带纹饰装备的拆解）")
                .define("enableSmithing", true);

        ENABLE_STONECUTTING = builder
                .comment("是否允许拆解切石机配方")
                .define("enableStonecutting", true);

        ENABLE_ENCHANTMENT_TRANSFER = builder
                .comment("是否允许附魔转移：放入书本时，拆解带附魔的物品会将附魔转移到附魔书上，并产生额外经验消耗",
                        "关闭后拆解时附魔将直接丢失，也不再产生附魔相关的额外经验消耗")
                .define("enableEnchantmentTransfer", true);

        ENABLE_KUBEJS_RECIPES = builder
                .comment("是否允许拆解 KubeJS 脚本添加的配方（配方 ID 命名空间为 kubejs）",
                        "关闭后仅使用原版与其他模组原生配方进行拆解")
                .define("enableKubeJSRecipes", true);

        ENABLE_CRAFTTWEAKER_RECIPES = builder
                .comment("是否允许拆解 CraftTweaker 脚本添加的配方（配方 ID 命名空间为 crafttweaker）",
                        "关闭后仅使用原版与其他模组原生配方进行拆解")
                .define("enableCraftTweakerRecipes", true);

        SPEC = builder.build();
    }
}