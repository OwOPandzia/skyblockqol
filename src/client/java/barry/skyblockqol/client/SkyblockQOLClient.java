package barry.skyblockqol.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class SkyblockQOLClient implements ClientModInitializer {

    private static final Map<String, GlowColor> TARGET_NAMES = new HashMap<>();
    private static final Map<Entity, GlowColor> TARGET_ENTITIES = new HashMap<>();

    static {
        TARGET_NAMES.put("Endangered", GlowColor.PURPLE);
        TARGET_NAMES.put("Elusive", GlowColor.YELLOW);
        TARGET_NAMES.put("Trackable", GlowColor.WHITE);
        TARGET_NAMES.put("Untrackable", GlowColor.GREEN);
        TARGET_NAMES.put("Undetected", GlowColor.BLUE);
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
        TARGET_ENTITIES.clear();

        AABB searchBox = client.player.getBoundingBox().inflate(128.0);

        for (ArmorStand armorStand :
                client.level.getEntitiesOfClass(ArmorStand.class, searchBox)) {

            if (!armorStand.hasCustomName()) continue;

            String name = armorStand.getCustomName().getString();
            GlowColor color = getGlowColor(name);
            if (color == GlowColor.NONE) continue;

            // 1. Try the vehicle (armor stand is riding the mob)
            Entity target = armorStand.getVehicle();
            if (target != null) {
                TARGET_ENTITIES.put(target, color);
                continue;
            }

            // 2. Try passengers (mob is riding the armor stand)
            List<Entity> passengers = armorStand.getPassengers();
            if (!passengers.isEmpty()) {
                for (Entity passenger : passengers) {
                    TARGET_ENTITIES.put(passenger, color);
                }
                continue;
            }

            // 3. Fallback: search for the nearest living entity within 2 blocks
            AABB near = armorStand.getBoundingBox().inflate(2.0);
            List<LivingEntity> nearby = client.level.getEntitiesOfClass(
                    LivingEntity.class, near,
                    e -> e != armorStand && !(e instanceof ArmorStand)
            );
            if (!nearby.isEmpty()) {
                // pick the closest one
                Entity closest = nearby.stream()
                        .min(Comparator.comparingDouble(
                                e -> e.distanceToSqr(armorStand)
                        ))
                        .orElse(null);
                if (closest != null) {
                    TARGET_ENTITIES.put(closest, color);
                }
            }
        }
    }

    private static GlowColor getGlowColor(String name) {
        if (name == null || name.isEmpty()) {
            return GlowColor.NONE;
        }

        String normalized = name
                .replaceAll("§[0-9A-FK-ORa-fk-or]", "")
                .toLowerCase(Locale.ROOT);

        for (Map.Entry<String, GlowColor> entry : TARGET_NAMES.entrySet()) {

            if (normalized.contains(
                    entry.getKey().toLowerCase(Locale.ROOT))) {

                return entry.getValue();
            }
        }

        return GlowColor.NONE;
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

        GlowColor(int color) {
            this.color = color;
        }

        public int getColor() {
            return color;
        }
    }
}