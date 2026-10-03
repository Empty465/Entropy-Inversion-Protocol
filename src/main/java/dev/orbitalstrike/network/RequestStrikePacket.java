package dev.orbitalstrike.network;

import dev.orbitalstrike.item.OrbitalRequestorItem;
import dev.orbitalstrike.world.StrikeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class RequestStrikePacket {
    public static void encode(RequestStrikePacket packet, FriendlyByteBuf buffer) {
    }

    public static RequestStrikePacket decode(FriendlyByteBuf buffer) {
        return new RequestStrikePacket();
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

            if (player.getCooldowns().isOnCooldown(
                    dev.orbitalstrike.OrbitalStrikeMod.ORBITAL_REQUESTOR.get())) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.orbitalstrike.cooldown"
                        ),
                        true
                );
                return;
            }

            HitResult hit = player.pick(OrbitalRequestorItem.TARGETING_RANGE, 0.0F, false);
            if (!(hit instanceof BlockHitResult blockHit)
                    || hit.getType() != HitResult.Type.BLOCK
                    || !player.serverLevel().hasChunkAt(blockHit.getBlockPos())) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.orbitalstrike.no_target"
                        ),
                        true
                );
                return;
            }

            StrikeManager.schedule(player.serverLevel(), blockHit.getLocation(), player.getUUID());
            player.getCooldowns().addCooldown(
                    dev.orbitalstrike.OrbitalStrikeMod.ORBITAL_REQUESTOR.get(),
                    200
            );
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.orbitalstrike.requested",
                            blockHit.getBlockPos().getX(),
                            blockHit.getBlockPos().getY(),
                            blockHit.getBlockPos().getZ()
                    ),
                    true
            );
        });
        context.setPacketHandled(true);
    }

    private static boolean hasRequestor(ItemStack stack) {
        return stack.is(dev.orbitalstrike.OrbitalStrikeMod.ORBITAL_REQUESTOR.get());
    }
}
