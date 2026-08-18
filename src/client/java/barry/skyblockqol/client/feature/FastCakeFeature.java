package barry.skyblockqol.client.feature;

import barry.skyblockqol.SkyblockQOL;
import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents; // VERIFY: networking v1 path in this snapshot
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting; // VERIFY: still at this path in current mapping
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand; // VERIFY: still named this in current mapping
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FastCake - press the bound key once to start clicking every un-clicked
 * cake in range, one every CLICK_INTERVAL_TICKS, until none are left.
 * Press the key again while running to stop early.
 *
 * Big Yum effect tracking has two known issues being worked through:
 *
 * 1. RACE CONDITION (fixed here): clicking finishes client-side in ~2
 *    seconds for 20 cakes, but the server's "Big Yum" chat confirmations
 *    trickle back more slowly. Evaluating "missing effects" the instant
 *    local clicking finishes catches the run mid-flight. Fixed by waiting
 *    REPORT_DELAY_TICKS after the last click before reporting.
 *
 * 2. TRACKING NOT REGISTERING (unresolved, being diagnosed): even accounting
 *    for #1, effects that had already printed to chat before the report
 *    fired still showed as "missing" - meaning sessionSeenEffects wasn't
 *    populated even though ClientReceiveMessageEvents.GAME is the same
 *    event TrapperSolver already uses successfully for similar Hypixel
 *    broadcast-style messages. Rather than guess at another regex/event
 *    change, this now logs (to the log file/console only, NOT chat) the
 *    raw and stripped text of every message containing "yum"
 *    (case-insensitive) so we can see exactly what's arriving and why the
 *    regex isn't matching it, next time this runs. Check the log after a
 *    run for lines starting with "[FastCake DEBUG]".
 */
public class FastCakeFeature {

    public static volatile boolean enabled = true;
    public static volatile int cakeKeyCode = GLFW.GLFW_KEY_UNKNOWN;

    // Adjustable in the feature menu. Capped at 4 blocks - vanilla/Hypixel
    // entity-interact reach checks are typically ~5-6 blocks, so 4 stays
    // safely inside that margin.
    public static volatile double detectRange = 3.0;
    private static final double MIN_RANGE = 2.0;
    private static final double MAX_RANGE = 4.0;

    // Delay between each automated click once a run starts.
    private static final long CLICK_INTERVAL_TICKS = 2; // 0.1s at 20 TPS

    // Grace period after the last click before checking which effects
    // showed up - gives the server's chat confirmations time to arrive.
    private static final long REPORT_DELAY_TICKS = 40; // 2s at 20 TPS

    private static boolean cakeKeyWasDown = false;

    private static volatile boolean running = false;
    private static long tickCounter = 0;
    private static long nextClickTick = -1;

    private static boolean reportPending = false;
    private static long reportAtTick = -1;

    // UUIDs (as strings) of cakes already clicked this session.
    private static final Set<String> clickedCakes = new HashSet<>();

    // "Big Yum! You refresh +N <Effect Name> for 48 hours!" - captures the
    // effect name, stripped of the leading amount.
    private static final Pattern BIG_YUM_PATTERN =
            Pattern.compile("Big Yum! You refresh \\+\\d+ (.+) for 48 hours!");

    // Every distinct effect name ever seen, across all runs - persisted.
    // Not a hardcoded "complete" list - grows automatically the first time
    // a new effect line is seen.
    private static final Set<String> knownEffects =
            new TreeSet<>(List.of(
                    "Treasure Chance", "Foraging Fortune", "Speed", "Health", "Strength",
                    "Defense", "Sea Creature Chance", "Intelligence", "Ferocity", "Pet Luck",
                    "Magic Find", "Mining Fortune", "Rift Time", "Vitality", "Tracking",
                    "Sweep", "True Defense", "Hunting Fortune", "Farming Fortune", "Cold Resistance"
            ));

    // Effect names seen during the current run only - reset each time a run starts.
    private static final Set<String> sessionSeenEffects = new HashSet<>();

    private static final Gson GSON = new Gson();
    private static final Path SAVE_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("skyblockqol-fastcake.json");

    static {
        load();
    }

