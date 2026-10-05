package dev.entropyinversion.world;

import dev.entropyinversion.item.EntropyInversionRequestorItem;
import dev.entropyinversion.network.StrikeCutscenePacket;
import dev.entropyinversion.network.StrikeNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class StrikeManager {
    private static final int CUTSCENE_TICKS = 200;
    private static final int CHUNKS_PER_TICK = 4;
    private static final int DESTRUCTION_EFFECT_SAMPLE_SIZE = 4;
    private static final Map<ServerLevel, LevelState> LEVEL_STATES = new HashMap<>();
    private static final Map<UUID, Vec3> LOCKED_TARGETS = new HashMap<>();

    public static boolean lockTarget(
            ServerPlayer player,
            BlockPos requestedTarget,
            Vec3 requestedHitLocation
    ) {
        if (requestedHitLocation == null) {
            LOCKED_TARGETS.remove(player.getUUID());
            return false;
        }

        ServerLevel level = player.serverLevel();
        Vec3 eyePosition = player.getEyePosition();
        Vec3 ray = requestedHitLocation.subtract(eyePosition);
        double reachSquared = EntropyInversionRequestorItem.TARGETING_RANGE
                * EntropyInversionRequestorItem.TARGETING_RANGE;
        if (requestedTarget.getY() < level.getMinBuildHeight()
                || requestedTarget.getY() >= level.getMaxBuildHeight()
                || ray.lengthSqr() > reachSquared
                || ray.lengthSqr() < 0.0001D
                || !isNearRequestedBlock(requestedTarget, requestedHitLocation)) {
            LOCKED_TARGETS.remove(player.getUUID());
            return false;
        }

        level.getChunk(requestedTarget.getX() >> 4, requestedTarget.getZ() >> 4);
        Vec3 rayEnd = requestedHitLocation.add(ray.normalize().scale(0.05D));
        HitResult hit = level.clip(new ClipContext(
                eyePosition,
                rayEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY,
                player
        ));
        if (hit.getType() == HitResult.Type.BLOCK
                && (!(hit instanceof BlockHitResult blockHit)
                || !blockHit.getBlockPos().equals(requestedTarget))) {
            LOCKED_TARGETS.remove(player.getUUID());
            return false;
        }

        LOCKED_TARGETS.put(player.getUUID(), requestedHitLocation);
        return true;
    }

    private static boolean isNearRequestedBlock(BlockPos target, Vec3 hitLocation) {
        double tolerance = 0.1D;
        return hitLocation.x >= target.getX() - tolerance
                && hitLocation.x <= target.getX() + 1.0D + tolerance
                && hitLocation.y >= target.getY() - tolerance
                && hitLocation.y <= target.getY() + 1.0D + tolerance
                && hitLocation.z >= target.getZ() - tolerance
                && hitLocation.z <= target.getZ() + 1.0D + tolerance;
    }

    public static Vec3 consumeLockedTarget(ServerPlayer player) {
        return LOCKED_TARGETS.remove(player.getUUID());
    }

    public static void clearLockedTarget(ServerPlayer player) {
        LOCKED_TARGETS.remove(player.getUUID());
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LOCKED_TARGETS.remove(event.getEntity().getUUID());
    }

    public static void schedule(ServerLevel level, Vec3 target, UUID owner, int radius) {
        LevelState state = LEVEL_STATES.computeIfAbsent(level, ignored -> new LevelState());
        ScheduledStrike strike = new ScheduledStrike(
                level.getGameTime() + CUTSCENE_TICKS,
                target.x,
                target.y,
                target.z,
                owner,
                radius
        );
        state.scheduled.add(strike);
        warnPlayersInStrikeRadius(level, strike, level.getGameTime());
        Player player = level.getPlayerByUUID(owner);
        if (player instanceof ServerPlayer serverPlayer) {
            StrikeNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new StrikeCutscenePacket(target.x, target.y, target.z, CUTSCENE_TICKS, radius)
            );
        }
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }

        LevelState state = LEVEL_STATES.get(level);
        if (state == null) {
            return;
        }

        long gameTime = level.getGameTime();
        Iterator<ScheduledStrike> strikes = state.scheduled.iterator();
        while (strikes.hasNext()) {
            ScheduledStrike strike = strikes.next();
            warnPlayersInStrikeRadius(level, strike, gameTime);
            if (strike.dueAt <= gameTime) {
                strikes.remove();
                impact(level, strike);
                state.jobs.add(createJob(strike));
            }
        }

        for (int i = 0; i < CHUNKS_PER_TICK && !state.jobs.isEmpty(); i++) {
            DestructionJob job = state.jobs.peek();
            if (!job.chunks.isEmpty()) {
                destroyChunk(level, job, job.chunks.remove());
            } else if (!job.waterCleanupChunks.isEmpty()) {
                clearWaterInChunk(level, job, job.waterCleanupChunks.remove());
            }

            if (job.chunks.isEmpty() && job.waterCleanupChunks.isEmpty()) {
                state.jobs.remove();
                Player owner = level.getPlayerByUUID(job.owner);
                if (owner != null) {
                    owner.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable(
                                    "message.entropyinversion.complete"
                            ),
                            true
                    );
                }
            }
        }

        if (state.scheduled.isEmpty() && state.jobs.isEmpty()) {
            LEVEL_STATES.remove(level);
        }
    }

    private static void impact(ServerLevel level, ScheduledStrike strike) {
        double radius = strike.radius;
        AABB area = new AABB(
                strike.x - radius,
                level.getMinBuildHeight(),
                strike.z - radius,
                strike.x + radius + 1.0D,
                level.getMaxBuildHeight(),
                strike.z + radius + 1.0D
        );
        removeEntities(level, area, strike.x, strike.z, strike.radius, strike.owner);
        level.playSound(
                null,
                BlockPos.containing(strike.x, strike.y, strike.z),
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.WEATHER,
                5.0F,
                0.65F
        );
        level.playSound(
                null,
                BlockPos.containing(strike.x, strike.y, strike.z),
                net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                net.minecraft.sounds.SoundSource.WEATHER,
                8.0F,
                0.65F
        );
    }

    private static DestructionJob createJob(ScheduledStrike strike) {
        int centerX = (int) Math.floor(strike.x);
        int centerZ = (int) Math.floor(strike.z);
        double rimCoverageRadius = strike.radius
                + scorchedRimWidth(strike.radius)
                + scorchedRimJaggedness(strike.radius) * 1.4D
                + 1.0D;
        double radiusSquared = rimCoverageRadius * rimCoverageRadius;
        int rimChunkRadius = (int) Math.ceil(rimCoverageRadius);
        int minChunkX = Math.floorDiv(centerX - rimChunkRadius, 16);
        int maxChunkX = Math.floorDiv(centerX + rimChunkRadius, 16);
        int minChunkZ = Math.floorDiv(centerZ - rimChunkRadius, 16);
        int maxChunkZ = Math.floorDiv(centerZ + rimChunkRadius, 16);
        List<ChunkPos> chunks = new ArrayList<>();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                int minX = chunkX << 4;
                int minZ = chunkZ << 4;
                double nearestX = clamp(strike.x, minX, minX + 15);
                double nearestZ = clamp(strike.z, minZ, minZ + 15);
                double dx = nearestX - strike.x;
                double dz = nearestZ - strike.z;
                if (dx * dx + dz * dz <= radiusSquared) {
                    chunks.add(new ChunkPos(chunkX, chunkZ));
                }
            }
        }

        chunks.sort(Comparator.comparingDouble(pos -> {
            double dx = pos.getMinBlockX() + 8.0D - strike.x;
            double dz = pos.getMinBlockZ() + 8.0D - strike.z;
            return dx * dx + dz * dz;
        }));
        List<ChunkPos> waterCleanupChunks = new ArrayList<>();
        for (ChunkPos chunk : chunks) {
            int minX = chunk.getMinBlockX();
            int minZ = chunk.getMinBlockZ();
            double nearestX = clamp(strike.x, minX, minX + 15);
            double nearestZ = clamp(strike.z, minZ, minZ + 15);
            double dx = nearestX - strike.x;
            double dz = nearestZ - strike.z;
            if (dx * dx + dz * dz <= strike.radius * (double) strike.radius) {
                waterCleanupChunks.add(chunk);
            }
        }
        waterCleanupChunks.sort(Comparator.comparingDouble((ChunkPos pos) -> {
            double dx = pos.getMinBlockX() + 8.0D - strike.x;
            double dz = pos.getMinBlockZ() + 8.0D - strike.z;
            return dx * dx + dz * dz;
        }).reversed());
        return new DestructionJob(
                new ArrayDeque<>(chunks),
                new ArrayDeque<>(waterCleanupChunks),
                strike.owner,
                strike.x,
                strike.z,
                strike.radius
        );
    }

    private static void destroyChunk(ServerLevel level, DestructionJob job, ChunkPos chunkPos) {
        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        RandomSource random = level.getRandom();
        double radiusSquared = job.radius * (double) job.radius;
        int[] columns = new int[256];
        int columnCount = 0;
        for (int localX = 0; localX < 16; localX++) {
            int x = minX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = minZ + localZ;
                double dx = x + 0.5D - job.x;
                double dz = z + 0.5D - job.z;
                if (dx * dx + dz * dz <= radiusSquared) {
                    columns[columnCount++] = (localX << 4) | localZ;
                }
            }
        }
        removeEntities(
                level,
                new AABB(minX, minY, minZ, minX + 16.0D, maxY, minZ + 16.0D),
                job.x,
                job.z,
                job.radius,
                job.owner
        );
        if (columnCount == 0) {
            spawnScorchedRim(level, job, chunkPos, random);
            return;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<BlockPos> effectPositions = new ArrayList<>(DESTRUCTION_EFFECT_SAMPLE_SIZE);
        List<BlockPos> evaporationPositions = new ArrayList<>(4);
        int evaporatedWaterCount = 0;
        int destroyedBlockCount = 0;
        LevelChunkSection[] sections = chunk.getSections();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section.hasOnlyAir()) {
                continue;
            }

            int sectionMinY = level.getMinBuildHeight() + sectionIndex * 16;
            int firstLocalY = Math.max(0, minY - sectionMinY);
            int lastLocalY = Math.min(16, maxY - sectionMinY);
            for (int localY = firstLocalY; localY < lastLocalY; localY++) {
                int y = sectionMinY + localY;
                for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                    int column = columns[columnIndex];
                    int localX = column >> 4;
                    int localZ = column & 15;
                    pos.set(minX + localX, y, minZ + localZ);
                    if (job.cooledPositions.contains(pos.asLong())) {
                        continue;
                    }

                    var blockState = section.getBlockState(localX, localY, localZ);
                    if (blockState.isAir()) {
                        continue;
                    }

                    FluidState fluidState = blockState.getFluidState();
                    if (fluidState.is(FluidTags.WATER)) {
                        evaporatedWaterCount++;
                        if (evaporationPositions.size() < 4) {
                            evaporationPositions.add(pos.immutable());
                        } else if (random.nextInt(evaporatedWaterCount) < 4) {
                            evaporationPositions.set(random.nextInt(4), pos.immutable());
                        }
                        coolAdjacentLava(level, job, pos);
                    } else if (fluidState.is(FluidTags.LAVA)
                            && touchesWater(level, pos)) {
                        coolLavaBlock(level, job, pos, fluidState);
                        continue;
                    }

                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    destroyedBlockCount++;
                    if (effectPositions.size() < DESTRUCTION_EFFECT_SAMPLE_SIZE) {
                        effectPositions.add(pos.immutable());
                    } else if (random.nextInt(destroyedBlockCount) < DESTRUCTION_EFFECT_SAMPLE_SIZE) {
                        effectPositions.set(
                                random.nextInt(DESTRUCTION_EFFECT_SAMPLE_SIZE),
                                pos.immutable()
                        );
                    }
                }
            }
        }
        spawnDestructionEffects(level, effectPositions);
        spawnEvaporationEffects(level, evaporationPositions);
        spawnScorchedRim(level, job, chunkPos, random);
    }

    private static void clearWaterInChunk(ServerLevel level, DestructionJob job, ChunkPos chunkPos) {
        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int[] columns = new int[256];
        int columnCount = 0;
        double radiusSquared = job.radius * (double) job.radius;
        for (int localX = 0; localX < 16; localX++) {
            int x = minX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = minZ + localZ;
                double dx = x + 0.5D - job.x;
                double dz = z + 0.5D - job.z;
                if (dx * dx + dz * dz <= radiusSquared) {
                    columns[columnCount++] = (localX << 4) | localZ;
                }
            }
        }
        if (columnCount == 0) {
            return;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<BlockPos> evaporationPositions = new ArrayList<>(4);
        RandomSource random = level.getRandom();
        int evaporatedWaterCount = 0;
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section.hasOnlyAir()) {
                continue;
            }

            int sectionMinY = minY + sectionIndex * 16;
            int firstLocalY = Math.max(0, minY - sectionMinY);
            int lastLocalY = Math.min(16, maxY - sectionMinY);
            for (int localY = firstLocalY; localY < lastLocalY; localY++) {
                for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                    int column = columns[columnIndex];
                    int localX = column >> 4;
                    int localZ = column & 15;
                    if (!section.getBlockState(localX, localY, localZ).getFluidState().is(FluidTags.WATER)) {
                        continue;
                    }

                    pos.set(minX + localX, sectionMinY + localY, minZ + localZ);
                    evaporatedWaterCount++;
                    if (evaporationPositions.size() < 4) {
                        evaporationPositions.add(pos.immutable());
                    } else if (random.nextInt(evaporatedWaterCount) < evaporationPositions.size()) {
                        evaporationPositions.set(random.nextInt(evaporationPositions.size()), pos.immutable());
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        spawnEvaporationEffects(level, evaporationPositions);
    }

    private static int scorchedRimWidth(int strikeRadius) {
        return Math.max(2, Math.min(24, (int) Math.ceil(strikeRadius * 0.12D)));
    }

    private static double scorchedRimJaggedness(int strikeRadius) {
        return Math.max(1.0D, Math.min(8.0D, 1.0D + strikeRadius * 0.035D));
    }

    private static void spawnScorchedRim(
            ServerLevel level,
            DestructionJob job,
            ChunkPos chunkPos,
            RandomSource random
    ) {
        double rimWidth = scorchedRimWidth(job.radius);
        double jaggedness = scorchedRimJaggedness(job.radius);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<BlockPos> evaporationPositions = new ArrayList<>(4);
        int evaporatedWaterCount = 0;
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        for (int localX = 0; localX < 16; localX++) {
            int x = minX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = minZ + localZ;
                double dx = x + 0.5D - job.x;
                double dz = z + 0.5D - job.z;
                double distanceSquared = dx * dx + dz * dz;
                double distance = Math.sqrt(distanceSquared);
                double angle = Math.atan2(dz, dx);
                double edgeOffset = Math.sin(angle * 7.0D + 0.7D) * jaggedness
                        + Math.sin(angle * 17.0D - 1.4D) * jaggedness * 0.38D;
                double innerEdge = Math.max(0.0D, job.radius + edgeOffset);
                double outerEdge = innerEdge + rimWidth
                        * (0.82D + 0.18D * Math.sin(angle * 11.0D + 2.1D));
                if (distance <= job.radius || distance < innerEdge || distance > outerEdge) {
                    continue;
                }

                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (surfaceY < level.getMinBuildHeight()) {
                    continue;
                }
                pos.set(x, surfaceY, z);
                var surface = level.getBlockState(pos);
                if (surface.getFluidState().is(FluidTags.WATER)) {
                    int groundY = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
                    if (groundY < level.getMinBuildHeight()) {
                        continue;
                    }
                    for (int offset = 1; offset <= 5; offset++) {
                        int waterY = groundY + offset;
                        if (waterY >= level.getMaxBuildHeight()) {
                            break;
                        }
                        pos.set(x, waterY, z);
                        if (!level.getFluidState(pos).is(FluidTags.WATER)) {
                            break;
                        }
                        evaporatedWaterCount++;
                        if (evaporationPositions.size() < 4) {
                            evaporationPositions.add(pos.immutable());
                        } else if (random.nextInt(evaporatedWaterCount) < 4) {
                            evaporationPositions.set(random.nextInt(4), pos.immutable());
                        }
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                    pos.set(x, groundY, z);
                    surface = level.getBlockState(pos);
                }
                if (!surface.isFaceSturdy(level, pos, Direction.UP)) {
                    int searchLimit = Math.max(level.getMinBuildHeight(), pos.getY() - 3);
                    while (pos.getY() > searchLimit) {
                        pos.move(Direction.DOWN);
                        surface = level.getBlockState(pos);
                        if (surface.getFluidState().is(FluidTags.WATER)
                                || surface.getFluidState().is(FluidTags.LAVA)) {
                            break;
                        }
                        if (surface.isFaceSturdy(level, pos, Direction.UP)) {
                            break;
                        }
                    }
                }
                if (!surface.isFaceSturdy(level, pos, Direction.UP)
                        || !surface.getFluidState().isEmpty()
                        || surface.getDestroySpeed(level, pos) < 0.0F) {
                    continue;
                }

                var scarBlock = random.nextInt(4) == 0
                        ? Blocks.MAGMA_BLOCK
                        : random.nextBoolean() ? Blocks.BLACKSTONE : Blocks.BASALT;
                level.setBlock(pos, scarBlock.defaultBlockState(), 3);
            }
        }
        spawnEvaporationEffects(level, evaporationPositions);
    }

    private static void coolAdjacentLava(ServerLevel level, DestructionJob job, BlockPos waterPos) {
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.values()) {
            neighbor.setWithOffset(waterPos, direction);
            double dx = neighbor.getX() + 0.5D - job.x;
            double dz = neighbor.getZ() + 0.5D - job.z;
            if (dx * dx + dz * dz > job.radius * (double) job.radius) {
                continue;
            }
            FluidState lava = level.getFluidState(neighbor);
            if (lava.is(FluidTags.LAVA)) {
                coolLavaBlock(level, job, neighbor, lava);
            }
        }
    }

    private static boolean touchesWater(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.values()) {
            neighbor.setWithOffset(pos, direction);
            if (level.getFluidState(neighbor).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    private static void coolLavaBlock(
            ServerLevel level,
            DestructionJob job,
            BlockPos pos,
            FluidState lava
    ) {
        job.cooledPositions.add(pos.asLong());
        level.setBlock(
                pos,
                (lava.isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).defaultBlockState(),
                3
        );
    }

    private static void spawnEvaporationEffects(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            double x = pos.getX() + 0.5D;
            double y = pos.getY() + 0.4D;
            double z = pos.getZ() + 0.5D;
            level.sendParticles(ParticleTypes.CLOUD, x, y, z, 8, 0.45D, 0.5D, 0.45D, 0.025D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 5, 0.35D, 0.3D, 0.35D, 0.015D);
        }
    }

    private static void spawnDestructionEffects(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            double x = pos.getX() + 0.5D;
            double y = pos.getY() + 0.5D;
            double z = pos.getZ() + 0.5D;
            level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    x,
                    y,
                    z,
                    12,
                    0.6D,
                    0.8D,
                    0.6D,
                    0.06D
            );
            level.sendParticles(
                    ParticleTypes.LARGE_SMOKE,
                    x,
                    y,
                    z,
                    8,
                    0.8D,
                    1.0D,
                    0.8D,
                    0.025D
            );
            level.sendParticles(
                    ParticleTypes.PORTAL,
                    x,
                    y,
                    z,
                    10,
                    0.7D,
                    0.7D,
                    0.7D,
                    0.5D
            );
        }
    }

    private static void removeEntities(
            ServerLevel level,
            AABB area,
            double x,
            double z,
            int radius,
            UUID ownerId
    ) {
        double radiusSquared = radius * (double) radius;
        List<Entity> entities = level.getEntities(
                (Entity) null,
                area,
                entity -> horizontalDistanceSquared(entity.position(), x, z) <= radiusSquared
                        && !(entity instanceof Player player
                        && (player.isCreative() || player.isSpectator()))
        );
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        DamageSource damageSource = owner == null ? null : createStrikeDamageSource(owner);
        for (Entity entity : entities) {
            if (owner != null) {
                if (entity instanceof LivingEntity livingEntity) {
                    livingEntity.setLastHurtByPlayer(owner);
                }
                entity.hurt(damageSource, 1_000_000.0F);
            }
            if (!entity.isRemoved()) {
                entity.kill();
            }
        }
    }

    private static DamageSource createStrikeDamageSource(ServerPlayer owner) {
        DamageSource playerAttack = owner.damageSources().playerAttack(owner);
        return new DamageSource(playerAttack.typeHolder(), owner, owner) {
            @Override
            public Component getLocalizedDeathMessage(LivingEntity entity) {
                return Component.translatable(
                        "death.attack.entropyinversion",
                        entity.getDisplayName()
                );
            }
        };
    }

    private static void warnPlayersInStrikeRadius(
            ServerLevel level,
            ScheduledStrike strike,
            long gameTime
    ) {
        double radiusSquared = strike.radius * (double) strike.radius;
        for (ServerPlayer player : level.players()) {
            UUID playerId = player.getUUID();
            if (horizontalDistanceSquared(player.position(), strike.x, strike.z) > radiusSquared) {
                strike.nextWarningAt.remove(playerId);
                continue;
            }

            long nextWarningAt = strike.nextWarningAt.getOrDefault(playerId, Long.MIN_VALUE);
            if (gameTime >= nextWarningAt) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.entropyinversion.in_strike_zone",
                                strike.radius
                        ),
                        true
                );
                strike.nextWarningAt.put(playerId, gameTime + 20L);
            }
        }
    }

    private static double horizontalDistanceSquared(Vec3 position, double x, double z) {
        double dx = position.x - x;
        double dz = position.z - z;
        return dx * dx + dz * dz;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class LevelState {
        private final List<ScheduledStrike> scheduled = new ArrayList<>();
        private final ArrayDeque<DestructionJob> jobs = new ArrayDeque<>();
    }

    private static final class ScheduledStrike {
        private final long dueAt;
        private final double x;
        private final double y;
        private final double z;
        private final UUID owner;
        private final int radius;
        private final Map<UUID, Long> nextWarningAt = new HashMap<>();

        private ScheduledStrike(long dueAt, double x, double y, double z, UUID owner, int radius) {
            this.dueAt = dueAt;
            this.x = x;
            this.y = y;
            this.z = z;
            this.owner = owner;
            this.radius = radius;
        }
    }

    private static final class DestructionJob {
        private final ArrayDeque<ChunkPos> chunks;
        private final ArrayDeque<ChunkPos> waterCleanupChunks;
        private final UUID owner;
        private final double x;
        private final double z;
        private final int radius;
        private final Set<Long> cooledPositions = new HashSet<>();

        private DestructionJob(
                ArrayDeque<ChunkPos> chunks,
                ArrayDeque<ChunkPos> waterCleanupChunks,
                UUID owner,
                double x,
                double z,
                int radius
        ) {
            this.chunks = chunks;
            this.waterCleanupChunks = waterCleanupChunks;
            this.owner = owner;
            this.x = x;
            this.z = z;
            this.radius = radius;
        }
    }
}
