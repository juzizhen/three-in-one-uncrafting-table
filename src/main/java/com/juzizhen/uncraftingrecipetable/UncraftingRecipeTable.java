package com.juzizhen.uncraftingrecipetable;

import com.juzizhen.uncraftingrecipetable.block.UncraftingScreenHandler;
import com.juzizhen.uncraftingrecipetable.block.UncraftingTableBlock;
import com.juzizhen.uncraftingrecipetable.block.UncraftingTableBlockEntity;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UncraftingRecipeTable implements ModInitializer {
	public static final String MOD_ID = "uncrafting-recipe-table";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(UNCRAFTING_TABLE_ITEM));

		LOGGER.info("Uncrafting Recipe Table Initialized!");
	}

	public static final Block UNCRAFTING_TABLE = Registry.register(
			Registries.BLOCK,
			Identifier.of(MOD_ID, "uncrafting_table"),
			new UncraftingTableBlock(FabricBlockSettings.copyOf(Blocks.SMITHING_TABLE))
	);

	public static final Item UNCRAFTING_TABLE_ITEM = Registry.register(
			Registries.ITEM,
			Identifier.of(MOD_ID, "uncrafting_table"),
			new BlockItem(UNCRAFTING_TABLE, new FabricItemSettings())
	);

	public static final BlockEntityType<UncraftingTableBlockEntity> UNCRAFTING_TABLE_BLOCK_ENTITY =
			Registry.register(
					Registries.BLOCK_ENTITY_TYPE,
					Identifier.of(MOD_ID, "uncrafting_table"),
					FabricBlockEntityTypeBuilder.create(UncraftingTableBlockEntity::new, UNCRAFTING_TABLE).build()
			);

	public static final ScreenHandlerType<UncraftingScreenHandler> UNCRAFTING_SCREEN_HANDLER =
			Registry.register(
					Registries.SCREEN_HANDLER,
					Identifier.of(MOD_ID, "uncrafting_table"),
					new ExtendedScreenHandlerType<>((syncId, inv, buf) ->
							new UncraftingScreenHandler(syncId, inv,
									(Inventory) inv.player.getWorld().getBlockEntity(buf.readBlockPos())))
			);
}