package barry.skyblockqol.client.feature;

import barry.skyblockqol.SkyblockQOL;
import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PeltTracker {

    private static final long PAUSE_AFTER_MS = 30_000;
    private static final Gson GSON = new Gson();
    private static final Path SAVE_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("skyblockqol-pelts.json");

    // Session - in-memory only, naturally resets every game launch.
    private static int sessionPelts = 0;
    private static long sessionActiveMillis = 0;

    // All-time - loaded from disk on class init, saved on every pelt and periodically.
    private static int allTimePelts = 0;
    private static long allTimeActiveMillis = 0;

    private static long lastPeltMillis = System.currentTimeMillis();
    private static long lastTickMillis = System.currentTimeMillis();
    private static long lastSaveMillis = System.currentTimeMillis();

    static {
        load();
    }

    private PeltTracker() {}

    public static void addPelts(int amount) {
        if (amount <= 0) return;
        sessionPelts += amount;
        allTimePelts += amount;
        lastPeltMillis = System.currentTimeMillis();
        save();
    }

    /** Call once per client tick. */
    public static void tick() {
        long now = System.currentTimeMillis();
        long delta = now - lastTickMillis;
        lastTickMillis = now;

        boolean withinPeltWindow = lastPeltMillis > 0 && now - lastPeltMillis <= PAUSE_AFTER_MS;
        boolean active = TrapperSolver.isActive(Minecraft.getInstance()) && withinPeltWindow;

        if (active) {
            sessionActiveMillis += delta;
            allTimeActiveMillis += delta;
        }

        if (now - lastSaveMillis > 30_000) {
            save();
            lastSaveMillis = now;
        }
    }

    public static boolean isPaused() {
        if (lastPeltMillis <= 0) return true;
        return !TrapperSolver.isActive(Minecraft.getInstance())
                || System.currentTimeMillis() - lastPeltMillis > PAUSE_AFTER_MS;
    }

    public static int getSessionPelts() { return sessionPelts; }
    public static long getSessionActiveMillis() { return sessionActiveMillis; }
    public static double getSessionPeltsPerHour() { return rate(sessionPelts, sessionActiveMillis); }

    public static int getAllTimePelts() { return allTimePelts; }
    public static long getAllTimeActiveMillis() { return allTimeActiveMillis; }
    public static double getAllTimePeltsPerHour() { return rate(allTimePelts, allTimeActiveMillis); }

    private static double rate(int pelts, long activeMillis) {
        if (activeMillis <= 0) return 0.0;
        return pelts / (activeMillis / 3_600_000.0);
    }

    public static String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
        return (totalSeconds / 3600) + "h " + ((totalSeconds % 3600) / 60) + "m";
    }

    private static void load() {
        try {
            if (!Files.exists(SAVE_PATH)) return;
            SaveData data = GSON.fromJson(Files.readString(SAVE_PATH, StandardCharsets.UTF_8), SaveData.class);
            if (data != null) {
                allTimePelts = data.totalPelts;
                allTimeActiveMillis = data.activeMillis;
            }
        } catch (IOException | RuntimeException e) {
            SkyblockQOL.LOGGER.warn("Could not load pelts save data", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(SAVE_PATH.getParent());
            SaveData data = new SaveData();
            data.totalPelts = allTimePelts;
            data.activeMillis = allTimeActiveMillis;
            Files.writeString(SAVE_PATH, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SkyblockQOL.LOGGER.warn("Could not save pelts data", e);
        }
    }

    private static class SaveData {
        int totalPelts;
        long activeMillis;
    }
}