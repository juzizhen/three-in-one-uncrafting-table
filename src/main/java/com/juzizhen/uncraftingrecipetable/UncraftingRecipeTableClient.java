package com.juzizhen.uncraftingrecipetable;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class UncraftingRecipeTableClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HandledScreens.register(UncraftingRecipeTable.UNCRAFTING_SCREEN_HANDLER, UncraftingScreen::new);
	}
}