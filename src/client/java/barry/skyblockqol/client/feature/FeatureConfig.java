package barry.skyblockqol.client.feature;

import barry.skyblockqol.SkyblockQOL;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Persists every Feature/FeatureSetting's enabled state to disk, keyed by name. */
public class FeatureConfig {

    private static final Gson GSON = new Gson();
    private static final Path SAVE_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("skyblockqol-features.json");
    private static final Type MAP_TYPE = new TypeToken<HashMap<String, Boolean>>() {}.getType();

    private static final Map<String, Boolean> VALUES = new HashMap<>();

    private FeatureConfig() {}

    static {
        load();
    }

    public static boolean has(String key) {
        return VALUES.containsKey(key);
    }

    public static boolean get(String key, boolean defaultValue) {
        return VALUES.getOrDefault(key, defaultValue);
    }

    public static void set(String key, boolean value) {
        VALUES.put(key, value);
        save();
    }

    private static void load() {
        try {
            if (!Files.exists(SAVE_PATH)) return;
            Map<String, Boolean> loaded = GSON.fromJson(Files.readString(SAVE_PATH, StandardCharsets.UTF_8), MAP_TYPE);
            if (loaded != null) VALUES.putAll(loaded);
        } catch (IOException | RuntimeException e) {
            SkyblockQOL.LOGGER.warn("Could not load feature config", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(SAVE_PATH.getParent());
            Files.writeString(SAVE_PATH, GSON.toJson(VALUES), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SkyblockQOL.LOGGER.warn("Could not save feature config", e);
        }
    }
}