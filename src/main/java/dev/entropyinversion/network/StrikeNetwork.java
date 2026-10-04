package dev.entropyinversion.network;

import dev.entropyinversion.EntropyInversionMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class StrikeNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(EntropyInversionMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private StrikeNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(
                0,
                StrikeCutscenePacket.class,
                StrikeCutscenePacket::encode,
                StrikeCutscenePacket::decode,
                StrikeCutscenePacket::handle
        );
        CHANNEL.registerMessage(
                1,
                RequestStrikePacket.class,
                RequestStrikePacket::encode,
                RequestStrikePacket::decode,
                RequestStrikePacket::handle
        );
        CHANNEL.registerMessage(
                2,
                LockStrikeTargetPacket.class,
                LockStrikeTargetPacket::encode,
                LockStrikeTargetPacket::decode,
                LockStrikeTargetPacket::handle
        );
    }
}
