package barry.skyblockqol.client.feature;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;

public class AntiBlindnessFeature {

    // Swap this for your Feature/ModConfig toggle once wired up.
    public static volatile boolean enabled = true;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AntiBlindnessFeature::tick);
    }

    private static void tick(Minecraft client) {
        if (!enabled) return;
        if (client.player == null) return;

        client.player.removeEffect(MobEffects.BLINDNESS);
    }
}