package dev.entropyinversion.world;

import dev.entropyinversion.AsteroidDimensions;
import dev.entropyinversion.item.EntropyInversionRequestorItem;
import dev.entropyinversion.item.AttackMode;
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
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class StrikeManager {
    private static final int CUTSCENE_TICKS = 200;
    private static final int ASTEROID_CUTSCENE_TICKS = 260;
    private static final int ASTEROID_FLIGHT_TICKS = 150;
    private static final int ASTEROID_SPAWN_HEIGHT = 120;
    private static final int MICROBOT_REVEAL_TICKS = 160;
    private static final int MICROBOT_PARTICLE_INTERVAL_TICKS = 5;
    private static final int CHUNKS_PER_TICK = 4;
    private static final int ASTEROID_CHUNKS_PER_TICK = 16;
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

    public static void schedule(
            ServerLevel level,
            Vec3 target,
            UUID owner,
            int radius,
            AttackMode attackMode
    ) {
        LevelState state = LEVEL_STATES.computeIfAbsent(level, ignored -> new LevelState());
        int cutsceneTicks = attackMode == AttackMode.ASTEROID_BOMBARDMENT
                ? ASTEROID_CUTSCENE_TICKS
                : CUTSCENE_TICKS;
        ScheduledStrike strike = new ScheduledStrike(
                level.getGameTime() + cutsceneTicks,
                target.x,
                target.y,
                target.z,
                owner,
                radius,
                attackMode,
                cutsceneTicks
        );
        state.scheduled.add(strike);
        warnPlayersInStrikeRadius(level, strike, level.getGameTime());
        if (attackMode == AttackMode.ASTEROID_BOMBARDMENT) {
            AsteroidFlight asteroid = summonAsteroid(level, strike);
            state.asteroids.put(
                    asteroid.centerEntity.getUUID(),
                    asteroid
            );
        }
        Player player = level.getPlayerByUUID(owner);
        if (player instanceof ServerPlayer serverPlayer) {
            StrikeNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new StrikeCutscenePacket(
                            target.x,
                            target.y,
                            target.z,
                            cutsceneTicks,
                            radius,
                            attackMode
                    )
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
            if (strike.attackMode == AttackMode.ANTI_ORGANIC_MICROBOTS
                    && !strike.microbotsRevealed
                    && strike.dueAt - gameTime <= MICROBOT_REVEAL_TICKS) {
                revealMicrobotTargets(level, strike);
            }
            if (strike.attackMode == AttackMode.ANTI_ORGANIC_MICROBOTS
                    && strike.microbotsRevealed
                    && strike.dueAt > gameTime
                    && gameTime % MICROBOT_PARTICLE_INTERVAL_TICKS == 0L) {
                emitMicrobotTargetParticles(level, strike);
            }
            if (strike.dueAt <= gameTime) {
                strikes.remove();
                switch (strike.attackMode) {
                    case ENTROPY_INVERSION -> {
                        impact(level, strike);
                        state.jobs.add(createJob(strike));
                    }
                    case ASTEROID_BOMBARDMENT -> {
                    }
                    case ANTI_ORGANIC_MICROBOTS -> {
                        impactMicrobots(level, strike);
                    }
                }
            }
        }

        int chunkBudget = !state.jobs.isEmpty()
                && state.jobs.peek().attackMode == AttackMode.ASTEROID_BOMBARDMENT
                ? ASTEROID_CHUNKS_PER_TICK
                : CHUNKS_PER_TICK;
        for (int i = 0; i < chunkBudget && !state.jobs.isEmpty(); i++) {
            DestructionJob job = state.jobs.peek();
            if (!job.chunks.isEmpty()) {
                ChunkPos chunk = job.chunks.remove();
                if (job.attackMode == AttackMode.ASTEROID_BOMBARDMENT) {
                    carveAsteroidChunk(level, job, chunk);
                } else {
                    destroyChunk(level, job, chunk);
                }
            } else if (!job.waterCleanupChunks.isEmpty()) {
                clearWaterInChunk(level, job, job.waterCleanupChunks.remove());
            }

            if (job.chunks.isEmpty() && job.waterCleanupChunks.isEmpty()) {
                state.jobs.remove();
                if (job.attackMode == AttackMode.ASTEROID_BOMBARDMENT) {
                    leaveAsteroidCore(level, job);
                }
                Player owner = level.getPlayerByUUID(job.owner);
                if (owner != null) {
                    Component completion = switch (job.attackMode) {
                        case ASTEROID_BOMBARDMENT -> Component.translatable(
                                "message.entropyinversion.asteroid_complete"
                        );
                        default -> Component.translatable("message.entropyinversion.complete");
                    };
                    owner.displayClientMessage(completion, true);
                }
            }
        }

        for (AsteroidFlight flight : new ArrayList<>(state.asteroids.values())) {
            FallingBlockEntity asteroid = flight.centerEntity;
            int elapsedTicks = (int) (gameTime - flight.startedAt);
            if (elapsedTicks >= ASTEROID_FLIGHT_TICKS || asteroid.isRemoved()) {
                setAsteroidClusterPosition(flight, flight.impactY + 0.25D);
                resolveAsteroidImpact(level, state, flight);
                flight.impacted = true;
            } else if (!flight.impacted) {
                double[] flightState = asteroidFlightState(flight, elapsedTicks);
                setAsteroidClusterPosition(flight, flightState[0], flightState[1]);
                level.sendParticles(
                        ParticleTypes.FLAME,
                        asteroid.getX(),
                        asteroid.getY(),
                        asteroid.getZ(),
                        4,
                        0.65D,
                        0.65D,
                        0.65D,
                        0.01D
                );
                level.sendParticles(
                        ParticleTypes.LARGE_SMOKE,
                        asteroid.getX(),
                        asteroid.getY(),
                        asteroid.getZ(),
                        2,
                        0.4D,
                        0.4D,
                        0.4D,
                        0.004D
                );
            }
        }
        state.asteroids.values().removeIf(flight -> flight.impacted);

        if (state.scheduled.isEmpty() && state.jobs.isEmpty() && state.asteroids.isEmpty()) {
            LEVEL_STATES.remove(level);
        }
    }

    private static int getAsteroidImpactY(ServerLevel level, ScheduledStrike strike) {
        int blockX = BlockPos.containing(strike.x, strike.y, strike.z).getX();
        int blockZ = BlockPos.containing(strike.x, strike.y, strike.z).getZ();
        return Math.max(
                level.getMinBuildHeight(),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ) - 1
        );
    }

    private static void resolveAsteroidImpact(
            ServerLevel level,
            LevelState state,
            AsteroidFlight flight
    ) {
        for (FallingBlockEntity piece : flight.entities) {
            piece.discard();
        }
        for (long chunkKey : flight.forcedChunks) {
            level.setChunkForced((int) chunkKey, (int) (chunkKey >> 32), false);
        }
        ScheduledStrike strike = new ScheduledStrike(
                level.getGameTime(),
                flight.strike.x,
                flight.impactY,
                flight.strike.z,
                flight.strike.owner,
                flight.strike.radius,
                AttackMode.ASTEROID_BOMBARDMENT,
                flight.strike.cutsceneTicks
        );
        impactAsteroid(level, strike);
        state.jobs.add(createAsteroidJob(strike, flight.impactY));
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
        removeEntities(
                level,
                area,
                strike.x,
                strike.z,
                strike.radius,
                strike.owner,
                AttackMode.ENTROPY_INVERSION
        );
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

    private static AsteroidFlight summonAsteroid(ServerLevel level, ScheduledStrike strike) {
        int x = BlockPos.containing(strike.x, strike.y, strike.z).getX();
        int z = BlockPos.containing(strike.x, strike.y, strike.z).getZ();
        int impactY = getAsteroidImpactY(level, strike);
        int spawnY = Math.min(level.getMaxBuildHeight() - 2, impactY + ASTEROID_SPAWN_HEIGHT);
        int halfSize = AsteroidDimensions.halfSizeForRadius(strike.radius);
        List<BlockPos> offsets = new ArrayList<>();
        List<FallingBlockEntity> pieces = new ArrayList<>();
        FallingBlockEntity centerEntity = null;
        Set<Long> forcedChunks = new HashSet<>();
        for (int yOffset = -halfSize; yOffset <= halfSize; yOffset++) {
            for (int xOffset = -halfSize; xOffset <= halfSize; xOffset++) {
                for (int zOffset = -halfSize; zOffset <= halfSize; zOffset++) {
                    if (!AsteroidDimensions.isInsideAsteroid(xOffset, yOffset, zOffset, halfSize)) {
                        continue;
                    }
                    BlockPos spawnPos = new BlockPos(x + xOffset, spawnY + yOffset, z + zOffset);
                    int chunkX = spawnPos.getX() >> 4;
                    int chunkZ = spawnPos.getZ() >> 4;
                    level.getChunk(chunkX, chunkZ);
                    long chunkKey = ((long) chunkZ << 32) | (chunkX & 0xFFFFFFFFL);
                    if (forcedChunks.add(chunkKey)) {
                        level.setChunkForced(chunkX, chunkZ, true);
                    }
                    level.setBlock(spawnPos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                    FallingBlockEntity piece = FallingBlockEntity.fall(
                            level,
                            spawnPos,
                            Blocks.MAGMA_BLOCK.defaultBlockState()
                    );
                    piece.disableDrop();
                    piece.setNoGravity(true);
                    piece.noPhysics = true;
                    piece.setDeltaMovement(Vec3.ZERO);
                    pieces.add(piece);
                    offsets.add(new BlockPos(xOffset, yOffset, zOffset));
                    if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
                        centerEntity = piece;
                    }
                }
            }
        }
        level.sendParticles(
                ParticleTypes.FLAME,
                strike.x,
                spawnY,
                strike.z,
                24,
                0.6D,
                0.6D,
                0.6D,
                0.02D
        );
        level.playSound(
                null,
                new BlockPos(x, spawnY, z),
                net.minecraft.sounds.SoundEvents.FIRECHARGE_USE,
                net.minecraft.sounds.SoundSource.WEATHER,
                3.0F,
                0.55F
        );
        return new AsteroidFlight(
                pieces,
                offsets,
                centerEntity,
                forcedChunks,
                strike,
                impactY,
                level.getGameTime(),
                spawnY
        );
    }

    private static void setAsteroidClusterPosition(AsteroidFlight flight, double centerY) {
        setAsteroidClusterPosition(flight, centerY, 0.0D);
    }

    // Returns {y, velocityY}: constant fast fall, then linear braking over the last 30 blocks.
    private static double[] asteroidFlightState(AsteroidFlight flight, double ticks) {
        double distance = flight.startY - flight.impactY;
        double brake = Math.min(distance, 30.0D);
        double fast = distance - brake;
        double speed = (fast + 2.0D * brake) / ASTEROID_FLIGHT_TICKS;
        double fastTicks = fast / speed;
        double fallen;
        double velocity;
        if (ticks <= fastTicks) {
            fallen = speed * ticks;
            velocity = speed;
        } else {
            double brakeTicks = 2.0D * brake / speed;
            double q = Math.min(1.0D, (ticks - fastTicks) / brakeTicks);
            fallen = fast + brake * (2.0D * q - q * q);
            velocity = speed * (1.0D - q);
        }
        return new double[]{flight.startY - fallen, -velocity};
    }

    private static void setAsteroidClusterPosition(AsteroidFlight flight, double centerY, double velocityY) {
        for (int i = 0; i < flight.entities.size(); i++) {
            FallingBlockEntity piece = flight.entities.get(i);
            BlockPos offset = flight.offsets.get(i);
            piece.setPos(
                    flight.strike.x + offset.getX(),
                    centerY + offset.getY(),
                    flight.strike.z + offset.getZ()
            );
            piece.setDeltaMovement(0.0D, velocityY, 0.0D);
            piece.hurtMarked = velocityY != 0.0D;
        }
    }

    private static void impactAsteroid(ServerLevel level, ScheduledStrike strike) {
        int craterDepth = Math.max(8, Math.min(48, strike.radius / 3));
        AABB area = new AABB(
                strike.x - strike.radius,
                level.getMinBuildHeight(),
                strike.z - strike.radius,
                strike.x + strike.radius + 1.0D,
                level.getMaxBuildHeight(),
                strike.z + strike.radius + 1.0D
        );
        removeAsteroidEntities(
                level,
                area,
                strike
        );
        BlockPos center = BlockPos.containing(strike.x, strike.y, strike.z);
        level.sendParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                strike.x,
                strike.y,
                strike.z,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D
        );
        level.sendParticles(ParticleTypes.FLAME, strike.x, strike.y, strike.z, 80, 2.0D, 1.0D, 2.0D, 0.12D);
        level.playSound(
                null,
                center,
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.WEATHER,
                8.0F,
                0.5F
        );
        level.playSound(
                null,
                center,
                net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                net.minecraft.sounds.SoundSource.WEATHER,
                8.0F,
                0.55F
        );
    }

    private static void removeAsteroidEntities(ServerLevel level, AABB area, ScheduledStrike strike) {
        double radiusSquared = strike.radius * (double) strike.radius;
        int craterDepth = Math.max(8, Math.min(48, strike.radius / 3));
        List<Entity> entities = level.getEntities(
                (Entity) null,
                area,
                entity -> {
                    if (horizontalDistanceSquared(entity.position(), strike.x, strike.z) > radiusSquared
                            || entity instanceof Player player
                            && (player.isCreative() || player.isSpectator())) {
                        return false;
                    }

                    return entity.getBoundingBox().maxY >= strike.y - craterDepth;
                }
        );
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(strike.owner);
        DamageSource damageSource = owner == null ? null
                : createStrikeDamageSource(owner, AttackMode.ASTEROID_BOMBARDMENT);
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

    private static void impactMicrobots(ServerLevel level, ScheduledStrike strike) {
        if (!strike.microbotsRevealed) {
            revealMicrobotTargets(level, strike);
        }

        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(strike.owner);
        DamageSource damageSource = owner == null ? null
                : createStrikeDamageSource(owner, AttackMode.ANTI_ORGANIC_MICROBOTS);
        for (UUID targetId : strike.microbotTargets) {
            Entity entity = level.getEntity(targetId);
            if (!(entity instanceof LivingEntity livingEntity) || entity.isRemoved()) {
                continue;
            }
            if (owner != null) {
                livingEntity.setLastHurtByPlayer(owner);
                livingEntity.hurt(damageSource, 1_000_000.0F);
            }
            if (!livingEntity.isRemoved()) {
                livingEntity.kill();
            }
        }

        level.playSound(
                null,
                BlockPos.containing(strike.x, strike.y, strike.z),
                net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE,
                net.minecraft.sounds.SoundSource.WEATHER,
                5.0F,
                0.65F
        );
        level.sendParticles(
                ParticleTypes.PORTAL,
                strike.x,
                strike.y,
                strike.z,
                48,
                1.5D,
                1.0D,
                1.5D,
                0.35D
        );
    }

    private static void revealMicrobotTargets(ServerLevel level, ScheduledStrike strike) {
        strike.microbotsRevealed = true;
        double radiusSquared = strike.radius * (double) strike.radius;
        AABB area = new AABB(
                strike.x - strike.radius,
                level.getMinBuildHeight(),
                strike.z - strike.radius,
                strike.x + strike.radius + 1.0D,
                level.getMaxBuildHeight(),
                strike.z + strike.radius + 1.0D
        );
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                entity -> horizontalDistanceSquared(entity.position(), strike.x, strike.z) <= radiusSquared
                        && !(entity instanceof Player player
                        && (player.isCreative() || player.isSpectator()))
        );
        for (LivingEntity entity : targets) {
            strike.microbotTargets.add(entity.getUUID());
            entity.addEffect(new MobEffectInstance(
                    MobEffects.GLOWING,
                    MICROBOT_REVEAL_TICKS + 10,
                    0,
                    true,
                    false,
                    false
            ));
        }
        emitMicrobotTargetParticles(level, strike);
    }

    private static void emitMicrobotTargetParticles(ServerLevel level, ScheduledStrike strike) {
        for (UUID targetId : strike.microbotTargets) {
            Entity entity = level.getEntity(targetId);
            if (!(entity instanceof LivingEntity livingEntity) || entity.isRemoved()) {
                continue;
            }
            double centerY = entity.getY() + entity.getBbHeight() * 0.5D;
            level.sendParticles(
                    ParticleTypes.PORTAL,
                    entity.getX(),
                    centerY,
                    entity.getZ(),
                    8,
                    entity.getBbWidth() * 0.45D,
                    entity.getBbHeight() * 0.4D,
                    entity.getBbWidth() * 0.45D,
                    0.12D
            );
            level.sendParticles(
                    ParticleTypes.END_ROD,
                    entity.getX(),
                    centerY,
                    entity.getZ(),
                    4,
                    entity.getBbWidth() * 0.35D,
                    entity.getBbHeight() * 0.4D,
                    entity.getBbWidth() * 0.35D,
                    0.02D
            );
            level.sendParticles(
                    ParticleTypes.WITCH,
                    entity.getX(),
                    centerY,
                    entity.getZ(),
                    4,
                    entity.getBbWidth() * 0.4D,
                    entity.getBbHeight() * 0.4D,
                    entity.getBbWidth() * 0.4D,
                    0.04D
            );
        }
    }

    private static DestructionJob createAsteroidJob(ScheduledStrike strike, int landingY) {
        int centerX = (int) Math.floor(strike.x);
        int centerZ = (int) Math.floor(strike.z);
        double scorchedRadius = strike.radius
                + scorchedRimWidth(strike.radius)
                + scorchedRimJaggedness(strike.radius) * 1.4D
                + 1.0D;
        int chunkRadius = (int) Math.ceil(scorchedRadius);
        int minChunkX = Math.floorDiv(centerX - chunkRadius, 16);
        int maxChunkX = Math.floorDiv(centerX + chunkRadius, 16);
        int minChunkZ = Math.floorDiv(centerZ - chunkRadius, 16);
        int maxChunkZ = Math.floorDiv(centerZ + chunkRadius, 16);
        List<ChunkPos> chunks = new ArrayList<>();
        double radiusSquared = scorchedRadius * scorchedRadius;

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
        return new DestructionJob(
                new ArrayDeque<>(chunks),
                new ArrayDeque<>(),
                strike.owner,
                strike.x,
                strike.z,
                strike.radius,
                AttackMode.ASTEROID_BOMBARDMENT,
                landingY
        );
    }

    private static void carveAsteroidChunk(ServerLevel level, DestructionJob job, ChunkPos chunkPos) {
        level.getChunk(chunkPos.x, chunkPos.z);
        double radiusSquared = job.radius * (double) job.radius;
        int maxDepth = Math.max(2, Math.min(48, job.radius / 3));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxY = level.getMaxBuildHeight();

        for (int localX = 0; localX < 16; localX++) {
            int x = minX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = minZ + localZ;
                double dx = x + 0.5D - job.x;
                double dz = z + 0.5D - job.z;
                double distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > radiusSquared) {
                    continue;
                }

                for (int y = maxY - 1; y >= job.landingY; y--) {
                    pos.set(x, y, z);
                    var state = level.getBlockState(pos);
                    if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0.0F) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }

                double craterProfile = Math.sqrt(Math.max(0.0D, 1.0D - distanceSquared / radiusSquared));
                int depth = Math.max(1, (int) Math.round(maxDepth * craterProfile));
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                int bottomY = Math.max(level.getMinBuildHeight(), surfaceY - depth + 1);
                for (int y = surfaceY; y >= bottomY; y--) {
                    pos.set(x, y, z);
                    var state = level.getBlockState(pos);
                    if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0.0F) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                if (bottomY > level.getMinBuildHeight()) {
                    pos.set(x, bottomY, z);
                    if (level.isEmptyBlock(pos)
                            && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                        level.setBlock(pos, randomScorchedBlock(level.getRandom()).defaultBlockState(), 3);
                    }
                }
            }
        }
        spawnScorchedRim(level, job, chunkPos, level.getRandom());
    }

    private static void leaveAsteroidCore(ServerLevel level, DestructionJob job) {
        int centerX = (int) Math.floor(job.x);
        int centerZ = (int) Math.floor(job.z);
        int halfSize = AsteroidDimensions.halfSizeForRadius(job.radius);
        int coreCenterY = Math.max(
                level.getMinBuildHeight(),
                job.landingY - Math.max(2, Math.min(48, job.radius / 3)) + 1
        );
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int yOffset = -halfSize; yOffset <= halfSize; yOffset++) {
            for (int xOffset = -halfSize; xOffset <= halfSize; xOffset++) {
                for (int zOffset = -halfSize; zOffset <= halfSize; zOffset++) {
                    if (!AsteroidDimensions.isInsideAsteroid(xOffset, yOffset, zOffset, halfSize)) {
                        continue;
                    }
                    pos.set(centerX + xOffset, coreCenterY + yOffset, centerZ + zOffset);
                    if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
                        continue;
                    }
                    int material = level.getRandom().nextInt(10);
                    var coreBlock = xOffset == 0 && yOffset == 0 && zOffset == 0 || material < 3
                            ? Blocks.MAGMA_BLOCK
                            : material < 7 ? Blocks.BLACKSTONE
                            : material < 9 ? Blocks.OBSIDIAN : Blocks.BASALT;
                    level.setBlock(pos, coreBlock.defaultBlockState(), 3);
                }
            }
        }
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
                strike.radius,
                AttackMode.ENTROPY_INVERSION,
                BlockPos.containing(strike.x, strike.y, strike.z).getY()
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
                job.owner,
                AttackMode.ENTROPY_INVERSION
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

                level.setBlock(pos, randomScorchedBlock(random).defaultBlockState(), 3);
            }
        }
        spawnEvaporationEffects(level, evaporationPositions);
    }

    private static net.minecraft.world.level.block.Block randomScorchedBlock(RandomSource random) {
        int material = random.nextInt(8);
        if (material < 2) {
            return Blocks.MAGMA_BLOCK;
        }
        if (material < 5) {
            return Blocks.BLACKSTONE;
        }
        if (material < 7) {
            return Blocks.BASALT;
        }
        return Blocks.OBSIDIAN;
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
            UUID ownerId,
            AttackMode attackMode
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
        DamageSource damageSource = owner == null ? null
                : createStrikeDamageSource(owner, attackMode);
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

    private static DamageSource createStrikeDamageSource(ServerPlayer owner, AttackMode attackMode) {
        DamageSource playerAttack = owner.damageSources().playerAttack(owner);
        return new DamageSource(playerAttack.typeHolder(), owner, owner) {
            @Override
            public Component getLocalizedDeathMessage(LivingEntity entity) {
                return Component.translatable(
                        switch (attackMode) {
                            case ASTEROID_BOMBARDMENT -> "death.attack.entropyinversion.asteroid";
                            case ANTI_ORGANIC_MICROBOTS -> "death.attack.entropyinversion.microbots";
                            default -> "death.attack.entropyinversion";
                        },
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
                                "message.entropyinversion.in_attack_zone",
                                Component.translatable(strike.attackMode.getNameKey()),
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
        private final Map<UUID, AsteroidFlight> asteroids = new HashMap<>();
    }

    private static final class AsteroidFlight {
        private final List<FallingBlockEntity> entities;
        private final List<BlockPos> offsets;
        private final FallingBlockEntity centerEntity;
        private final Set<Long> forcedChunks;
        private final ScheduledStrike strike;
        private final int impactY;
        private final long startedAt;
        private final double startY;
        private boolean impacted;

        private AsteroidFlight(
                List<FallingBlockEntity> entities,
                List<BlockPos> offsets,
                FallingBlockEntity centerEntity,
                Set<Long> forcedChunks,
                ScheduledStrike strike,
                int impactY,
                long startedAt,
                double startY
        ) {
            this.entities = entities;
            this.offsets = offsets;
            this.centerEntity = centerEntity;
            this.forcedChunks = forcedChunks;
            this.strike = strike;
            this.impactY = impactY;
            this.startedAt = startedAt;
            this.startY = startY;
        }
    }

    private static final class ScheduledStrike {
        private final long dueAt;
        private final double x;
        private final double y;
        private final double z;
        private final UUID owner;
        private final int radius;
        private final AttackMode attackMode;
        private final int cutsceneTicks;
        private final Set<UUID> microbotTargets = new LinkedHashSet<>();
        private boolean microbotsRevealed;
        private final Map<UUID, Long> nextWarningAt = new HashMap<>();

        private ScheduledStrike(
                long dueAt,
                double x,
                double y,
                double z,
                UUID owner,
                int radius,
                AttackMode attackMode,
                int cutsceneTicks
        ) {
            this.dueAt = dueAt;
            this.x = x;
            this.y = y;
            this.z = z;
            this.owner = owner;
            this.radius = radius;
            this.attackMode = attackMode;
            this.cutsceneTicks = cutsceneTicks;
        }
    }

    private static final class DestructionJob {
        private final ArrayDeque<ChunkPos> chunks;
        private final ArrayDeque<ChunkPos> waterCleanupChunks;
        private final UUID owner;
        private final double x;
        private final double z;
        private final int radius;
        private final AttackMode attackMode;
        private final int landingY;
        private final Set<Long> cooledPositions = new HashSet<>();
        private DestructionJob(
                ArrayDeque<ChunkPos> chunks,
                ArrayDeque<ChunkPos> waterCleanupChunks,
                UUID owner,
                double x,
                double z,
                int radius,
                AttackMode attackMode,
                int landingY
        ) {
            this.chunks = chunks;
            this.waterCleanupChunks = waterCleanupChunks;
            this.owner = owner;
            this.x = x;
            this.z = z;
            this.radius = radius;
            this.attackMode = attackMode;
            this.landingY = landingY;
        }
    }
}
