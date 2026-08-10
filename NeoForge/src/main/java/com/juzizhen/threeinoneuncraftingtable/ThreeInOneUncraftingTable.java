package com.juzizhen.threeinoneuncraftingtable;

import com.juzizhen.threeinoneuncraftingtable.block.UncraftingScreen;
import com.juzizhen.threeinoneuncraftingtable.block.UncraftingScreenHandler;
import com.juzizhen.threeinoneuncraftingtable.block.UncraftingTableBlock;
import com.juzizhen.threeinoneuncraftingtable.block.UncraftingTableBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import org.slf4j.Logger;

@Mod(ThreeInOneUncraftingTable.MOD_ID)
public class ThreeInOneUncraftingTable {
    public static final String MOD_ID = "three_in_one_uncrafting_table";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredBlock<UncraftingTableBlock> UNCRAFTING_TABLE =
            BLOCKS.register("uncrafting_table",
                    () -> new UncraftingTableBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SMITHING_TABLE)));

    public static final DeferredItem<BlockItem> UNCRAFTING_TABLE_ITEM =
            ITEMS.registerSimpleBlockItem("uncrafting_table", UNCRAFTING_TABLE);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UncraftingTableBlockEntity>> UNCRAFTING_TABLE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("uncrafting_table",
                    () -> BlockEntityType.Builder.of(UncraftingTableBlockEntity::new, UNCRAFTING_TABLE.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<UncraftingScreenHandler>> UNCRAFTING_SCREEN_HANDLER =
            MENUS.register("uncrafting_table",
                    () -> IMenuTypeExtension.create((windowId, inv, data) -> {
                        var pos = data.readBlockPos();
                        UncraftingTableBlockEntity be = (UncraftingTableBlockEntity) inv.player.level().getBlockEntity(pos);
                        return new UncraftingScreenHandler(windowId, inv, be);
                    }));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            CREATIVE_TABS.register("main",
                    () -> CreativeModeTab.builder()
                            .withTabsBefore(CreativeModeTabs.COMBAT)
                            .icon(() -> UNCRAFTING_TABLE_ITEM.get().getDefaultInstance())
                            .displayItems((params, output) -> output.accept(UNCRAFTING_TABLE_ITEM.get()))
                            .build());

    public ThreeInOneUncraftingTable(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::registerScreens);

        LOGGER.info("Three In One Uncrafting Table (NeoForge) Initialized!");
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(UNCRAFTING_SCREEN_HANDLER.get(), UncraftingScreen::new);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                UNCRAFTING_TABLE_BLOCK_ENTITY.get(),
                (blockEntity, side) -> blockEntity.getInventory()
        );
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(UNCRAFTING_TABLE_ITEM);
        }
    }

}
