package com.juzizhen.threeinoneuncraftingtable;

import com.juzizhen.threeinoneuncraftingtable.block.UncraftingScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class ThreeInOneUncraftingTableClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HandledScreens.register(ThreeInOneUncraftingTable.UNCRAFTING_SCREEN_HANDLER, UncraftingScreen::new);
	}
}