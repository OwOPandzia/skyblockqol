package barry.skyblockqol.client.finder;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class WaypointManager {

    private static final List<Waypoint> WAYPOINTS = new CopyOnWriteArrayList<>();

    private WaypointManager() {}

    public static void add(Waypoint waypoint) {
        WAYPOINTS.add(waypoint);
    }

    public static void remove(Waypoint waypoint) {
        WAYPOINTS.remove(waypoint);
    }

    public static void clear() {
        WAYPOINTS.clear();
    }

    public static List<Waypoint> getAll() {
        return List.copyOf(WAYPOINTS);
    }
}