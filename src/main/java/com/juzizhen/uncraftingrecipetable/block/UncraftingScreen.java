package com.juzizhen.uncraftingrecipetable.block;

import com.juzizhen.uncraftingrecipetable.UncraftingRecipeTable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
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

        if (mouseX >= this.x + 121 && mouseX < this.x + 128 &&
                mouseY >= this.y + 71 && mouseY < this.y + 82) {
            context.drawTooltip(this.textRenderer,
                    Text.translatable("tooltip.uncrafting-recipe-table.prev_recipe"),
                    mouseX, mouseY);
        }

        if (mouseX >= this.x + 137 && mouseX < this.x + 144 &&
                mouseY >= this.y + 71 && mouseY < this.y + 82) {
            context.drawTooltip(this.textRenderer,
                    Text.translatable("tooltip.uncrafting-recipe-table.next_recipe"),
                    mouseX, mouseY);
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(TEXTURE, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight);

        if (!this.handler.hasRecipes()) {
            context.drawTexture(TEXTURE, this.x + 71, this.y + 33, 176, 0, 28, 21);
        }

        boolean hoverLeft = mouseX >= this.x + 121 && mouseX < this.x + 128 &&
                mouseY >= this.y + 71 && mouseY < this.y + 82;
        if (hoverLeft) {
            context.drawTexture(TEXTURE, this.x + 121, this.y + 71, 177, 35, 7, 11); // 激活
        } else {
            context.drawTexture(TEXTURE, this.x + 121, this.y + 71, 177, 23, 7, 11); // 非激活
        }

        boolean hoverRight = mouseX >= this.x + 137 && mouseX < this.x + 144 &&
                mouseY >= this.y + 71 && mouseY < this.y + 82;
        if (hoverRight) {
            context.drawTexture(TEXTURE, this.x + 137, this.y + 71, 185, 35, 7, 11); // 激活
        } else {
            context.drawTexture(TEXTURE, this.x + 137, this.y + 71, 185, 23, 7, 11); // 非激活
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, this.title, 8, 6, 4210752, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= this.x + 121 && mouseX < this.x + 128 &&
                mouseY >= this.y + 71 && mouseY < this.y + 82) {
            if (this.client != null && this.client.interactionManager != null) {
                    this.client.interactionManager.clickButton(this.handler.syncId, 0);
            }
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        if (mouseX >= this.x + 137 && mouseX < this.x + 144 &&
                mouseY >= this.y + 71 && mouseY < this.y + 82) {
            if (this.client != null && this.client.interactionManager != null) {
                this.client.interactionManager.clickButton(this.handler.syncId, 1);
            }
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}



