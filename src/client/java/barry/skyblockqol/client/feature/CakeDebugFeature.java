package barry.skyblockqol.client.feature;

import barry.skyblockqol.SkyblockQOL;
import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Debug tool for FastCake. Scans a (large, adjustable) range around the
 * player, clusters armor stands with the exact same CakeClusterUtil logic
 * FastCakeFeature uses, and dumps everything to your clipboard: cluster
 * count, member count per cluster, whether a player_head stand was found,
 * center position, and distance to the nearest other cluster.
 *
 * What to look for in the dump if FastCake is skipping cakes:
 *  - A cluster with member count roughly 2x the norm (e.g. ~22 instead of
 *    ~11) almost always means two physically separate cakes got merged by
 *    chain-linkage - CLUSTER_RADIUS in CakeClusterUtil is too large for how
 *    close together the cakes actually are.
 *  - A "nearest other cluster" distance that's smaller than
 *    CLUSTER_RADIUS confirms two clusters are close enough to risk merging.
 *  - Fewer clusters than the visible cake count in-game is the direct
 *    symptom of the above.
 *  - A cluster with no head stand found means the head-targeting logic in
 *    FastCakeFeature will fall back to nearest-stand, which is worth
 *    knowing about if clicks aren't landing on that particular cake.
 */
public class CakeDebugFeature {

    public static volatile boolean enabled = true;
    public static volatile double rangeBlocks = 32.0;
    public static volatile int debugKeyCode = GLFW.GLFW_KEY_UNKNOWN;

    private static final double MIN_RANGE = 4.0;
    private static final double MAX_RANGE = 128.0;

    private static final double MIN_CLUSTER_RADIUS = 1.0;
    private static final double MAX_CLUSTER_RADIUS = 4.0;

    private static boolean debugKeyWasDown = false;

    private static final Gson GSON = new Gson();
    private static final Path SAVE_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("skyblockqol-cakedebug.json");

    static {
        load();
    }

    public static void register() {
        FeatureRegistry.register(new Feature(
                "Dev",
                "Cake Debug",
                "Dumps all detected cake clusters in range to your clipboard, for diagnosing FastCake skipping or double-counting cakes.",
                () -> enabled,
                v -> { enabled = v; save(); }
        ).withRangeSetting(new RangeSetting(
                "Scan Range",
                "Middle-click, type a number, Enter to confirm. Blocks around you to scan for cake clusters.",
                MIN_RANGE, MAX_RANGE,
                () -> rangeBlocks,
                v -> { rangeBlocks = v; save(); }
        )).withRangeSetting(new RangeSetting(
                "Cluster Radius",
                "Middle-click, type a number, Enter to confirm. Max distance between armor stands to count as the same cake. Shared with FastCake - tune this until Cluster Radius sits between the within-cake spread and the real gap between separate cakes.",
                MIN_CLUSTER_RADIUS, MAX_CLUSTER_RADIUS,
                () -> CakeClusterUtil.clusterRadius,
                v -> { CakeClusterUtil.clusterRadius = v; save(); }
        )).withKeybindSetting(new KeybindSetting(
                "Dump Key",
                "Click, then press a key to bind it. Pressing that key dumps cake cluster info to your clipboard.",
                () -> debugKeyCode,
                code -> { debugKeyCode = code; save(); }
        )));

        ClientTickEvents.END_CLIENT_TICK.register(CakeDebugFeature::tick);
    }

    private static void tick(Minecraft client) {
        if (!enabled || debugKeyCode == GLFW.GLFW_KEY_UNKNOWN
                || client.gui.screen() != null) {
            debugKeyWasDown = false;
            return;
        }
        if (client.player == null || client.level == null) return;

        boolean down = InputConstants.isKeyDown(client.getWindow(), debugKeyCode);

        if (down && !debugKeyWasDown) {
            dumpClusters(client);
        }
        debugKeyWasDown = down;
    }

