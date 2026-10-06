package dev.entropyinversion.network;

import dev.entropyinversion.item.AttackMode;
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
    private final int attackMode;
    private final InteractionHand hand;

    public RequestStrikePacket(int radius, AttackMode attackMode, InteractionHand hand) {
        this(radius, attackMode.ordinal(), hand);
    }

    private RequestStrikePacket(int radius, int attackMode, InteractionHand hand) {
        this.radius = radius;
        this.attackMode = attackMode;
        this.hand = hand;
    }

    public static void encode(RequestStrikePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.radius);
        buffer.writeVarInt(packet.attackMode);
        buffer.writeEnum(packet.hand);
    }

    public static RequestStrikePacket decode(FriendlyByteBuf buffer) {
        int radius = buffer.readVarInt();
        int attackMode = buffer.readVarInt();
        InteractionHand hand = buffer.readEnum(InteractionHand.class);
        return new RequestStrikePacket(radius, attackMode, hand);
    }

    public static void handle(
            RequestStrikePacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !hasRequestor(player.getItemInHand(packet.hand))) {
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

            AttackMode requestedMode = AttackMode.fromOrdinal(packet.attackMode);
            ItemStack requestor = player.getItemInHand(packet.hand);
            boolean modeMatchesHeldRequestor = requestedMode != null
                    && requestedMode == AttackMode.fromStack(requestor);
            if (!modeMatchesHeldRequestor) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.entropyinversion.invalid_attack_mode"
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
                    packet.radius,
                    requestedMode
            );
            player.getCooldowns().addCooldown(
                    dev.entropyinversion.EntropyInversionMod.ENTROPY_INVERSION_REQUESTOR.get(),
                    200
            );
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.entropyinversion.attack_requested",
                            net.minecraft.network.chat.Component.translatable(requestedMode.getNameKey()),
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
