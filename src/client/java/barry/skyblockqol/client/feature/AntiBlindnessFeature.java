package barry.skyblockqol.client.feature;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import barry.skyblockqol.client.feature.Feature;
import barry.skyblockqol.client.feature.FeatureRegistry;

public class AntiBlindnessFeature {

    // Swap this for your Feature/ModConfig toggle once wired up.
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
}