    private static void dumpClusters(Minecraft client) {
        List<ArmorStand> candidates = CakeClusterUtil.findCandidates(client, rangeBlocks);
        List<CakeCluster> clusters = CakeClusterUtil.cluster(candidates, CakeClusterUtil.clusterRadius);

        clusters.sort(Comparator
                .comparingDouble((CakeCluster c) -> c.centerX)
                .thenComparingDouble(c -> c.centerZ));

        double px = client.player.getX(), py = client.player.getY(), pz = client.player.getZ();

        StringBuilder sb = new StringBuilder();
        sb.append("SkyblockQOL Cake Debug\n")
                .append("Range: ").append((int) rangeBlocks).append(" blocks, ClusterRadius: ")
                .append(CakeClusterUtil.clusterRadius).append("\n")
                .append("Candidate stands: ").append(candidates.size())
                .append(", Clusters formed: ").append(clusters.size()).append("\n\n");

        int totalMembers = 0;
        int noHeadCount = 0;

        for (int i = 0; i < clusters.size(); i++) {
            CakeCluster cluster = clusters.get(i);
            totalMembers += cluster.members.size();
            if (cluster.headStand == null) noHeadCount++;

            double nearestOtherDist = Double.MAX_VALUE;
            for (int j = 0; j < clusters.size(); j++) {
                if (i == j) continue;
                double d = cluster.distanceToCenter(
                        clusters.get(j).centerX, clusters.get(j).centerY, clusters.get(j).centerZ);
                if (d < nearestOtherDist) nearestOtherDist = d;
            }
            // NOTE: compares against the live-tunable clusterRadius, not a
            // fixed constant, so this flag stays accurate as you adjust it.

            double distFromPlayer = cluster.distanceToCenter(px, py, pz);

            sb.append("Cluster #").append(i + 1)
                    .append(" | members=").append(cluster.members.size())
                    .append(" | headStand=").append(cluster.headStand != null ? "YES" : "NO")
                    .append(" | center=(").append(String.format("%.2f, %.2f, %.2f",
                            cluster.centerX, cluster.centerY, cluster.centerZ)).append(")")
                    .append(" | distFromPlayer=").append(String.format("%.2f", distFromPlayer))
                    .append(" | nearestOtherCluster=").append(
                            nearestOtherDist == Double.MAX_VALUE ? "n/a" : String.format("%.2f", nearestOtherDist));

            if (nearestOtherDist != Double.MAX_VALUE && nearestOtherDist <= CakeClusterUtil.clusterRadius) {
                sb.append("  <-- WITHIN CLUSTER_RADIUS, merge risk");
            }
            sb.append("\n");
        }

        double avgMembers = clusters.isEmpty() ? 0 : (double) totalMembers / clusters.size();
        sb.append("\nAvg members/cluster: ").append(String.format("%.1f", avgMembers))
                .append(" | Clusters with no head stand: ").append(noHeadCount);

        client.keyboardHandler.setClipboard(sb.toString());

        notifyPlayer(client, "Found " + clusters.size() + " cake cluster(s) from "
                + candidates.size() + " stands. Avg " + String.format("%.1f", avgMembers)
                + " members/cluster. Full dump copied to clipboard.");
    }

    private static void notifyPlayer(Minecraft client, String message) {
        if (client.player == null) return;
        client.player.sendSystemMessage(Component.literal("[SkyblockQOL] " + message));
    }

    private static void load() {
        try {
            if (!Files.exists(SAVE_PATH)) return;
            SaveData data = GSON.fromJson(Files.readString(SAVE_PATH, StandardCharsets.UTF_8), SaveData.class);
            if (data != null) {
                enabled = data.enabled;
                rangeBlocks = data.rangeBlocks;
                debugKeyCode = data.debugKeyCode;
                if (data.clusterRadius > 0) CakeClusterUtil.clusterRadius = data.clusterRadius;
            }
        } catch (IOException | RuntimeException e) {
            SkyblockQOL.LOGGER.warn("Could not load Cake Debug config", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(SAVE_PATH.getParent());
            SaveData data = new SaveData();
            data.enabled = enabled;
            data.rangeBlocks = rangeBlocks;
            data.debugKeyCode = debugKeyCode;
            data.clusterRadius = CakeClusterUtil.clusterRadius;
            Files.writeString(SAVE_PATH, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SkyblockQOL.LOGGER.warn("Could not save Cake Debug config", e);
        }
    }

    private static class SaveData {
        boolean enabled;
        double rangeBlocks;
        int debugKeyCode;
        double clusterRadius;
    }
}