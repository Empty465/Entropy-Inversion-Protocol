package dev.orbitalstrike.item;

import dev.orbitalstrike.world.StrikeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class OrbitalRequestorItem extends Item {
    private static final double TARGETING_RANGE = 512.0D;

    public OrbitalRequestorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("item.orbitalstrike.orbital_requestor.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.orbitalstrike.orbital_requestor.radius")
                .withStyle(ChatFormatting.RED));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        HitResult result = player.pick(TARGETING_RANGE, 0.0F, false);
        if (!(result instanceof BlockHitResult blockHit)
                || result.getType() != HitResult.Type.BLOCK
                || !(player instanceof ServerPlayer serverPlayer)
                || !(level instanceof ServerLevel serverLevel)) {
            player.displayClientMessage(
                    Component.translatable("message.orbitalstrike.no_target")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        BlockPos target = blockHit.getBlockPos();
        Vec3 hitLocation = blockHit.getLocation();
        if (!serverLevel.hasChunkAt(target)) {
            player.displayClientMessage(
                    Component.translatable("message.orbitalstrike.unloaded_target")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        if (player.getCooldowns().isOnCooldown(this)) {
            player.displayClientMessage(
                    Component.translatable("message.orbitalstrike.cooldown")
                            .withStyle(ChatFormatting.YELLOW),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }

        StrikeManager.schedule(serverLevel, hitLocation, serverPlayer.getUUID());
        player.getCooldowns().addCooldown(this, 200);
        player.displayClientMessage(
                Component.translatable(
                        "message.orbitalstrike.requested",
                        target.getX(),
                        target.getY(),
                        target.getZ()
                ).withStyle(ChatFormatting.GOLD),
                true
        );
        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
