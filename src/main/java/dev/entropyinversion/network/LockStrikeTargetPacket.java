package dev.entropyinversion.network;

import dev.entropyinversion.EntropyInversionMod;
import dev.entropyinversion.world.StrikeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class LockStrikeTargetPacket {
    private final BlockPos target;

    public LockStrikeTargetPacket(BlockPos target) {
        this.target = target;
    }

    public static void encode(LockStrikeTargetPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.target != null);
        if (packet.target != null) {
            buffer.writeBlockPos(packet.target);
        }
    }

    public static LockStrikeTargetPacket decode(FriendlyByteBuf buffer) {
        return new LockStrikeTargetPacket(buffer.readBoolean() ? buffer.readBlockPos() : null);
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
                    || !StrikeManager.lockTarget(player, packet.target)) {
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
