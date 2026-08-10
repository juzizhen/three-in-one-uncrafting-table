package com.juzizhen.threeinoneuncraftingtable.block;

import com.juzizhen.threeinoneuncraftingtable.ThreeInOneUncraftingTable;
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
    private static final Identifier TEXTURE = Identifier.of(ThreeInOneUncraftingTable.MOD_ID, "textures/gui/uncrafting_table.png");

    // 左按钮（上一个配方）
    private static final int BTN_LEFT_X = 117;
    private static final int BTN_LEFT_Y = 71;
    private static final int BTN_LEFT_W = 7;
    private static final int BTN_LEFT_H = 11;

    // 右按钮（下一个配方）
    private static final int BTN_RIGHT_X = 141;
    private static final int BTN_RIGHT_Y = 71;
    private static final int BTN_RIGHT_W = 7;
    private static final int BTN_RIGHT_H = 11;

    // 中间按钮（一键收取）— 水平居中于左右按钮之间
    private static final int BTN_CENTER_W = 11;
    private static final int BTN_CENTER_H = 7;
    private static final int BTN_CENTER_X = (BTN_LEFT_X + BTN_LEFT_W / 2 + BTN_RIGHT_X + BTN_RIGHT_W / 2) / 2 - BTN_CENTER_W / 2;
    private static final int BTN_CENTER_Y = 73;

    public UncraftingScreen(UncraftingScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 166;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);

        Slot bookSlot = this.handler.slots.getFirst();
        if (!bookSlot.hasStack()) {
            ItemStack ghostBook = new ItemStack(Items.BOOK);
            context.drawItemInSlot(this.textRenderer, ghostBook, bookSlot.x + this.x, bookSlot.y + this.y);
        }

        if (mouseX >= this.x + BTN_LEFT_X && mouseX < this.x + BTN_LEFT_X + BTN_LEFT_W &&
                mouseY >= this.y + BTN_LEFT_Y && mouseY < this.y + BTN_LEFT_Y + BTN_LEFT_H) {
            context.drawTooltip(this.textRenderer,
                    Text.translatable("tooltip.three_in_one_uncrafting_table.prev_recipe"),
                    mouseX, mouseY);
        }

        if (mouseX >= this.x + BTN_CENTER_X && mouseX < this.x + BTN_CENTER_X + BTN_CENTER_W &&
                mouseY >= this.y + BTN_CENTER_Y && mouseY < this.y + BTN_CENTER_Y + BTN_CENTER_H) {
            context.drawTooltip(this.textRenderer,
                    Text.translatable("tooltip.three_in_one_uncrafting_table.move_all"),
                    mouseX, mouseY);
        }

        if (mouseX >= this.x + BTN_RIGHT_X && mouseX < this.x + BTN_RIGHT_X + BTN_RIGHT_W &&
                mouseY >= this.y + BTN_RIGHT_Y && mouseY < this.y + BTN_RIGHT_Y + BTN_RIGHT_H) {
            context.drawTooltip(this.textRenderer,
                    Text.translatable("tooltip.three_in_one_uncrafting_table.next_recipe"),
                    mouseX, mouseY);
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(TEXTURE, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight, 256, 256);

        if (!this.handler.hasRecipes() || handler.blockEntity.getStack(UncraftingTableBlockEntity.SLOT_INPUT) == ItemStack.EMPTY) {
            context.drawTexture(TEXTURE, this.x + 71, this.y + 33, 176, 0, 28, 21, 256, 256);
        }

        boolean hoverLeft = mouseX >= this.x + BTN_LEFT_X && mouseX < this.x + BTN_LEFT_X + BTN_LEFT_W &&
                mouseY >= this.y + BTN_LEFT_Y && mouseY < this.y + BTN_LEFT_Y + BTN_LEFT_H;
        if (hoverLeft) {
            context.drawTexture(TEXTURE, this.x + BTN_LEFT_X, this.y + BTN_LEFT_Y, 177, 35, 7, 11, 256, 256);
        } else {
            context.drawTexture(TEXTURE, this.x + BTN_LEFT_X, this.y + BTN_LEFT_Y, 177, 23, 7, 11, 256, 256);
        }

        boolean hoverCenter = mouseX >= this.x + BTN_CENTER_X && mouseX < this.x + BTN_CENTER_X + BTN_CENTER_W &&
                mouseY >= this.y + BTN_CENTER_Y && mouseY < this.y + BTN_CENTER_Y + BTN_CENTER_H;
        if (hoverCenter) {
            context.drawTexture(TEXTURE, this.x + BTN_CENTER_X, this.y + BTN_CENTER_Y, 177, 57, 11, 7, 256, 256);
        } else {
            context.drawTexture(TEXTURE, this.x + BTN_CENTER_X, this.y + BTN_CENTER_Y, 177, 49, 11, 7, 256, 256);
        }

        boolean hoverRight = mouseX >= this.x + BTN_RIGHT_X && mouseX < this.x + BTN_RIGHT_X + BTN_RIGHT_W &&
                mouseY >= this.y + BTN_RIGHT_Y && mouseY < this.y + BTN_RIGHT_Y + BTN_RIGHT_H;
        if (hoverRight) {
            context.drawTexture(TEXTURE, this.x + BTN_RIGHT_X, this.y + BTN_RIGHT_Y, 185, 35, 7, 11, 256, 256);
        } else {
            context.drawTexture(TEXTURE, this.x + BTN_RIGHT_X, this.y + BTN_RIGHT_Y, 185, 23, 7, 11, 256, 256);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, this.title, 8, 6, 4210752, false);

        if (handler.blockEntity.getStack(UncraftingTableBlockEntity.SLOT_INPUT) != ItemStack.EMPTY) {
            int xpCost = this.handler.blockEntity.experienceCost;
            if (xpCost > 0) {
                boolean hasEnoughXp = false;
                if (this.client != null && this.client.player != null) {
                    hasEnoughXp = this.client.player.isCreative() || this.client.player.experienceLevel >= xpCost;
                }
                int color = hasEnoughXp ? 8453920 : 16736352;

                Text xpText = Text.translatable("tooltip.three_in_one_uncrafting_table.need_xp", xpCost);
                int textWidth = this.textRenderer.getWidth(xpText);

                context.drawText(this.textRenderer, xpText, this.backgroundWidth - textWidth - 80, 64, color, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= this.x + BTN_LEFT_X && mouseX < this.x + BTN_LEFT_X + BTN_LEFT_W &&
                mouseY >= this.y + BTN_LEFT_Y && mouseY < this.y + BTN_LEFT_Y + BTN_LEFT_H) {
            if (this.client != null && this.client.interactionManager != null) {
                this.client.interactionManager.clickButton(this.handler.syncId, 0);
            }
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        if (mouseX >= this.x + BTN_CENTER_X && mouseX < this.x + BTN_CENTER_X + BTN_CENTER_W &&
                mouseY >= this.y + BTN_CENTER_Y && mouseY < this.y + BTN_CENTER_Y + BTN_CENTER_H) {
            if (this.client != null && this.client.interactionManager != null) {
                this.client.interactionManager.clickButton(this.handler.syncId, 2);
            }
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        if (mouseX >= this.x + BTN_RIGHT_X && mouseX < this.x + BTN_RIGHT_X + BTN_RIGHT_W &&
                mouseY >= this.y + BTN_RIGHT_Y && mouseY < this.y + BTN_RIGHT_Y + BTN_RIGHT_H) {
            if (this.client != null && this.client.interactionManager != null) {
                this.client.interactionManager.clickButton(this.handler.syncId, 1);
            }
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
