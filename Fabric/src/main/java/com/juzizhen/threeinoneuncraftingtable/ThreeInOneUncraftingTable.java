package com.juzizhen.threeinoneuncraftingtable;

import com.juzizhen.threeinoneuncraftingtable.block.UncraftingScreenHandler;
import com.juzizhen.threeinoneuncraftingtable.block.UncraftingTableBlock;
import com.juzizhen.threeinoneuncraftingtable.block.UncraftingTableBlockEntity;
import com.juzizhen.threeinoneuncraftingtable.config.ModConfig;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreeInOneUncraftingTable implements ModInitializer {
    public static final String MOD_ID = "three_in_one_uncrafting_table";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Block UNCRAFTING_TABLE = Registry.register(
            Registries.BLOCK,
            Identifier.of(MOD_ID, "uncrafting_table"),
            new UncraftingTableBlock(AbstractBlock.Settings.copy(Blocks.SMITHING_TABLE))
    );
    public static final Item UNCRAFTING_TABLE_ITEM = Registry.register(
            Registries.ITEM,
            Identifier.of(MOD_ID, "uncrafting_table"),
            new BlockItem(UNCRAFTING_TABLE, new Item.Settings())
    );
    public static final BlockEntityType<UncraftingTableBlockEntity> UNCRAFTING_TABLE_BLOCK_ENTITY =
            Registry.register(
                    Registries.BLOCK_ENTITY_TYPE,
                    Identifier.of(MOD_ID, "uncrafting_table"),
                    BlockEntityType.Builder.create(UncraftingTableBlockEntity::new, UNCRAFTING_TABLE).build()
            );
    public static final ScreenHandlerType<UncraftingScreenHandler> UNCRAFTING_SCREEN_HANDLER =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    Identifier.of(MOD_ID, "uncrafting_table"),
                    new ExtendedScreenHandlerType<>(UncraftingScreenHandler::new, BlockPos.PACKET_CODEC)
            );
    public static ModConfig CONFIG;
    public static boolean isTestVersion = false;
    public static String versionType = null;

    @Override
    public void onInitialize() {
        CONFIG = ModConfig.load();

        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("");
        versionType = detectVersionType(version);
        isTestVersion = versionType != null;
        if (isTestVersion) {
            LOGGER.info("Three In One Uncrafting Table (Fabric) - {} version detected", versionType);
        }

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(UNCRAFTING_TABLE_ITEM));

        LOGGER.info("Three In One Uncrafting Table Initialized!");
    }

    private static String detectVersionType(String version) {
        if (version == null || version.isEmpty()) return null;
        String lower = version.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("beta")) return "beta";
        if (lower.contains("alpha")) return "alpha";
        return null;
    }
}