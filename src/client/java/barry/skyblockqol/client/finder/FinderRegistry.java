package barry.skyblockqol.client.finder;

import barry.skyblockqol.SkyblockQOL;
import barry.skyblockqol.client.feature.Feature;
import barry.skyblockqol.client.feature.FeatureRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class FinderRegistry {

    public static final String CATEGORY = "Finder";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("skyblockqol").resolve("finders.json");

    private static final List<FinderConfig> CONFIGS = new ArrayList<>();
    private static final Map<String, Feature> LIVE_FEATURES = new HashMap<>();

    private FinderRegistry() {}

    // --- Persistence ---

    public static void load() {
        CONFIGS.clear();
        if (!Files.exists(CONFIG_PATH)) return;

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            FinderConfig[] loaded = GSON.fromJson(reader, FinderConfig[].class);
            if (loaded != null) CONFIGS.addAll(Arrays.asList(loaded));
        } catch (IOException e) {
            SkyblockQOL.LOGGER.warn("Failed to load finder configs", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(CONFIGS, writer);
            }
        } catch (IOException e) {
            SkyblockQOL.LOGGER.warn("Failed to save finder configs", e);
        }
    }

    // --- CRUD ---

    public static List<FinderConfig> getAll() {
        return List.copyOf(CONFIGS);
    }

    public static void add(FinderConfig config) {
        CONFIGS.add(config);
        save();
        rebuildFeatures();
    }

    public static void update(FinderConfig config) {
        for (int i = 0; i < CONFIGS.size(); i++) {
            if (CONFIGS.get(i).id.equals(config.id)) {
                CONFIGS.set(i, config);
                break;
            }
        }
        save();
        rebuildFeatures();
    }

    public static void remove(FinderConfig config) {
        CONFIGS.removeIf(c -> c.id.equals(config.id));
        save();
        rebuildFeatures();
    }

    // --- Feature-row sync ---

    /** Rebuilds the "Finder" category in FeatureRegistry from CONFIGS. Call after any CRUD op. */
    public static void rebuildFeatures() {
        FeatureRegistry.unregisterCategory(CATEGORY);
        LIVE_FEATURES.clear();

        for (FinderConfig config : CONFIGS) {
            Feature feature = buildFeature(config);
            LIVE_FEATURES.put(config.id, feature);
            FeatureRegistry.register(feature);
        }
    }

    private static Feature buildFeature(FinderConfig config) {
        String description = describe(config);

        Feature feature = new Feature(
                CATEGORY,
                config.name,
                description,
                () -> config.continuous ? config.enabled : false,
                v -> onToggle(config, v)
        );
        feature.setOnConfigure(() -> {
            Minecraft client = Minecraft.getInstance();
            client.setScreen(new barry.skyblockqol.client.gui.FinderWizardScreen(config));
        });
        return feature;
    }

    private static void onToggle(FinderConfig config, boolean value) {
        if (config.continuous) {
            config.enabled = value;
            return;
        }

        // One-shot finders: clicking always triggers a search, the row never "stays on".
        runOnce(config);
    }

    private static String describe(FinderConfig config) {
        if (config.type == FinderType.BLOCK) {
            return (config.continuous ? "Continuous" : "Click to search") + " · Block: "
                    + config.blockId + (config.includeConnected ? " (connected)" : "");
        }
        return (config.continuous ? "Continuous glow" : "Click to search") + " · Entity: "
                + config.entityCriteriaType;
    }

    public static void runOnce(FinderConfig config) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        if (config.type == FinderType.BLOCK) {
            FinderExecutor.findBlock(config).ifPresentOrElse(
                    result -> {
                        WaypointManager.add(new Waypoint(config.name, result.primaryPos(), config.glowColorArgb));
                        client.player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal(
                                        "[" + config.name + "] Found at " + posString(result.primaryPos())
                                ), false);
                    },
                    () -> client.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("[" + config.name + "] Not found."), false)
            );
        } else {
            FinderExecutor.findEntity(config).ifPresentOrElse(
                    entity -> {
                        BlockPos pos = entity.blockPosition();
                        WaypointManager.add(new Waypoint(config.name, pos, config.glowColorArgb));
                        client.player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal(
                                        "[" + config.name + "] Found at " + posString(pos)
                                ), false);
                    },
                    () -> client.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("[" + config.name + "] Not found."), false)
            );
        }
    }

    private static String posString(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    // --- Continuous glow lookup, used by SkyblockQOLClient.getGlowColor ---

    public static int getGlowColorForEntity(LivingEntity entity) {
        for (FinderConfig config : CONFIGS) {
            if (config.type != FinderType.ENTITY || !config.continuous || !config.enabled) continue;
            if (FinderExecutor.findAllMatchingEntities(config).contains(entity)) {
                return config.glowColorArgb;
            }
        }
        return 0x00000000;
    }
}