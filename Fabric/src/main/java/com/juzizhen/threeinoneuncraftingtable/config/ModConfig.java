package com.juzizhen.threeinoneuncraftingtable.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.juzizhen.threeinoneuncraftingtable.ThreeInOneUncraftingTable;

import java.io.*;

public class ModConfig {
    private static final File CONFIG_FILE = new File("config/three_in_one_uncrafting_table.json");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public int baseXpCost = 5;
    public float xpCostMultiplier = 1.0F;
    public boolean enableCrafting = true;
    public boolean enableSmithing = true;
    public boolean enableStonecutting = true;
    public boolean enableEnchantmentTransfer = true;
    // 脚本配方兼容开关：开启时对应 mod 添加的配方参与拆解，关闭时仅使用原版与其他 mod 原生配方
    public boolean enableKubeJSRecipes = true;
    public boolean enableCraftTweakerRecipes = true;

    public static ModConfig load() {
        ModConfig config = null;
        if (CONFIG_FILE.exists()) {
            try (Reader reader = new FileReader(CONFIG_FILE)) {
                config = gson.fromJson(reader, ModConfig.class);
            } catch (IOException e) {
                ThreeInOneUncraftingTable.LOGGER.warn(e.getMessage());
            }
        }
        if (config == null) {
            config = new ModConfig();
        }
        config.save();
        return config;
    }

    public void save() {
        try (Writer writer = new FileWriter(CONFIG_FILE)) {
            gson.toJson(this, writer);
        } catch (IOException e) {
            ThreeInOneUncraftingTable.LOGGER.warn(e.getMessage());
        }
    }
}
