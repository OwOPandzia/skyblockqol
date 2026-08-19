package barry.skyblockqol.client.feature;

import barry.skyblockqol.SkyblockQOL;
import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class EntityNbtDumper {

    public static volatile boolean enabled = true;
    public static volatile double rangeBlocks = 16.0;
    public static volatile int dumpKeyCode = GLFW.GLFW_KEY_UNKNOWN;

    private static final double MIN_RANGE = 1.0;
    private static final double MAX_RANGE = 128.0;

    private static boolean dumpKeyWasDown = false;

    private static final DateTimeFormatter DISPLAY_STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Gson GSON = new Gson();
    private static final Path SAVE_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("skyblockqol-nbtdumper.json");

    static {
        load();
    }

    public static void register() {
        FeatureRegistry.register(new Feature(
                "Dev",
                "Entity NBT Dumper",
                "Dumps NBT for every non-player entity in range to your clipboard.",
                () -> enabled,
                v -> { enabled = v; save(); }
        ).withRangeSetting(new RangeSetting(
                "Range",
                "Middle-click, type a number, Enter to confirm. Blocks around you to scan.",
                MIN_RANGE, MAX_RANGE,
                () -> rangeBlocks,
                v -> { rangeBlocks = v; save(); }
        )).withKeybindSetting(new KeybindSetting(
                "Dump Key",
                "Click, then press a key to bind it. Pressing that key dumps nearby entity NBT to your clipboard.",
                () -> dumpKeyCode,
                code -> { dumpKeyCode = code; save(); }
        )));

        ClientTickEvents.END_CLIENT_TICK.register(EntityNbtDumper::tick);
    }

    private static void tick(Minecraft client) {
        if (!enabled || dumpKeyCode == GLFW.GLFW_KEY_UNKNOWN
                || client.gui.screen() != null) {
            dumpKeyWasDown = false;
            return;
        }

        boolean down = InputConstants.isKeyDown(client.getWindow(), dumpKeyCode);

        if (down && !dumpKeyWasDown) {
            dumpNearbyEntities(client);
        }
        dumpKeyWasDown = down;
    }

    private static void dumpNearbyEntities(Minecraft client) {
        if (client.player == null || client.level == null) return;

        AABB searchBox = client.player.getBoundingBox().inflate(rangeBlocks);

        List<Entity> entities = new ArrayList<>();
        for (Entity entity : client.level.getEntitiesOfClass(Entity.class, searchBox)) {
            if (entity instanceof Player) continue;
            entities.add(entity);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("SkyblockQOL Entity NBT Dump - ").append(LocalDateTime.now().format(DISPLAY_STAMP))
                .append(" - range ").append((int) rangeBlocks).append(" blocks")
                .append(" - ").append(entities.size()).append(" entities\n\n");

        for (Entity entity : entities) {
            CompoundTag tag = dumpEntityNbt(entity, client);

            sb.append("Entity: ").append(entity.getType())
                    .append(" (id ").append(entity.getId()).append(")\n");
            sb.append("Position: ").append(String.format("%.2f, %.2f, %.2f",
                    entity.getX(), entity.getY(), entity.getZ())).append("\n");
            sb.append(tag).append("\n\n");
        }

        client.keyboardHandler.setClipboard(sb.toString());
        notifyPlayer(client, "Copied NBT for " + entities.size() + " entities to clipboard.");
    }

    private static CompoundTag dumpEntityNbt(Entity entity, Minecraft client) {
        TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING,
                client.level.registryAccess()
        );
        entity.saveWithoutId(output);
        return output.buildResult();
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
                dumpKeyCode = data.dumpKeyCode;
            }
        } catch (IOException | RuntimeException e) {
            SkyblockQOL.LOGGER.warn("Could not load entity NBT dumper config", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(SAVE_PATH.getParent());
            SaveData data = new SaveData();
            data.enabled = enabled;
            data.rangeBlocks = rangeBlocks;
            data.dumpKeyCode = dumpKeyCode;
            Files.writeString(SAVE_PATH, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SkyblockQOL.LOGGER.warn("Could not save entity NBT dumper config", e);
        }
    }

    private static class SaveData {
        boolean enabled;
        double rangeBlocks;
        int dumpKeyCode;
    }
}