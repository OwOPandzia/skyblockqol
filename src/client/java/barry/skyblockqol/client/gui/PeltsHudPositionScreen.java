package barry.skyblockqol.client.gui;

import barry.skyblockqol.client.feature.PeltsHudFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Opened by middle-clicking the "Pelts Hud" setting row. Dragging is
 * computed from the mouseX/mouseY already passed into extractRenderState
 * each frame rather than a mouseDragged override, so we don't need to
 * verify yet another input method's signature.
 *
 * NOTE: "scale" here only resizes the edit-mode box outline - actually
 * scaling the drawn text needs pose-stack scaling like your Odin reference's
 * HudManager.kt does (guiGraphics.pose().pushMatrix()/scale()/popMatrix()).
 * Left as a follow-up rather than guessing at another unverified call chain.
 */
public class PeltsHudPositionScreen extends Screen {

    private boolean dragging = false;
    private int dragOffsetX;
    private int dragOffsetY;

    private int lastMouseX;
    private int lastMouseY;

    public PeltsHudPositionScreen() {
        super(Component.literal("Position Pelts HUD"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        if (dragging) {
            PeltsHudFeature.x = mouseX + dragOffsetX;
            PeltsHudFeature.y = mouseY + dragOffsetY;
        }

        int width = (int) (PeltsHudFeature.BOX_WIDTH * PeltsHudFeature.scale);
        int height = (int) (PeltsHudFeature.BOX_HEIGHT * PeltsHudFeature.scale);

        PeltsHudFeature.drawHud(graphics, PeltsHudFeature.x, PeltsHudFeature.y, PeltsHudFeature.scale);

        int c = 0xFF2E7BD6;
        graphics.fill(PeltsHudFeature.x - 1, PeltsHudFeature.y - 1, PeltsHudFeature.x + width + 1, PeltsHudFeature.y, c);
        graphics.fill(PeltsHudFeature.x - 1, PeltsHudFeature.y + height, PeltsHudFeature.x + width + 1, PeltsHudFeature.y + height + 1, c);
        graphics.fill(PeltsHudFeature.x - 1, PeltsHudFeature.y, PeltsHudFeature.x, PeltsHudFeature.y + height, c);
        graphics.fill(PeltsHudFeature.x + width, PeltsHudFeature.y, PeltsHudFeature.x + width + 1, PeltsHudFeature.y + height, c);

        graphics.centeredText(this.font, "Drag to move, scroll to resize, Escape to save & close",
                this.width / 2, this.height - 20, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && isOverHud(lastMouseX, lastMouseY)) {
            dragging = true;
            dragOffsetX = PeltsHudFeature.x - lastMouseX;
            dragOffsetY = PeltsHudFeature.y - lastMouseY;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isOverHud((int) mouseX, (int) mouseY)) {
            float delta = (float) Math.signum(verticalAmount) * 0.1f;
            PeltsHudFeature.scale = Math.max(0.5f, Math.min(3.0f, PeltsHudFeature.scale + delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean isOverHud(int mouseX, int mouseY) {
        int width = (int) (PeltsHudFeature.BOX_WIDTH * PeltsHudFeature.scale);
        int height = (int) (PeltsHudFeature.BOX_HEIGHT * PeltsHudFeature.scale);
        return mouseX >= PeltsHudFeature.x && mouseX < PeltsHudFeature.x + width
                && mouseY >= PeltsHudFeature.y && mouseY < PeltsHudFeature.y + height;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}