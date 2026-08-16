package barry.skyblockqol.client;

import barry.skyblockqol.SkyblockQOL;
import barry.skyblockqol.client.feature.AntiBlindnessFeature;
import barry.skyblockqol.client.feature.TrapperSolver;
import barry.skyblockqol.client.gui.FeatureMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

public class SkyblockQOLClient implements ClientModInitializer {

    private static KeyMapping openMenuKey;

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(SkyblockQOL.id("main"));

    @Override
    public void onInitializeClient() {
        AntiBlindnessFeature.register();
        TrapperSolver.register();

        openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.skyblockqol.open_menu",
                GLFW.GLFW_KEY_RIGHT_BRACKET,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                if (client.gui.screen(); == activescreen) {
                    client.setScreenAndShow(new FeatureMenuScreen());
                }
            }
        });
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