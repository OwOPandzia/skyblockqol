package barry.skyblockqol.client;

import barry.skyblockqol.client.feature.AntiBlindnessFeature;
import barry.skyblockqol.client.feature.TrapperSolver;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.world.entity.Entity;

public class SkyblockQOLClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        AntiBlindnessFeature.register();
        TrapperSolver.register();
    }

    // Kept here so LivingEntityRendererMixin doesn't need to change -
    // delegates straight through to the feature that owns the real state.
    public static GlowColor getGlowColor(Entity entity) {
        TrapperSolver.GlowColor color = TrapperSolver.getGlowColor(entity);
        return GlowColor.valueOf(color.name());
    }

    public enum GlowColor {
        NONE(0x00000000),
        WHITE(0xFFFFFFFF),
        GREEN(0xFF55FF55),
        BLUE(0xFF5555FF),
        PURPLE(0xFFAA55FF),
        YELLOW(0xFFFFFF55);

        private final int color;
        GlowColor(int color) { this.color = color; }
        public int getColor() { return color; }
    }
}