package com.juzizhen.threeinoneuncraftingtable.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.juzizhen.threeinoneuncraftingtable.ThreeInOneUncraftingTable;

import java.io.*;

public class ModConfig {
    private static final File CONFIG_FILE = new File("config/three-in-one-uncrafting-table.json");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @SuppressWarnings("unused")
    public int xpCost = 5;
    @SuppressWarnings("unused")
    public boolean enableCrafting = true;
    @SuppressWarnings("unused")
    public boolean enableSmithing = true;
    @SuppressWarnings("unused")
    public boolean enableStonecutting = true;
    @SuppressWarnings("unused")
    public boolean enableEnchantmentTransfer = true;

    public static ModConfig load() {
        if (CONFIG_FILE.exists()) {
            try (Reader reader = new FileReader(CONFIG_FILE)) {
                return gson.fromJson(reader, ModConfig.class);
            } catch (IOException e) {
                ThreeInOneUncraftingTable.LOGGER.warn(e.getMessage());
            }
        }
        ModConfig config = new ModConfig();
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