    public static void register() {
        FeatureRegistry.register(new Feature(
                "Utility",
                "FastCake",
                "Press the bound key to start right-clicking every un-clicked cake in range, one at a time. Press again to stop early. Reports any known Big Yum effects that didn't show up. Clicked list resets on disconnect or via the setting below.",
                () -> enabled,
                v -> { enabled = v; if (!enabled) { running = false; reportPending = false; } save(); }
        ).withRangeSetting(new RangeSetting(
                "Range",
                "Middle-click, type a number, Enter to confirm. Blocks around you to look for cakes. Capped at 4 to stay within normal interact reach.",
                MIN_RANGE, MAX_RANGE,
                () -> detectRange,
                v -> { detectRange = v; save(); }
        )).withKeybindSetting(new KeybindSetting(
                "Click Key",
                "Click, then press a key to bind it. Pressing that key starts clicking through every un-clicked cake; press again to stop.",
                () -> cakeKeyCode,
                code -> { cakeKeyCode = code; save(); }
        )).withSetting(new FeatureSetting(
                "Reset Clicked Cakes",
                "Click to clear the list of cakes already clicked this session.",
                () -> false,
                v -> resetClickedCakes()
        )));

        ClientTickEvents.END_CLIENT_TICK.register(FastCakeFeature::tick);

        // VERIFY: DISCONNECT event shape in this fabric-api version/snapshot.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            resetClickedCakes();
            running = false;
            reportPending = false;
        });

        // Plain (non-cancelable) GAME listener - the same event TrapperSolver
        // already uses successfully for this style of Hypixel chat line.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;

            String raw = message.getString();
            String text = stripFormatting(raw);

            // Diagnostic only - console/log file, never chat. Fires on ANY
            // message that loosely mentions "yum" so we can see the exact
            // text even if BIG_YUM_PATTERN below fails to match it.
            if (text.toLowerCase(Locale.ROOT).contains("yum")) {
                SkyblockQOL.LOGGER.info("[FastCake DEBUG] raw='{}' stripped='{}'", raw, text);
            }

            Matcher matcher = BIG_YUM_PATTERN.matcher(text);
            if (!matcher.find()) return;

            String effect = matcher.group(1).trim();
            sessionSeenEffects.add(effect);
            if (knownEffects.add(effect)) {
                save(); // persist newly-discovered effect immediately
            }
        });
    }

    private static String stripFormatting(String text) {
        return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    private static void tick(Minecraft client) {
        tickCounter++;

        if (reportPending && tickCounter >= reportAtTick) {
            reportPending = false;
            reportMissingEffects(client);
        }

        if (!enabled || cakeKeyCode == GLFW.GLFW_KEY_UNKNOWN
                || client.gui.screen() instanceof barry.skyblockqol.client.gui.FeatureMenuScreen) {
            cakeKeyWasDown = false;
            return;
        }
        if (client.player == null || client.level == null) {
            running = false;
            return;
        }

        boolean down = InputConstants.isKeyDown(client.getWindow(), cakeKeyCode);

        if (down && !cakeKeyWasDown) {
            if (running) {
                running = false;
            } else {
                running = true;
                reportPending = false; // cancel any pending report from a previous run
                sessionSeenEffects.clear();
                nextClickTick = tickCounter; // click immediately on start
            }
        }
        cakeKeyWasDown = down;

        if (running && tickCounter >= nextClickTick) {
            boolean clicked = clickNextCake(client);
            if (!clicked) {
                running = false;
                reportPending = true;
                reportAtTick = tickCounter + REPORT_DELAY_TICKS;
            } else {
                nextClickTick = tickCounter + CLICK_INTERVAL_TICKS;
            }
        }
    }

    /** @return true if a cake was clicked, false if none were left to click. */
    private static boolean clickNextCake(Minecraft client) {
        // Uses findReachableClusters (not findClusters) so clusters near the
        // edge of detectRange are still formed from their full stand set -
        // see CakeClusterUtil.SCAN_MARGIN javadoc for why that matters.
        List<CakeCluster> clusters = CakeClusterUtil.findReachableClusters(
                client, detectRange, CakeClusterUtil.clusterRadius);

        clusters.sort(Comparator
                .comparingDouble((CakeCluster c) -> c.centerX)
                .thenComparingDouble(c -> c.centerZ));

        CakeCluster target = null;
        for (CakeCluster cluster : clusters) {
            if (!clickedCakes.contains(cluster.key)) {
                target = cluster;
                break;
            }
        }

        if (target == null) return false;

        ArmorStand clickEntity = target.headStand != null
                ? target.headStand
                : target.nearestTo(client.player.getX(), client.player.getY(), client.player.getZ());
        if (clickEntity == null) return false;

        final ArmorStand finalTarget = clickEntity;
        final String key = target.key;

        client.execute(() -> {
            EntityHitResult hitResult = new EntityHitResult(finalTarget, finalTarget.position());
            // VERIFY: 4-arg interact signature confirmed by compiler error:
            // interact(Player, Entity, EntityHitResult, InteractionHand)
            client.gameMode.interact(client.player, finalTarget, hitResult, InteractionHand.MAIN_HAND);
        });

        clickedCakes.add(key);
        return true;
    }

    private static void reportMissingEffects(Minecraft client) {
        if (knownEffects.isEmpty()) return;

        Set<String> missing = new TreeSet<>(knownEffects);
        missing.removeAll(sessionSeenEffects);

        if (missing.isEmpty()) {
            notifyPlayer(client, "All " + knownEffects.size() + " known Big Yum effects were received.",
                    ChatFormatting.GREEN);
        } else {
            notifyPlayer(client, "Missing effects this run (" + missing.size() + "): "
                    + String.join(", ", missing), ChatFormatting.RED);
        }
    }

    private static void notifyPlayer(Minecraft client, String message, ChatFormatting color) {
        if (client.player == null) return;

        MutableComponent component = Component.literal("[SkyblockQOL] ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(message).withStyle(color));

        client.player.sendSystemMessage(component);
    }

    public static void resetClickedCakes() {
        clickedCakes.clear();
    }

    private static void load() {
        try {
            if (!Files.exists(SAVE_PATH)) return;
            SaveData data = GSON.fromJson(Files.readString(SAVE_PATH, StandardCharsets.UTF_8), SaveData.class);
            if (data != null) {
                enabled = data.enabled;
                cakeKeyCode = data.cakeKeyCode;
                if (data.detectRange > 0) detectRange = data.detectRange;
                if (data.knownEffects != null) knownEffects.addAll(data.knownEffects);
            }
        } catch (IOException | RuntimeException e) {
            SkyblockQOL.LOGGER.warn("Could not load FastCake config", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(SAVE_PATH.getParent());
            SaveData data = new SaveData();
            data.enabled = enabled;
            data.cakeKeyCode = cakeKeyCode;
            data.detectRange = detectRange;
            data.knownEffects = new ArrayList<>(knownEffects);
            Files.writeString(SAVE_PATH, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SkyblockQOL.LOGGER.warn("Could not save FastCake config", e);
        }
    }

    private static class SaveData {
        boolean enabled;
        int cakeKeyCode;
        double detectRange;
        List<String> knownEffects;
    }
}