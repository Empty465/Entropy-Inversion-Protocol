package dev.entropyinversion.network;

import dev.entropyinversion.client.ClientModHooks;
import dev.entropyinversion.item.AttackMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class StrikeCutscenePacket {
    private final double x;
    private final double y;
    private final double z;
    private final int duration;
    private final int radius;
    private final int attackMode;

    public StrikeCutscenePacket(
            double x,
            double y,
            double z,
            int duration,
            int radius,
            AttackMode attackMode
    ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.duration = duration;
        this.radius = radius;
        this.attackMode = attackMode.ordinal();
    }

    public static void encode(StrikeCutscenePacket packet, FriendlyByteBuf buffer) {
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
        buffer.writeVarInt(packet.duration);
        buffer.writeVarInt(packet.radius);
        buffer.writeVarInt(packet.attackMode);
    }

    public static StrikeCutscenePacket decode(FriendlyByteBuf buffer) {
        double x = buffer.readDouble();
        double y = buffer.readDouble();
        double z = buffer.readDouble();
        int duration = buffer.readVarInt();
        int radius = buffer.readVarInt();
        int attackMode = buffer.readVarInt();
        AttackMode mode = AttackMode.fromOrdinal(attackMode);
        return new StrikeCutscenePacket(
                x,
                y,
                z,
                duration,
                radius,
                mode == null ? AttackMode.ENTROPY_INVERSION : mode
        );
    }

    public static void handle(
            StrikeCutscenePacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () ->
                () -> ClientModHooks.beginCutscene(
                        packet.x,
                        packet.y,
                        packet.z,
                        packet.duration,
                        packet.radius,
                        AttackMode.fromOrdinal(packet.attackMode) == null
                                ? AttackMode.ENTROPY_INVERSION
                                : AttackMode.fromOrdinal(packet.attackMode)
                )));
        context.setPacketHandled(true);
    }
}
