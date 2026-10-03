package dev.orbitalstrike.network;

import dev.orbitalstrike.OrbitalStrikeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class StrikeNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(OrbitalStrikeMod.MOD_ID, "main"),
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
    }
}
