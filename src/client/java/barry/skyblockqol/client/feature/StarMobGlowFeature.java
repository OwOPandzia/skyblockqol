package barry.skyblockqol.client.feature;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Glows dungeon "star" mobs orange. Restricted to Catacombs, mirroring
 * TrapperSolver's area-gate pattern.
 *
 * Two things this had to work around, both confirmed via EntityNbtDumper:
 *
 * 1. The "✯ Mob Name" text is NOT the mob entity's own CustomName - it
 *    lives on a separate invisible marker ArmorStand hovering above the
 *    mob. Matched by horizontal (X/Z) proximity.
 *
 * 2. Some dungeon mobs (e.g. "Crypt Souleater") render using a fake
 *    player-model entity client-side, not a Zombie/Skeleton LivingEntity.
 *    Naively excluding "instanceof Player" (to skip real players) was
 *    also excluding these mobs. Fixed by only treating a Player entity as
 *    a REAL player if its UUID is actually in the tab list - fake mob
 *    NPCs aren't listed there. Same tab-list technique TrapperSolver uses
 *    for area detection.
 */
public class StarMobGlowFeature {

    public static volatile boolean enabled = true;

    private static final double SEARCH_RANGE = 192.0;
    private static final String STAR_GLYPH = "✯";

    // Max horizontal (X/Z) distance between a star-glyph nametag stand and
    // the mob it labels. Dumps showed pairs within ~0.1 blocks.
    private static final double NAMETAG_MATCH_RADIUS = 1.0;

    // VERIFY: exact substring Hypixel shows in the tab list while inside
    // Catacombs - guessed by analogy with TrapperSolver's
    // "Area: The Farming Islands" check. Check your tab list in-game
    // (default Tab key) and adjust this string if it doesn't match.
    private static final String REQUIRED_DUNGEON_TEXT = "Catacombs";

    private static final Set<Entity> GLOWING_ENTITIES = new HashSet<>();

    public static void register() {
        FeatureRegistry.register(new Feature(
                "Dungeons",
                "Star Mob Glow",
                "Glows mobs with a star (✯) in their nametag orange. Only active in Catacombs.",
                () -> enabled,
                v -> enabled = v
        ));
        ClientTickEvents.END_CLIENT_TICK.register(StarMobGlowFeature::tick);
    }

    private static void tick(Minecraft client) {
        GLOWING_ENTITIES.clear();

        if (!enabled) return;
        if (client.player == null || client.level == null) return;
        if (!isInCatacombs(client)) return;

        AABB searchBox = client.player.getBoundingBox().inflate(SEARCH_RANGE);

        List<ArmorStand> starStands = new ArrayList<>();
        for (ArmorStand stand : client.level.getEntitiesOfClass(ArmorStand.class, searchBox)) {
            Component name = stand.getCustomName();
            if (name == null) continue;
            if (name.getString().contains(STAR_GLYPH)) {
                starStands.add(stand);
            }
        }

        if (starStands.isEmpty()) return;

        double matchRadiusSq = NAMETAG_MATCH_RADIUS * NAMETAG_MATCH_RADIUS;

        for (LivingEntity entity : client.level.getEntitiesOfClass(LivingEntity.class, searchBox)) {
            if (entity instanceof ArmorStand) continue; // don't glow the label stands themselves
            if (isRealPlayer(entity, client)) continue; // skip actual players, keep player-model mobs

            for (ArmorStand stand : starStands) {
                double dx = entity.getX() - stand.getX();
                double dz = entity.getZ() - stand.getZ();
                if (dx * dx + dz * dz <= matchRadiusSq) {
                    GLOWING_ENTITIES.add(entity);
                    break;
                }
            }
        }
    }

    /**
     * A Player-typed entity is only a REAL player if its UUID is actually
     * listed in the tab list. Hypixel's player-model dungeon mobs are
     * Player entities client-side but never appear in the tab list, so
     * this correctly lets them through to be glowed.
     */
    private static boolean isRealPlayer(Entity entity, Minecraft client) {
        if (!(entity instanceof Player)) return false;
        if (entity == client.player) return true;
        if (client.getConnection() == null) return false;

        for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
            // VERIFY: GameProfile#id() - TrapperSolver only used .name(),
            // this is the first place .id() is needed, confirm it resolves.
            if (info.getProfile().id().equals(entity.getUUID())) {
                return true;
            }
        }
        return false;
    }

    // Same tab-list-scanning approach as TrapperSolver.isInFarmingIslands.
    private static boolean isInCatacombs(Minecraft client) {
        if (client.getConnection() == null) return false;

        for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
            Component displayName = info.getTabListDisplayName();
            String text = displayName != null
                    ? displayName.getString()
                    : info.getProfile().name();

            if (stripFormatting(text).contains(REQUIRED_DUNGEON_TEXT)) {
                return true;
            }
        }
        return false;
    }

    private static String stripFormatting(String text) {
        return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    public static boolean shouldGlow(Entity entity) {
        return enabled && GLOWING_ENTITIES.contains(entity);
    }
}