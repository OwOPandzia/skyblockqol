package barry.skyblockqol.client.finder;

import net.minecraft.core.BlockPos;

public class Waypoint {
    public final String label;
    public final BlockPos pos;
    public final int colorArgb;
    public final long createdAtMillis;

    public Waypoint(String label, BlockPos pos, int colorArgb) {
        this.label = label;
        this.pos = pos;
        this.colorArgb = colorArgb;
        this.createdAtMillis = System.currentTimeMillis();
    }
}