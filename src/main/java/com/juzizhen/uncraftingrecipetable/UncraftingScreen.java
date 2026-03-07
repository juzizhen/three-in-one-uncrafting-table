package com.juzizhen.uncraftingrecipetable;

import com.juzizhen.uncraftingrecipetable.block.UncraftingScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class UncraftingScreen extends HandledScreen<UncraftingScreenHandler> {
    private static final Identifier TEXTURE = new Identifier(UncraftingRecipeTable.MOD_ID, "textures/gui/uncrafting_table.png");

    public UncraftingScreen(UncraftingScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 166;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);

        Slot bookSlot = this.handler.slots.get(0);
        if (!bookSlot.hasStack()) {
            ItemStack ghostBook = new ItemStack(Items.BOOK);
            context.drawItemInSlot(this.textRenderer, ghostBook, bookSlot.x + this.x, bookSlot.y + this.y);
        }
    }


    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(TEXTURE, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, this.title, 8, 6, 4210752, false);
    }
}



