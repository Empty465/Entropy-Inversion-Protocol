package dev.entropyinversion.network;

import dev.entropyinversion.client.CutsceneOverlay;
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

    public StrikeCutscenePacket(double x, double y, double z, int duration, int radius) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.duration = duration;
        this.radius = radius;
    }

    public static void encode(StrikeCutscenePacket packet, FriendlyByteBuf buffer) {
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
        buffer.writeVarInt(packet.duration);
        buffer.writeVarInt(packet.radius);
    }

    public static StrikeCutscenePacket decode(FriendlyByteBuf buffer) {
        return new StrikeCutscenePacket(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    public static void handle(
            StrikeCutscenePacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () ->
                () -> CutsceneOverlay.begin(
                        packet.x,
                        packet.y,
                        packet.z,
                        packet.duration,
                        packet.radius
                )));
        context.setPacketHandled(true);
    }
}
