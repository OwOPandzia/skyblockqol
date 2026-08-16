package barry.skyblockqol.client.feature;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;

public class AntiBlindnessFeature {

    public static volatile boolean enabled = true;

    public static void register() {
        FeatureRegistry.register(new Feature(
                "Anti Blindness",
                "Automatically clears the Blindness effect Hypixel Skyblock applies while hunting.",
                () -> enabled,
                v -> enabled = v
        ));
        ClientTickEvents.END_CLIENT_TICK.register(AntiBlindnessFeature::tick);
    }

    private static void tick(Minecraft client) {
        if (!enabled) return;
        if (client.player == null) return;

        if (client.player.hasEffect(MobEffects.BLINDNESS)) {
            client.player.removeEffect(MobEffects.BLINDNESS);
        }
    }
}