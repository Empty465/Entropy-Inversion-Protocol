package dev.entropyinversion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class AsteroidFallingBlockEntity extends FallingBlockEntity {
    private static final double RENDER_DISTANCE = 1024.0D;

    public AsteroidFallingBlockEntity(EntityType<? extends FallingBlockEntity> type, Level level) {
        super(type, level);
    }

    public void initialize(BlockPos pos, BlockState blockState) {
        CompoundTag tag = new CompoundTag();
        tag.put("BlockState", NbtUtils.writeBlockState(blockState));
        readAdditionalSaveData(tag);
        setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        setDeltaMovement(Vec3.ZERO);
        setStartPos(pos);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceToCamera) {
        return distanceToCamera < RENDER_DISTANCE * RENDER_DISTANCE;
    }
}
