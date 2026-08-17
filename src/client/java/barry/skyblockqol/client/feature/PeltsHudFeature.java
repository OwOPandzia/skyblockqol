package barry.skyblockqol.client.feature;

import barry.skyblockqol.SkyblockQOL;
import barry.skyblockqol.client.gui.PeltsHudPositionScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class PeltsHudFeature {

    public static volatile boolean hudEnabled = false;
    public static volatile boolean sessionMode = true; // true = session, false = all-time

    public static volatile int x = 10;
    public static volatile int y = 10;
    public static volatile float scale = 1.0f;

    public static final int BOX_WIDTH = 150;
    public static final int BOX_HEIGHT = 60; // header + pelts + rate + time = 4 lines

    public static void register() {
        FeatureRegistry.find("Trapper", "Trapper Solver").ifPresent(trapper -> {
            trapper.withSetting(new FeatureSetting(
                    "Pelts Hud",
                    "Shows pelts, pelts/hour and time spent hunting. Only shows while Trapper Solver is enabled. Middle-click this row to drag/resize the HUD.",
                    () -> hudEnabled,
                    v -> hudEnabled = v,
                    PeltsHudFeature::openPositionEditor
            ));
            trapper.withSetting(new FeatureSetting(
                    "Session Stats",
                    "ON: stats for this Minecraft launch only (resets when you close the game). OFF: totals across every session since you started using this mod.",
                    () -> sessionMode,
                    v -> sessionMode = v
            ));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> PeltTracker.tick());

        HudElementRegistry.addLast(SkyblockQOL.id("pelts_hud"), PeltsHudFeature::onHudRender);
    }

    private static void onHudRender(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!hudEnabled) return;
        if (Minecraft.getInstance().player == null) return;
        if (!TrapperSolver.isActive(Minecraft.getInstance())) return;
        drawHud(graphics, x, y, scale);
    }

    public static void drawHud(GuiGraphicsExtractor graphics, int drawX, int drawY, float drawScale) {
        boolean session = sessionMode;
        int pelts = session ? PeltTracker.getSessionPelts() : PeltTracker.getAllTimePelts();
        long activeMillis = session ? PeltTracker.getSessionActiveMillis() : PeltTracker.getAllTimeActiveMillis();
        double rate = session ? PeltTracker.getSessionPeltsPerHour() : PeltTracker.getAllTimePeltsPerHour();
        String header = session ? "Session" : "All-Time";

        int lineHeight = 12;

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) drawX, (float) drawY);
        graphics.pose().scale(drawScale, drawScale);

        int ty = 4;
        graphics.text(Minecraft.getInstance().font, header, 6, ty, 0xFFFFFF55, true);
        ty += lineHeight;
        graphics.text(Minecraft.getInstance().font, "Pelts: " + pelts, 6, ty, 0xFFFFFFFF, true);
        ty += lineHeight;

        String rateText = String.format("%.1f/h", rate);
        if (PeltTracker.isPaused()) rateText += " (paused)";
        graphics.text(Minecraft.getInstance().font, rateText, 6, ty, 0xFFFFFFFF, true);
        ty += lineHeight;

        graphics.text(Minecraft.getInstance().font, "Time: " + PeltTracker.formatDuration(activeMillis),
                6, ty, 0xFFFFFFFF, true);

        graphics.pose().popMatrix();
    }

    public static void openPositionEditor() {
        Minecraft.getInstance().gui.setScreen(new PeltsHudPositionScreen());
    }
}