package dev.entropyinversion.network;

import dev.entropyinversion.client.CutsceneOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class StrikeCutscenePacket {
    private final double x;
    private final double z;
    private final int duration;

    public StrikeCutscenePacket(double x, double z, int duration) {
        this.x = x;
        this.z = z;
        this.duration = duration;
    }

    public static void encode(StrikeCutscenePacket packet, FriendlyByteBuf buffer) {
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.z);
        buffer.writeVarInt(packet.duration);
    }

    public static StrikeCutscenePacket decode(FriendlyByteBuf buffer) {
        return new StrikeCutscenePacket(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readVarInt()
        );
    }

    public static void handle(
            StrikeCutscenePacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () ->
                () -> CutsceneOverlay.begin(packet.x, packet.z, packet.duration)));
        context.setPacketHandled(true);
    }
}
