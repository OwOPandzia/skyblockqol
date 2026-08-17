package barry.skyblockqol.client.finder;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

/** Core scanning logic for both one-shot and continuous finders. */
public class FinderExecutor {

    private static final int BLOCK_SEARCH_RADIUS = 48;      // horizontal + vertical, from player
    private static final int MAX_CONNECTED_BLOCKS = 512;    // safety cap on flood fill
    private static final double ENTITY_SEARCH_RADIUS = 128.0;

    private FinderExecutor() {}

    // --- Block search ---

    public static Optional<BlockSearchResult> findBlock(FinderConfig config) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || config.blockId == null) {
            return Optional.empty();
        }

        ResourceLocation id = ResourceLocation.tryParse(config.blockId);
        if (id == null) return Optional.empty();
        Block target = BuiltInRegistries.BLOCK.get(id);
        if (target == null) return Optional.empty();

        Level level = client.level;
        BlockPos origin = client.player.blockPosition();

        BlockPos nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (int dx = -BLOCK_SEARCH_RADIUS; dx <= BLOCK_SEARCH_RADIUS; dx++) {
            for (int dy = -BLOCK_SEARCH_RADIUS; dy <= BLOCK_SEARCH_RADIUS; dy++) {
                for (int dz = -BLOCK_SEARCH_RADIUS; dz <= BLOCK_SEARCH_RADIUS; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!level.isLoaded(pos)) continue;

                    BlockState state = level.getBlockState(pos);
                    if (state.is(target)) {
                        double distSq = pos.distSqr(origin);
                        if (distSq < nearestDistSq) {
                            nearestDistSq = distSq;
                            nearest = pos.immutable();
                        }
                    }
                }
            }
        }

        if (nearest == null) return Optional.empty();

        List<BlockPos> connectedGroup = List.of(nearest);
        if (config.includeConnected) {
            connectedGroup = floodFillConnected(level, nearest, target);
        }

        return Optional.of(new BlockSearchResult(nearest, connectedGroup));
    }

    private static List<BlockPos> floodFillConnected(Level level, BlockPos start, Block target) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && result.size() < MAX_CONNECTED_BLOCKS) {
            BlockPos current = queue.poll();
            result.add(current);

            for (BlockPos neighbor : neighbors6(current)) {
                if (visited.contains(neighbor)) continue;
                if (!level.isLoaded(neighbor)) continue;
                if (level.getBlockState(neighbor).is(target)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return result;
    }

    private static List<BlockPos> neighbors6(BlockPos pos) {
        return List.of(
                pos.north(), pos.south(), pos.east(), pos.west(),
                pos.above(), pos.below()
        );
    }

    public record BlockSearchResult(BlockPos primaryPos, List<BlockPos> connected) {}

    // --- Entity search ---

    public static Optional<LivingEntity> findEntity(FinderConfig config) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return Optional.empty();

        Player player = client.player;
        AABB searchBox = player.getBoundingBox().inflate(ENTITY_SEARCH_RADIUS);

        LivingEntity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (LivingEntity entity : client.level.getEntitiesOfClass(LivingEntity.class, searchBox)) {
            if (entity instanceof Player) continue;
            if (!matches(entity, config)) continue;

            double distSq = entity.distanceToSqr(player);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = entity;
            }
        }

        return Optional.ofNullable(nearest);
    }

    /** Every currently-loaded entity matching this config's criteria - used for continuous glow. */
    public static List<LivingEntity> findAllMatchingEntities(FinderConfig config) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return List.of();

        AABB searchBox = client.player.getBoundingBox().inflate(ENTITY_SEARCH_RADIUS);
        List<LivingEntity> matches = new ArrayList<>();

        for (LivingEntity entity : client.level.getEntitiesOfClass(LivingEntity.class, searchBox)) {
            if (entity instanceof Player) continue;
            if (matches(entity, config)) matches.add(entity);
        }

        return matches;
    }

    private static boolean matches(LivingEntity entity, FinderConfig config) {
        return switch (config.entityCriteriaType) {
            case MOB_TYPE -> matchesMobType(entity, config.entityTypeId);
            case HP_AMOUNT -> Math.abs(entity.getMaxHealth() - config.hpAmount) <= config.hpTolerance;
            case NBT -> matchesNbt(entity, config.nbtSnippet);
        };
    }

    private static boolean matchesMobType(LivingEntity entity, String entityTypeId) {
        if (entityTypeId == null) return false;
        ResourceLocation id = ResourceLocation.tryParse(entityTypeId);
        if (id == null) return false;
        EntityType<?> target = BuiltInRegistries.ENTITY_TYPE.get(id);
        return target != null && entity.getType() == target;
    }

    private static boolean matchesNbt(LivingEntity entity, String snippet) {
        if (snippet == null || snippet.isBlank()) return false;
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        // Partial match: the saved NBT's string form just needs to contain the snippet.
        return tag.toString().contains(snippet.trim());
    }

    /** Grabs the SNBT of whatever entity the player is currently looking at, for the "copy from target" button. */
    public static Optional<String> nbtOfLookedAtEntity() {
        Minecraft client = Minecraft.getInstance();
        if (client.hitResult == null) return Optional.empty();
        // NOTE: verify this against genSources - some Mojmap versions expose the entity
        // hit result via a separate crosshair-target field rather than client.hitResult directly.
        if (!(client.crosshairPickEntity instanceof Entity target)) return Optional.empty();

        CompoundTag tag = new CompoundTag();
        target.saveWithoutId(tag);
        return Optional.of(tag.toString());
    }
}