package dev.orbitalstrike.item;

import dev.orbitalstrike.client.TargetingMode;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.List;

public final class OrbitalRequestorItem extends Item {
    public static final double TARGETING_RANGE = 512.0D;

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
        tooltip.add(Component.translatable("item.orbitalstrike.orbital_requestor.confirm")
                .withStyle(ChatFormatting.YELLOW));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> TargetingMode::start);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
