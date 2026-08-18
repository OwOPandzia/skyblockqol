package barry.skyblockqol.client.feature;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * One physical cake: a group of stacked armor stands clustered by proximity.
 * Identity (key) comes from the UUID of the player_head "candle" stand
 * (falling back to the first member's UUID if none is present) - NOT an
 * averaged position, since averaged coordinates can drift slightly between
 * scans depending on entity iteration order and cause the same physical
 * cake to be treated as "new" on a later scan.
 */
public class CakeCluster {
    public final List<ArmorStand> members;
    public final double centerX, centerY, centerZ; // display/sort/debug only, not identity
    public final ArmorStand headStand; // nullable
    public final String key;

    public CakeCluster(List<ArmorStand> members) {
        this.members = members;

        double sumX = 0, sumY = 0, sumZ = 0;
        for (ArmorStand a : members) {
            sumX += a.getX();
            sumY += a.getY();
            sumZ += a.getZ();
        }
        this.centerX = sumX / members.size();
        this.centerY = sumY / members.size();
        this.centerZ = sumZ / members.size();

        this.headStand = findHeadStand(members);

        ArmorStand identityStand = headStand != null ? headStand : members.get(0);
        this.key = identityStand.getUUID().toString();
    }

    private static ArmorStand findHeadStand(List<ArmorStand> members) {
        for (ArmorStand a : members) {
            ItemStack head = a.getItemBySlot(EquipmentSlot.HEAD);
            if (!head.isEmpty() && head.getItem() == Items.PLAYER_HEAD) {
                return a;
            }
        }
        return null;
    }

    public ArmorStand nearestTo(double x, double y, double z) {
        ArmorStand best = null;
        double bestDist = Double.MAX_VALUE;
        for (ArmorStand a : members) {
            double d = a.distanceToSqr(x, y, z);
            if (d < bestDist) {
                bestDist = d;
                best = a;
            }
        }
        return best;
    }

    public double distanceToCenter(double x, double y, double z) {
        double dx = centerX - x, dy = centerY - y, dz = centerZ - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}