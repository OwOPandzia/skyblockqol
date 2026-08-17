package barry.skyblockqol.client.feature;

import barry.skyblockqol.client.feature.Feature;
import barry.skyblockqol.client.feature.FeatureRegistry;
import barry.skyblockqol.SkyblockQOL;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TrapperSolver {

    public static volatile boolean enabled = true;

    private static final Map<Entity, GlowColor> TARGET_ENTITIES = new HashMap<>();
    private static final Set<Integer> LOGGED_ENTITY_IDS = new HashSet<>();

    private static final Map<Double, GlowColor> HEALTH_COLORS = new LinkedHashMap<>();
    static {
        HEALTH_COLORS.put(100.0, GlowColor.WHITE);
        HEALTH_COLORS.put(200.0, GlowColor.WHITE);
        HEALTH_COLORS.put(500.0, GlowColor.GREEN);
        HEALTH_COLORS.put(1000.0, GlowColor.BLUE);
    }
    private static final double MATCH_TOLERANCE = 2.0;

    private static final double HIGH_TIER_CEILING = 1000.0;
    private static volatile GlowColor activeHighTierColor = GlowColor.NONE;

    private static final Map<String, GlowColor> CATEGORY_COLORS = new LinkedHashMap<>();
    static {
        CATEGORY_COLORS.put("Trackable", GlowColor.WHITE);
        CATEGORY_COLORS.put("Untrackable", GlowColor.GREEN);
        CATEGORY_COLORS.put("Undetected", GlowColor.BLUE);
        CATEGORY_COLORS.put("Endangered", GlowColor.PURPLE);
        CATEGORY_COLORS.put("Elusive", GlowColor.YELLOW);
    }

    // --- Auto-trapper timer state ---

    private static volatile boolean questActive = false;
    private static volatile boolean questCompleted = false;
    private static long questAcceptedTick = -1;
    private static long tickCounter = 0;
    private static final long CALL_DELAY_TICKS = 18 * 20; // 18 seconds

    // --- Retry-on-not-found state ---

    private static volatile boolean retryPending = false;
    private static long retryScheduledTick = -1;
    private static final long NOT_FOUND_RETRY_TICKS = 10; // 0.5 seconds

    // --- Location gate ---

    private static final String REQUIRED_AREA_TEXT = "Area: The Farming Islands";

    // --- Location-based auto-warp + category parsing ---

    private static final Pattern HUNT_CLUE_PATTERN =
            Pattern.compile("You can find your (\\w+) animal near the (.+)\\.");

    private static final Pattern PELT_REWARD_PATTERN =
            Pattern.compile("Killing the animal rewarded you (\\d+) pelts");

    public static void register() {
        FeatureRegistry.register(new Feature(
                "Trapper",
                "Trapper Solver",
                "Automates Trevor's hunt: glows the target animal by tier, auto-warps to the clue area, clicks the accept prompt and calls Trevor when ready.",
                () -> enabled,
                v -> enabled = v
        ));
        ClientTickEvents.END_CLIENT_TICK.register(TrapperSolver::tick);

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!enabled || overlay) return;
            if (!isInFarmingIslands(Minecraft.getInstance())) return;
            String text = stripFormatting(message.getString());
            if (text.contains("I couldn't locate any animals")) {
                //SkyblockQOL.LOGGER.info("Trevor found no animals, retrying /call trevor in 5s");
                retryPending = true;
                retryScheduledTick = tickCounter;
                return;
            }

            if (text.contains("Accept the trapper's task to hunt the animal?")) {
                //SkyblockQOL.LOGGER.info("Accept prompt detected, attempting to click YES");
                boolean clicked = clickYesOption(message);

                if (clicked) {
                    questActive = true;
                    questCompleted = false;
                    questAcceptedTick = tickCounter;
                    //SkyblockQOL.LOGGER.info("Quest accepted, 18s minimum delay timer started");
                }
                return;
            }

            Matcher clueMatcher = HUNT_CLUE_PATTERN.matcher(text);
            if (clueMatcher.find()) {
                String category = clueMatcher.group(1).trim();
                String area = clueMatcher.group(2).trim();

                //SkyblockQOL.LOGGER.info("Hunt clue parsed: category='{}' area='{}'", category, area);

                GlowColor color = matchCategoryColor(category);
                if (color != GlowColor.NONE) {
                    activeHighTierColor = color;
                    //SkyblockQOL.LOGGER.info("Active high-tier color set to {}", color);
                } /*else {
                    SkyblockQOL.LOGGER.warn("Unknown category '{}', high-tier color unchanged", category);
                }*/

                warpToArea(area);
                return;
            }

            Matcher peltMatcher = PELT_REWARD_PATTERN.matcher(text);
            if (peltMatcher.find()) {
                PeltTracker.addPelts(Integer.parseInt(peltMatcher.group(1)));
            }

            if (questActive && !questCompleted
                    && (text.contains("Killing the animal rewarded you")
                    || text.contains("Return to the Trapper soon"))) {

                questCompleted = true;

                long elapsed = tickCounter - questAcceptedTick;
                if (elapsed >= CALL_DELAY_TICKS) {
                    callTrevorNow(Minecraft.getInstance());
                }
            }
        });
    }

    private static String stripFormatting(String text) {
        return text.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    // Scans the tab list for a fake-player entry whose display text contains
    // "Area: The Farming Islands" - this is how Hypixel exposes the current
    // area, since vanilla tab has no other way to show arbitrary text.
    //
    // NOTE: getListedOnlinePlayers() / getTabListDisplayName() are a guess at
    // this version's Mojmap names for reading the tab list. If either fails
    // to resolve, log client.getConnection().getClass() and check genSources
    // for the real method names on the connection/PlayerInfo classes.
    private static boolean isInFarmingIslands(Minecraft client) {
        if (client == null || client.getConnection() == null) return false;

        for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
            Component displayName = info.getTabListDisplayName();
            String text = displayName != null
                    ? displayName.getString()
                    : info.getProfile().name();

            if (stripFormatting(text).contains(REQUIRED_AREA_TEXT)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isActive(Minecraft client) {
        return enabled && isInFarmingIslands(client);
    }

    private static GlowColor matchCategoryColor(String category) {
        for (Map.Entry<String, GlowColor> entry : CATEGORY_COLORS.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(category)) {
                return entry.getValue();
            }
        }
        return GlowColor.NONE;
    }

    private static void warpToArea(String area) {
        String warpCommand;

        if (area.equalsIgnoreCase("Desert Settlement") || area.equalsIgnoreCase("Oasis") || area.equalsIgnoreCase("Desert Mountain")) {
            warpCommand = "warp desert";
        } else if (area.equalsIgnoreCase("Overgrown Mushroom Cave")
                || area.equalsIgnoreCase("Glowing Mushroom Cave")) {
            warpCommand = "warp glowing";
        } else if (area.equalsIgnoreCase("Mushroom Gorge")) {
            warpCommand = "warp trap";
        } else {
            //SkyblockQOL.LOGGER.info("No warp mapping for area '{}', staying put", area);
            return;
        }

        //SkyblockQOL.LOGGER.info("Warping for area '{}': /{}", area, warpCommand);

        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.connection.sendCommand(warpCommand);
            }
        });
    }

    private static void tick(Minecraft client) {
        tickCounter++;

        if (!enabled) return;
        if (client.level == null || client.player == null) return;
        if (!isInFarmingIslands(client)) return;

        handleAutoCallTimer(client);
        handleNotFoundRetry(client);
        handleGlowDetection(client);
    }

    private static void handleAutoCallTimer(Minecraft client) {
        if (!questActive || !questCompleted) return;

        if (tickCounter - questAcceptedTick >= CALL_DELAY_TICKS) {
            callTrevorNow(client);
        }
    }

    private static void handleNotFoundRetry(Minecraft client) {
        if (!retryPending) return;

        if (tickCounter - retryScheduledTick >= NOT_FOUND_RETRY_TICKS) {
            //SkyblockQOL.LOGGER.info("Retrying /call trevor after no-animal-found message");

            client.execute(() -> {
                if (client.player != null) {
                    client.player.connection.sendCommand("call trevor");
                }
            });

            retryPending = false;
            retryScheduledTick = -1;
        }
    }

    private static void callTrevorNow(Minecraft client) {
        //SkyblockQOL.LOGGER.info("Executing /call trevor");

        client.execute(() -> {
            if (client.player != null) {
                client.player.connection.sendCommand("call trevor");
            }
        });

        questActive = false;
        questCompleted = false;
        questAcceptedTick = -1;
    }

    // --- Chat click simulation ---

    private static boolean clickYesOption(Component message) {
        ClickEvent event = findClickEventContaining(message, "YES");

        if (event == null) {
            //SkyblockQOL.LOGGER.warn("Could not find a [YES] click event in the accept prompt");
            return false;
        }

       // SkyblockQOL.LOGGER.info("Found YES click event: {}", event);
        return executeClickEvent(event);
    }

    private static ClickEvent findClickEventContaining(Component component, String needle) {
        ClickEvent ownClick = component.getStyle().getClickEvent();
        if (ownClick != null && component.getString().contains(needle)) {
            return ownClick;
        }

        for (Component sibling : component.getSiblings()) {
            ClickEvent found = findClickEventContaining(sibling, needle);
            if (found != null) return found;
        }

        return null;
    }

    private static boolean executeClickEvent(ClickEvent event) {
        Minecraft client = Minecraft.getInstance();

        if (event instanceof ClickEvent.RunCommand runCommand) {
            String command = runCommand.command();
            //SkyblockQOL.LOGGER.info("Executing command from click event: {}", command);

            client.execute(() -> {
                if (client.player != null) {
                    String stripped = command.startsWith("/") ? command.substring(1) : command;
                    client.player.connection.sendCommand(stripped);
                }
            });
            return true;
        } else {
            //SkyblockQOL.LOGGER.warn("Unhandled click event type: {}", event.getClass());
            return false;
        }
    }

    // --- Glow detection ---

    private static void handleGlowDetection(Minecraft client) {
        AABB searchBox = client.player.getBoundingBox().inflate(192.0);

        Set<Integer> currentIds = new HashSet<>();

        for (LivingEntity entity :
                client.level.getEntitiesOfClass(LivingEntity.class, searchBox)) {

            if (entity instanceof Player) continue;

            int id = entity.getId();
            currentIds.add(id);

            double maxHealth = entity.getMaxHealth();

            if (maxHealth >= 50.0 && LOGGED_ENTITY_IDS.add(id)) {
                //SkyblockQOL.LOGGER.info("Candidate {} maxHealth={}", entity.getType(), maxHealth);
            }

            GlowColor color;

            if (maxHealth > HIGH_TIER_CEILING) {
                color = activeHighTierColor;
            } else {
                color = matchColorByHealth(maxHealth);
            }

            if (color == GlowColor.NONE) continue;

            TARGET_ENTITIES.put(entity, color);
        }

        TARGET_ENTITIES.keySet().removeIf(entity ->
                entity.isRemoved() || client.level.getEntity(entity.getId()) == null
        );

        LOGGED_ENTITY_IDS.removeIf(id -> !currentIds.contains(id));
    }

    private static GlowColor matchColorByHealth(double maxHealth) {
        for (Map.Entry<Double, GlowColor> entry : HEALTH_COLORS.entrySet()) {
            if (Math.abs(entry.getKey() - maxHealth) <= MATCH_TOLERANCE) {
                return entry.getValue();
            }
        }
        return GlowColor.NONE;
    }

    public static GlowColor getGlowColor(Entity entity) {
        return TARGET_ENTITIES.getOrDefault(entity, GlowColor.NONE);
    }

    public enum GlowColor {
        NONE(0x00000000),
        WHITE(0xFFFFFFFF),
        GREEN(0xFF55FF55),
        BLUE(0xFF5555FF),
        PURPLE(0xFFAA55FF),
        YELLOW(0xFFFFFF55);

        private final int color;
        GlowColor(int color) { this.color = color; }
        public int getColor() { return color; }
    }
}