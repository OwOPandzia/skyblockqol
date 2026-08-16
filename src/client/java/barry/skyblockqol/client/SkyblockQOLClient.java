package barry.skyblockqol.client;

import barry.skyblockqol.SkyblockQOL;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class SkyblockQOLClient implements ClientModInitializer {

    private static final Map<Entity, GlowColor> TARGET_ENTITIES = new HashMap<>();

    // Starting point pulled from a known-working reference solver, NOT confirmed
    // against this server yet. Log output will tell us which of these actually
    // appear and what they should map to.
    private static final Set<Double> KNOWN_HUNT_HEALTHS = Set.of(
            100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0, 30000.0
    );

    // Values that are known/suspected to collide with unrelated mobs (e.g. plain
    // Oasis decoration spawns also sitting at 100 HP). Anything in this set
    // requires a nearby armor stand name match before we glow it.
    private static final Set<Double> AMBIGUOUS_HEALTHS = Set.of(100.0);

    private static final Map<Double, GlowColor> HEALTH_COLORS = new HashMap<>();
    static {
        // Placeholder mapping - confirm against real log output before trusting these.
        HEALTH_COLORS.put(100.0, GlowColor.WHITE);
        HEALTH_COLORS.put(200.0, GlowColor.WHITE);
        HEALTH_COLORS.put(500.0, GlowColor.GREEN);
        HEALTH_COLORS.put(1000.0, GlowColor.BLUE);
        HEALTH_COLORS.put(2000.0, GlowColor.BLUE);
        HEALTH_COLORS.put(5000.0, GlowColor.PURPLE);
        HEALTH_COLORS.put(10000.0, GlowColor.YELLOW);
        HEALTH_COLORS.put(30000.0, GlowColor.YELLOW);
    }

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(SkyblockQOLClient::tick);
    }

    private static void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            return;
        }

        client.player.removeEffect(MobEffects.BLINDNESS);

        AABB searchBox = client.player.getBoundingBox().inflate(192.0); // 12 chunks

        for (LivingEntity entity :
                client.level.getEntitiesOfClass(LivingEntity.class, searchBox)) {

            if (entity instanceof Player) continue;

            double maxHealth = entity.getMaxHealth();

            // Log every candidate so we can nail down the real mapping.
            if (KNOWN_HUNT_HEALTHS.contains(maxHealth)) {
                SkyblockQOL.LOGGER.info("Candidate {} maxHealth={}", entity.getType(), maxHealth);
            }

            if (!KNOWN_HUNT_HEALTHS.contains(maxHealth)) continue;

            if (AMBIGUOUS_HEALTHS.contains(maxHealth)) {
                String nearbyName = findNearbyArmorStandName(client, entity);
                if (nearbyName == null) {
                    SkyblockQOL.LOGGER.info(
                            " -> SKIPPED (ambiguous health {} with no confirming nametag)", maxHealth);
                    continue; // ambiguous tier without confirmation - don't glow
                }
                SkyblockQOL.LOGGER.info(" -> confirmed via nametag: '{}'", nearbyName);
            }

            GlowColor color = HEALTH_COLORS.getOrDefault(maxHealth, GlowColor.WHITE);
            TARGET_ENTITIES.put(entity, color);
        }

        TARGET_ENTITIES.keySet().removeIf(entity ->
                entity.isRemoved() || client.level.getEntity(entity.getId()) == null
        );
    }

    // Only used as a tie-breaker for ambiguous-health mobs, when an armor stand
    // happens to be in range. Returns null if none is nearby - that's fine,
    // it just means we don't glow that particular ambiguous-health mob this tick.
    private static String findNearbyArmorStandName(Minecraft client, LivingEntity entity) {
        AABB near = entity.getBoundingBox().inflate(2.0, 2.0, 2.0);
        for (ArmorStand stand : client.level.getEntitiesOfClass(ArmorStand.class, near)) {
            if (stand.hasCustomName()) {
                return stand.getCustomName().getString();
            }
        }
        return null;
    }

    public static GlowColor getGlowColor(Entity entity) {
        return TARGET_ENTITIES.getOrDefault(entity, GlowColor.NONE);
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