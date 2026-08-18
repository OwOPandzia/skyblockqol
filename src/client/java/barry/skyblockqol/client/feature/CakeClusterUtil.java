package barry.skyblockqol.client.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Single source of truth for "what counts as one cake" so FastCakeFeature
 * and any debug/inspection tooling can never disagree with each other.
 *
 * Clustering is a simple greedy nearest-neighbor grouping: pick an
 * unclaimed stand as a seed, pull in every unclaimed stand within
 * CLUSTER_RADIUS of it, repeat. This is a CHAIN-LINKAGE approach - if two
 * physically separate cakes have any pair of stands within CLUSTER_RADIUS
 * of each other, they will be merged into a single cluster. If the real
 * gap between cakes in the grid is smaller than CLUSTER_RADIUS, this is
 * the most likely cause of "clicks fewer cakes than exist" - a merged
 * cluster counts as one, silently absorbing its neighbor.
 */
public class CakeClusterUtil {

    // The dump showed ~0.7 blocks X spread, ~0.5 Z, ~1.6 Y for one cake's
    // 11 stands, and CakeDebugFeature showed the real gap between separate
    // cakes can be as tight as ~2 blocks center-to-center. This sits below
    // that gap so distinct cakes don't chain-link merge. Adjustable at
    // runtime via CakeDebugFeature's "Cluster Radius" setting so it can be
    // tuned without a rebuild if the real spacing turns out different.
    public static volatile double clusterRadius = 1.9;

    // How much further than the actual interaction/click range to scan for
    // clustering purposes. Without this, a cluster sitting near the edge of
    // a tight search box can have some of its stands fall in/out of the box
    // as the player moves half a block, making the SAME physical cake
    // sometimes have its head stand included and sometimes not - which
    // flips its identity key (see CakeCluster) and causes it to look
    // "un-clicked" again even though it was already hit. Scanning wider
    // than you actually click ensures clusters near your reach's edge are
    // always captured whole before eligibility is decided.
    public static final double SCAN_MARGIN = 5.0;

    private CakeClusterUtil() {}

    public static List<ArmorStand> findCandidates(Minecraft client, double range) {
        AABB searchBox = client.player.getBoundingBox().inflate(range);

        List<ArmorStand> candidates = new ArrayList<>();
        for (ArmorStand stand : client.level.getEntitiesOfClass(ArmorStand.class, searchBox)) {
            if (stand.isInvisible() && stand.isSmall()) {
                candidates.add(stand);
            }
        }
        return candidates;
    }

    public static List<CakeCluster> cluster(List<ArmorStand> candidates, double radius) {
        List<CakeCluster> clusters = new ArrayList<>();
        Set<ArmorStand> claimed = new HashSet<>();

        for (ArmorStand seed : candidates) {
            if (claimed.contains(seed)) continue;

            List<ArmorStand> members = new ArrayList<>();
            members.add(seed);
            claimed.add(seed);

            for (ArmorStand other : candidates) {
                if (claimed.contains(other)) continue;
                if (seed.distanceTo(other) <= radius) {
                    members.add(other);
                    claimed.add(other);
                }
            }

            clusters.add(new CakeCluster(members));
        }

        return clusters;
    }

    public static List<CakeCluster> findClusters(Minecraft client, double range, double radius) {
        return cluster(findCandidates(client, range), radius);
    }

    /**
     * Scans a wider area than {@code interactionRange} (by SCAN_MARGIN) so
     * clusters near the edge of your reach are always fully formed, then
     * returns only the clusters whose center actually falls within
     * interactionRange. Use this instead of findClusters() for anything
     * that decides whether a cake is currently clickable.
     */
    public static List<CakeCluster> findReachableClusters(Minecraft client, double interactionRange, double radius) {
        List<ArmorStand> candidates = findCandidates(client, interactionRange + SCAN_MARGIN);
        List<CakeCluster> allClusters = cluster(candidates, radius);

        double px = client.player.getX(), py = client.player.getY(), pz = client.player.getZ();

        List<CakeCluster> reachable = new ArrayList<>();
        for (CakeCluster c : allClusters) {
            if (c.distanceToCenter(px, py, pz) <= interactionRange) {
                reachable.add(c);
            }
        }
        return reachable;
    }
}