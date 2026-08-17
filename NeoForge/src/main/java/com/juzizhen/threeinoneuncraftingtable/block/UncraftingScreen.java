package com.juzizhen.threeinoneuncraftingtable.block;

import com.juzizhen.threeinoneuncraftingtable.ThreeInOneUncraftingTable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class UncraftingScreen extends AbstractContainerScreen<UncraftingScreenHandler> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ThreeInOneUncraftingTable.MOD_ID, "textures/gui/uncrafting_table.png");

    private static final int BTN_LEFT_X = 117;
    private static final int BTN_LEFT_Y = 71;
    private static final int BTN_LEFT_W = 7;
    private static final int BTN_LEFT_H = 11;

    private static final int BTN_RIGHT_X = 141;
    private static final int BTN_RIGHT_Y = 71;
    private static final int BTN_RIGHT_W = 7;
    private static final int BTN_RIGHT_H = 11;

    private static final int BTN_CENTER_W = 11;
    private static final int BTN_CENTER_H = 7;
    private static final int BTN_CENTER_X = (BTN_LEFT_X + BTN_LEFT_W / 2 + BTN_RIGHT_X + BTN_RIGHT_W / 2) / 2 - BTN_CENTER_W / 2;
    private static final int BTN_CENTER_Y = 73;

    private static final long WARNING_DURATION_MS = 3000;
    private static boolean warningShownThisSession = false;
    private long openTime = -1;

    public UncraftingScreen(UncraftingScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        openTime = Util.getMillis();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        if (ThreeInOneUncraftingTable.isTestVersion && !warningShownThisSession && openTime >= 0) {
            long elapsed = Util.getMillis() - openTime;
            if (elapsed < WARNING_DURATION_MS) {
                renderTestVersionWarning(guiGraphics, elapsed);
            } else {
                warningShownThisSession = true;
            }
        }

        if (isHoveringLeftButton(mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font,
                    List.of(Component.translatable("tooltip." + ThreeInOneUncraftingTable.MOD_ID + ".prev_recipe").getVisualOrderText()),
                    mouseX, mouseY);
        }

        if (isHoveringCenterButton(mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font,
                    List.of(Component.translatable("tooltip." + ThreeInOneUncraftingTable.MOD_ID + ".move_all").getVisualOrderText()),
                    mouseX, mouseY);
        }

        if (isHoveringRightButton(mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font,
                    List.of(Component.translatable("tooltip." + ThreeInOneUncraftingTable.MOD_ID + ".next_recipe").getVisualOrderText()),
                    mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);

        if (!this.menu.hasRecipes() || menu.blockEntity.getInventory().getStackInSlot(UncraftingTableBlockEntity.SLOT_INPUT).isEmpty()) {
            guiGraphics.blit(TEXTURE, this.leftPos + 71, this.topPos + 33, 176, 0, 28, 21, 256, 256);
        }

        boolean hoverLeft = isHoveringLeftButton(mouseX, mouseY);
        guiGraphics.blit(TEXTURE, this.leftPos + BTN_LEFT_X, this.topPos + BTN_LEFT_Y,
                177, hoverLeft ? 35 : 23, 7, 11, 256, 256);

        boolean hoverCenter = isHoveringCenterButton(mouseX, mouseY);
        guiGraphics.blit(TEXTURE, this.leftPos + BTN_CENTER_X, this.topPos + BTN_CENTER_Y,
                177, hoverCenter ? 57 : 49, 11, 7, 256, 256);

        boolean hoverRight = isHoveringRightButton(mouseX, mouseY);
        guiGraphics.blit(TEXTURE, this.leftPos + BTN_RIGHT_X, this.topPos + BTN_RIGHT_Y,
                185, hoverRight ? 35 : 23, 7, 11, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 8, 6, 4210752, false);

        if (!menu.blockEntity.getInventory().getStackInSlot(UncraftingTableBlockEntity.SLOT_INPUT).isEmpty()) {
            int xpCost = this.menu.blockEntity.experienceCost;
            if (xpCost > 0) {
                boolean hasEnoughXp = false;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    hasEnoughXp = mc.player.isCreative() || mc.player.experienceLevel >= xpCost;
                }
                int color = hasEnoughXp ? 8453920 : 16736352;

                Component xpText = Component.translatable("tooltip." + ThreeInOneUncraftingTable.MOD_ID + ".need_xp", xpCost);
                int textWidth = this.font.width(xpText);

                guiGraphics.drawString(this.font, xpText, this.imageWidth - textWidth - 80, 64, color, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Minecraft mc = Minecraft.getInstance();

        if (isHoveringLeftButton((int) mouseX, (int) mouseY)) {
            if (mc.gameMode != null) {
                mc.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
            }
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        if (isHoveringCenterButton((int) mouseX, (int) mouseY)) {
            if (mc.gameMode != null) {
                mc.gameMode.handleInventoryButtonClick(this.menu.containerId, 2);
            }
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        if (isHoveringRightButton((int) mouseX, (int) mouseY)) {
            if (mc.gameMode != null) {
                mc.gameMode.handleInventoryButtonClick(this.menu.containerId, 1);
            }
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isHoveringLeftButton(int mouseX, int mouseY) {
        return mouseX >= this.leftPos + BTN_LEFT_X && mouseX < this.leftPos + BTN_LEFT_X + BTN_LEFT_W &&
                mouseY >= this.topPos + BTN_LEFT_Y && mouseY < this.topPos + BTN_LEFT_Y + BTN_LEFT_H;
    }

    private boolean isHoveringCenterButton(int mouseX, int mouseY) {
        return mouseX >= this.leftPos + BTN_CENTER_X && mouseX < this.leftPos + BTN_CENTER_X + BTN_CENTER_W &&
                mouseY >= this.topPos + BTN_CENTER_Y && mouseY < this.topPos + BTN_CENTER_Y + BTN_CENTER_H;
    }

    private boolean isHoveringRightButton(int mouseX, int mouseY) {
        return mouseX >= this.leftPos + BTN_RIGHT_X && mouseX < this.leftPos + BTN_RIGHT_X + BTN_RIGHT_W &&
                mouseY >= this.topPos + BTN_RIGHT_Y && mouseY < this.topPos + BTN_RIGHT_Y + BTN_RIGHT_H;
    }

    private void renderTestVersionWarning(GuiGraphics guiGraphics, long elapsedMs) {
        float alpha = 1.0F;
        if (elapsedMs > WARNING_DURATION_MS - 1000) {
            alpha = (WARNING_DURATION_MS - elapsedMs) / 1000.0F;
            alpha = Math.clamp(alpha, 0.0F, 1.0F);
        }

        if (alpha <= 0.0F) {
            return;
        }

        int bgColor = ((int) (alpha * 200) << 24) | 0x00CC4400;
        int bannerX = this.leftPos;
        int bannerY = this.topPos - 24;
        int bannerW = this.imageWidth;
        int bannerH = 22;

        guiGraphics.fill(bannerX, bannerY, bannerX + bannerW, bannerY + bannerH, bgColor);

        int textAlpha = (int) (alpha * 255);

        if (textAlpha >= 4) {
            int textColor = (textAlpha << 24) | 0x00FFFFFF;

            Component line1 = Component.translatable(
                    "gui." + ThreeInOneUncraftingTable.MOD_ID + ".test_version_warning",
                    ThreeInOneUncraftingTable.versionType);
            Component line2 = Component.translatable(
                    "gui." + ThreeInOneUncraftingTable.MOD_ID + ".report_issues");

            int centerX = this.leftPos + this.imageWidth / 2;
            guiGraphics.drawCenteredString(this.font, line1, centerX, bannerY + 3, textColor);
            guiGraphics.drawCenteredString(this.font, line2, centerX, bannerY + 12, textColor);
        }
    }
}