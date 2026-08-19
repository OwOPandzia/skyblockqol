package barry.skyblockqol.client.feature;

import barry.skyblockqol.SkyblockQOL;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Glows dungeon key item stands (Wither Key, Blood Key, ...) and shows a
 * distance + direction indicator on the HUD. Subfeature of Star Mob Glow,
 * same nesting pattern as PeltsHudFeature attaching itself to Trapper
 * Solver.
 *
 * DETECTION: confirmed via EntityNbtDumper that a dungeon key is actually
 * TWO armor stands at (almost) the same X/Z: one carries only the
 * CustomName ("Wither Key", no equipment), the other carries the visual
 * player_head skull item and no name. Same split-stand pattern FastCake's
 * cake clusters and the nearby Superboom TNT pair use. We match the two
 * by X/Z proximity, same idea as CakeCluster.findHeadStand.
 *
 * WAYPOINT: implemented as a HUD compass arrow + distance readout rather
 * than a true in-world 3D beam. A beam would need camera/projection
 * matrix APIs (WorldRenderEvents + verified matrix/buffer calls) that
 * haven't been touched anywhere else in this mod yet, and given how many
 * renamed APIs this snapshot already has, guessing at that felt too risky
 * to bundle in blind. The glow itself is visible through walls (same as
 * vanilla Glowing), so combined with the HUD arrow this should get you
 * most of the way to a key without needing to see it directly. Happy to
 * build the full 3D beam next once the camera/projection method names are
 * confirmed via IntelliJ.
 */
public class KeyLocatorFeature {

    public static volatile boolean enabled = false;

    private static final double SEARCH_RANGE = 128.0;

    // VERIFY: "Blood Key" is guessed by name-pattern with "Wither Key" -
    // only "Wither Key" has been confirmed via an actual NBT dump so far.
    // Extend this set as more key types get confirmed in-game; the
    // matching/glow/HUD logic below is shared by all of them.
    private static final Set<String> KNOWN_KEY_NAMES = new HashSet<>(List.of(
            "Wither Key", "Blood Key"
    ));

    // Max horizontal (X/Z) distance between a key's nametag stand and its
    // separate head-equipped stand. The dumped "Wither Key" pair sat at
    // the exact same X/Z (-88.50, -74.50), only Y differed (68.55 vs
    // 68.32), so this has generous headroom.
    private static final double HEAD_MATCH_RADIUS = 0.3;

    // Chosen so it reads distinctly from Star Mob Glow's orange - same
    // color for every known key type per your ask ("same as Blood Key").
    // Easy to swap if you'd rather it match something specific in-game.
    private static final int KEY_GLOW_COLOR = 0xFFFFAA00;

    private static final Set<Entity> GLOWING_ENTITIES = new HashSet<>();
    private static volatile TrackedKey nearestKey = null;

    public static void register() {
        FeatureRegistry.find("Dungeons", "Star Mob Glow").ifPresent(feature ->
                feature.withSetting(new FeatureSetting(
                        "Key Locator",
                        "Glows dungeon key item stands (Wither Key, Blood Key) and shows distance/direction on the HUD.",
                        () -> enabled,
                        v -> enabled = v
                ))
        );

        ClientTickEvents.END_CLIENT_TICK.register(KeyLocatorFeature::tick);
        HudElementRegistry.addLast(SkyblockQOL.id("key_locator_hud"), KeyLocatorFeature::onHudRender);
    }

    private static void tick(Minecraft client) {
        GLOWING_ENTITIES.clear();
        nearestKey = null;

        if (!enabled) return;
        if (client.player == null || client.level == null) return;

        AABB searchBox = client.player.getBoundingBox().inflate(SEARCH_RANGE);

        List<ArmorStand> labelStands = new ArrayList<>();
        List<ArmorStand> headStands = new ArrayList<>();

        for (ArmorStand stand : client.level.getEntitiesOfClass(ArmorStand.class, searchBox)) {
            ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);
            if (!head.isEmpty() && head.getItem() == Items.PLAYER_HEAD) {
                headStands.add(stand);
            }

            Component name = stand.getCustomName();
            if (name != null && KNOWN_KEY_NAMES.contains(name.getString().trim())) {
                labelStands.add(stand);
            }
        }

        double px = client.player.getX(), py = client.player.getY(), pz = client.player.getZ();
        double matchRadiusSq = HEAD_MATCH_RADIUS * HEAD_MATCH_RADIUS;
        double nearestDistSq = Double.MAX_VALUE;

        for (ArmorStand label : labelStands) {
            String keyName = label.getCustomName().getString().trim();

            ArmorStand matchedHead = null;
            double bestDistSq = matchRadiusSq;
            for (ArmorStand head : headStands) {
                double dx = head.getX() - label.getX();
                double dz = head.getZ() - label.getZ();
                double distSq = dx * dx + dz * dz;
                if (distSq <= bestDistSq) {
                    bestDistSq = distSq;
                    matchedHead = head;
                }
            }

            // Fall back to the label stand itself so something still glows
            // even if no matching head stand is found.
            ArmorStand glowTarget = matchedHead != null ? matchedHead : label;
            GLOWING_ENTITIES.add(glowTarget);

            double dx = glowTarget.getX() - px, dy = glowTarget.getY() - py, dz = glowTarget.getZ() - pz;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearestKey = new TrackedKey(keyName, glowTarget.getX(), glowTarget.getZ(), Math.sqrt(distSq));
            }
        }
    }

    public static boolean shouldGlow(Entity entity) {
        return enabled && GLOWING_ENTITIES.contains(entity);
    }

    public static int getGlowColorArgb() {
        return KEY_GLOW_COLOR;
    }

    private static void onHudRender(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!enabled) return;
        TrackedKey key = nearestKey;
        if (key == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        String arrow = compassArrow(client, key);
        String text = arrow + " " + key.name() + " - " + String.format("%.0fm", key.distance());

        int x = client.getWindow().getGuiScaledWidth() / 2 - client.font.width(text) / 2;
        int y = 6;

        graphics.fill(x - 4, y - 2, x + client.font.width(text) + 4, y + 10, 0x90000000);
        graphics.text(client.font, text, x, y, KEY_GLOW_COLOR);
    }

    /**
     * Cheap 8-direction compass arrow relative to the player's current yaw
     * and the key's horizontal bearing - deliberately simple 2D math, no
     * camera/projection needed. See class javadoc for why a real in-world
     * beam waypoint was left out of this first pass.
     */
    private static String compassArrow(Minecraft client, TrackedKey key) {
        double dx = key.x() - client.player.getX();
        double dz = key.z() - client.player.getZ();

        double bearingToKey = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = bearingToKey - client.player.getYRot();
        relative = ((relative % 360) + 360) % 360;

        String[] arrows = {"\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196"};
        int index = (int) Math.round(relative / 45.0) % 8;
        return arrows[index];
    }

    private record TrackedKey(String name, double x, double z, double distance) {}
}