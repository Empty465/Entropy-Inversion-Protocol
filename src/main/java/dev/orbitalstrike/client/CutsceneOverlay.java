package dev.orbitalstrike.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = "orbitalstrike",
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class CutsceneOverlay {
    private static long startedAt;
    private static int duration;
    private static double targetX;
    private static double targetZ;
    private static boolean active;

    private CutsceneOverlay() {
    }

    public static void begin(double x, double z, int ticks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        targetX = x;
        targetZ = z;
        duration = Math.max(1, ticks);
        startedAt = minecraft.level.getGameTime();
        active = true;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && active) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.level.getGameTime() - startedAt >= duration) {
                active = false;
            }
        }
    }

    @SubscribeEvent
    public static void onGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (!active || event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            active = false;
            return;
        }

        float progress = Math.min(
                1.0F,
                (minecraft.level.getGameTime() - startedAt + event.getPartialTick()) / duration
        );
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        int centerX = width / 2;
        int centerY = height / 2;

        event.getGuiGraphics().fill(0, 0, width, height, 0x55030A18);
        event.getGuiGraphics().drawCenteredString(
                minecraft.font,
                Component.translatable("gui.orbitalstrike.cutscene.title"),
                centerX,
                height / 5,
                0xFFE7F4FF
        );
        event.getGuiGraphics().drawCenteredString(
                minecraft.font,
                Component.translatable(
                        "gui.orbitalstrike.cutscene.target",
                        (int) Math.floor(targetX),
                        (int) Math.floor(targetZ)
                ),
                centerX,
                height / 5 + 14,
                0xFF9EDBFF
        );

        if (progress >= 0.25F && progress < 0.9F) {
            float beamProgress = Math.min(1.0F, (progress - 0.25F) / 0.55F);
            int beamBottom = Math.max(0, (int) (height * beamProgress));
            event.getGuiGraphics().fill(centerX - 7, 0, centerX + 8, beamBottom, 0x99FF4B69);
            event.getGuiGraphics().fill(centerX - 2, 0, centerX + 3, beamBottom, 0xFFFFF4E8);
            event.getGuiGraphics().fill(0, centerY - 1, width, centerY + 1, 0x6637BFFF);
        }

        if (progress >= 0.82F) {
            int alpha = Math.max(0, (int) (160.0F * (1.0F - progress) / 0.18F));
            event.getGuiGraphics().fill(0, 0, width, height, (alpha << 24) | 0x00E7F5FF);
        }
    }
}
