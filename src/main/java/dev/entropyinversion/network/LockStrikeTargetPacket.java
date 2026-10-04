package dev.entropyinversion.network;

import dev.entropyinversion.EntropyInversionMod;
import dev.entropyinversion.world.StrikeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class LockStrikeTargetPacket {
    private final BlockPos target;
    private final Vec3 hitLocation;

    public LockStrikeTargetPacket(BlockPos target, Vec3 hitLocation) {
        this.target = target;
        this.hitLocation = hitLocation;
    }

    public static void encode(LockStrikeTargetPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.target != null);
        if (packet.target != null) {
            buffer.writeBlockPos(packet.target);
            buffer.writeDouble(packet.hitLocation.x);
            buffer.writeDouble(packet.hitLocation.y);
            buffer.writeDouble(packet.hitLocation.z);
        }
    }

    public static LockStrikeTargetPacket decode(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return new LockStrikeTargetPacket(null, null);
        }
        BlockPos target = buffer.readBlockPos();
        Vec3 hitLocation = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        return new LockStrikeTargetPacket(target, hitLocation);
    }

    public static void handle(
            LockStrikeTargetPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            if (packet.target == null) {
                StrikeManager.clearLockedTarget(player);
                return;
            }

            if ((!hasRequestor(player.getItemInHand(InteractionHand.MAIN_HAND))
                    && !hasRequestor(player.getItemInHand(InteractionHand.OFF_HAND)))
                    || !StrikeManager.lockTarget(player, packet.target, packet.hitLocation)) {
                StrikeManager.clearLockedTarget(player);
                player.displayClientMessage(
                        Component.translatable("message.entropyinversion.no_target"),
                        true
                );
            }
        });
        context.setPacketHandled(true);
    }

    private static boolean hasRequestor(net.minecraft.world.item.ItemStack stack) {
        return stack.is(EntropyInversionMod.ENTROPY_INVERSION_REQUESTOR.get());
    }
}
