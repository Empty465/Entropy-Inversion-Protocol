package dev.entropyinversion.world;

import dev.entropyinversion.network.StrikeCutscenePacket;
import dev.entropyinversion.network.StrikeNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
import java.util.UUID;

public final class StrikeManager {
    private static final int CUTSCENE_TICKS = 100;
    private static final int CHUNKS_PER_TICK = 2;
    private static final Map<ServerLevel, LevelState> LEVEL_STATES = new HashMap<>();

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
            destroyChunk(level, job, job.chunks.remove());
            if (job.chunks.isEmpty()) {
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
        int minChunkX = Math.floorDiv(centerX - strike.radius, 16);
        int maxChunkX = Math.floorDiv(centerX + strike.radius, 16);
        int minChunkZ = Math.floorDiv(centerZ - strike.radius, 16);
        int maxChunkZ = Math.floorDiv(centerZ + strike.radius, 16);
        double radiusSquared = strike.radius * (double) strike.radius;
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
        return new DestructionJob(
                new ArrayDeque<>(chunks),
                strike.owner,
                strike.x,
                strike.z,
                strike.radius
        );
    }

    private static void destroyChunk(ServerLevel level, DestructionJob job, ChunkPos chunkPos) {
        level.getChunk(chunkPos.x, chunkPos.z);
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        double radiusSquared = job.radius * (double) job.radius;
        removeEntities(
                level,
                new AABB(minX, minY, minZ, minX + 16.0D, maxY, minZ + 16.0D),
                job.x,
                job.z,
                job.radius,
                job.owner
        );
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            int x = minX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = minZ + localZ;
                double dx = x + 0.5D - job.x;
                double dz = z + 0.5D - job.z;
                if (dx * dx + dz * dz > radiusSquared) {
                    continue;
                }

                for (int y = minY; y < maxY; y++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
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
        private final UUID owner;
        private final double x;
        private final double z;
        private final int radius;

        private DestructionJob(
                ArrayDeque<ChunkPos> chunks,
                UUID owner,
                double x,
                double z,
                int radius
        ) {
            this.chunks = chunks;
            this.owner = owner;
            this.x = x;
            this.z = z;
            this.radius = radius;
        }
    }
}
