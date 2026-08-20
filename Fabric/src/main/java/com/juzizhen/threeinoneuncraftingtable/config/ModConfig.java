package com.juzizhen.threeinoneuncraftingtable.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.juzizhen.threeinoneuncraftingtable.ThreeInOneUncraftingTable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

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
    // 拆解黑名单：列表中的物品禁止被拆解，格式为完整物品 ID（如 "minecraft:diamond_sword"），
    // 放入输入槽后不匹配任何配方、不显示输出，关闭 GUI 时原样归还
    public List<String> blacklistItems = new ArrayList<>();

    public static ModConfig load() {
        ModConfig config = null;
        if (CONFIG_FILE.exists()) {
            try (Reader reader = new FileReader(CONFIG_FILE)) {
                config = gson.fromJson(reader, ModConfig.class);
            } catch (IOException e) {
                ThreeInOneUncraftingTable.LOGGER.warn(e.getMessage());
            } catch (JsonParseException e) {
                // 用户手改导致的 JSON 语法错误：回退默认配置并重写文件，避免启动崩溃
                ThreeInOneUncraftingTable.LOGGER.warn("Config file {} is corrupted, falling back to defaults: {}",
                        CONFIG_FILE, e.getMessage());
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
