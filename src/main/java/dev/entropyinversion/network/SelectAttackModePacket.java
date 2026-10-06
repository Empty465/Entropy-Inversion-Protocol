package dev.entropyinversion.network;

import dev.entropyinversion.EntropyInversionMod;
import dev.entropyinversion.item.AttackMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class SelectAttackModePacket {
    private final InteractionHand hand;
    private final int mode;

    public SelectAttackModePacket(InteractionHand hand, int mode) {
        this.hand = hand;
        this.mode = mode;
    }

    public static void encode(SelectAttackModePacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.hand);
        buffer.writeVarInt(packet.mode);
    }

    public static SelectAttackModePacket decode(FriendlyByteBuf buffer) {
        return new SelectAttackModePacket(buffer.readEnum(InteractionHand.class), buffer.readVarInt());
    }

    public static void handle(
            SelectAttackModePacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            AttackMode mode = AttackMode.fromOrdinal(packet.mode);
            if (player == null || mode == null
                    || !player.getItemInHand(packet.hand)
                    .is(EntropyInversionMod.ENTROPY_INVERSION_REQUESTOR.get())) {
                return;
            }

            AttackMode.writeToStack(player.getItemInHand(packet.hand), mode);
        });
        context.setPacketHandled(true);
    }
}
