package dev.entropyinversion.network;

import dev.entropyinversion.item.EntropyInversionRequestorItem;
import dev.entropyinversion.world.StrikeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class RequestStrikePacket {
    private final int radius;

    public RequestStrikePacket(int radius) {
        this.radius = radius;
    }

    public static void encode(RequestStrikePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.radius);
    }

    public static RequestStrikePacket decode(FriendlyByteBuf buffer) {
        return new RequestStrikePacket(buffer.readVarInt());
    }

    public static void handle(
            RequestStrikePacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !hasRequestor(player.getItemInHand(InteractionHand.MAIN_HAND))
                    && !hasRequestor(player.getItemInHand(InteractionHand.OFF_HAND))) {
                return;
            }

            if (packet.radius < EntropyInversionRequestorItem.MIN_STRIKE_RADIUS
                    || packet.radius > EntropyInversionRequestorItem.MAX_STRIKE_RADIUS) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.entropyinversion.invalid_radius"
                        ),
                        true
                );
                return;
            }

            if (player.getCooldowns().isOnCooldown(
                    dev.entropyinversion.EntropyInversionMod.ENTROPY_INVERSION_REQUESTOR.get())) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.entropyinversion.cooldown"
                        ),
                        true
                );
                return;
            }

            Vec3 target = StrikeManager.consumeLockedTarget(player);
            if (target == null) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.entropyinversion.no_target"
                        ),
                        true
                );
                return;
            }
            net.minecraft.core.BlockPos targetBlock = net.minecraft.core.BlockPos.containing(target);
            if (!player.serverLevel().hasChunkAt(targetBlock)) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.entropyinversion.unloaded_target"
                        ),
                        true
                );
                return;
            }

            StrikeManager.schedule(
                    player.serverLevel(),
                    target,
                    player.getUUID(),
                    packet.radius
            );
            player.getCooldowns().addCooldown(
                    dev.entropyinversion.EntropyInversionMod.ENTROPY_INVERSION_REQUESTOR.get(),
                    200
            );
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.entropyinversion.requested",
                            targetBlock.getX(),
                            targetBlock.getY(),
                            targetBlock.getZ()
                    ),
                    true
            );
        });
        context.setPacketHandled(true);
    }

    private static boolean hasRequestor(ItemStack stack) {
        return stack.is(dev.entropyinversion.EntropyInversionMod.ENTROPY_INVERSION_REQUESTOR.get());
    }
}
