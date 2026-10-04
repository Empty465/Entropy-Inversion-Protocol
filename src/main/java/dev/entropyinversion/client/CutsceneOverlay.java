package dev.entropyinversion.client;

import dev.entropyinversion.item.EntropyInversionRequestorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = "entropyinversion",
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class CutsceneOverlay {
    private static final float SPACE_SCENE_END = 0.36F;
    private static final float IMPACT_SCENE_START = 0.43F;
    private static final float IMPACT_BEAM_END = 0.82F;
    private static final int STAR_COUNT = 112;
    private static long startedAt;
    private static int duration;
    private static int strikeRadius;
    private static int soundStage;
    private static double targetX;
    private static double targetZ;
    private static boolean active;

    private CutsceneOverlay() {
    }

    public static void begin(double x, double z, int ticks, int radius) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        targetX = x;
        targetZ = z;
        duration = Math.max(1, ticks);
        strikeRadius = Math.max(
                EntropyInversionRequestorItem.MIN_STRIKE_RADIUS,
                Math.min(EntropyInversionRequestorItem.MAX_STRIKE_RADIUS, radius)
        );
        startedAt = minecraft.level.getGameTime();
        soundStage = 0;
        active = true;
        minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.END_PORTAL_SPAWN, 0.35F, 0.75F)
        );
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && active) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.level.getGameTime() - startedAt >= duration) {
                active = false;
                return;
            }

            float progress = (minecraft.level.getGameTime() - startedAt) / (float) duration;
            if (progress >= IMPACT_SCENE_START && soundStage == 0) {
                soundStage = 1;
                minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.ENDERMAN_TELEPORT, 0.7F, 0.8F)
                );
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

        if (progress < IMPACT_SCENE_START) {
            float sceneAlpha = progress <= SPACE_SCENE_END
                    ? 1.0F
                    : 1.0F - (progress - SPACE_SCENE_END)
                            / (IMPACT_SCENE_START - SPACE_SCENE_END);
            drawSpaceScene(event, width, height, centerX, progress, sceneAlpha);
            return;
        }

        drawImpactScene(event, width, height, centerX, centerY, progress);
    }

    private static void drawSpaceScene(
            RenderGuiOverlayEvent.Post event,
            int width,
            int height,
            int centerX,
            float progress,
            float sceneAlpha
    ) {
        int opaqueAlpha = (int) (255.0F * sceneAlpha);
        event.getGuiGraphics().fill(
                0,
                0,
                width,
                height,
                (opaqueAlpha << 24) | 0x00030A1C
        );
        drawStars(event, width, height, sceneAlpha);

        int planetRadius = Math.max(24, Math.min(width / 4, height / 3));
        int planetCenterY = height * 3 / 4;
        drawPlanet(event, centerX, planetCenterY, planetRadius, sceneAlpha);

        if (progress >= 0.21F) {
            float beamProgress = (progress - 0.21F) / (SPACE_SCENE_END - 0.21F);
            int beamBottom = planetCenterY - planetRadius + 4;
            int beamTop = beamBottom - (int) (height * 0.56F * beamProgress);
            int beamAlpha = (int) (sceneAlpha * Math.min(1.0F, beamProgress * 2.0F) * 210.0F);
            event.getGuiGraphics().fill(
                    centerX - 5,
                    Math.max(0, beamTop),
                    centerX + 6,
                    beamBottom,
                    (beamAlpha << 24) | 0x00FF517A
            );
            event.getGuiGraphics().fill(
                    centerX - 1,
                    Math.max(0, beamTop),
                    centerX + 2,
                    beamBottom,
                    (beamAlpha << 24) | 0x00FFF1D6
            );
        }

        drawSceneLabels(
                event,
                width,
                height,
                sceneAlpha,
                progress < SPACE_SCENE_END
                        ? "gui.entropyinversion.cutscene.space"
                        : "gui.entropyinversion.cutscene.transition"
        );
    }

    private static void drawStars(
            RenderGuiOverlayEvent.Post event,
            int width,
            int height,
            float alpha
    ) {
        for (int i = 0; i < STAR_COUNT; i++) {
            int x = Math.floorMod(i * 7919 + 37, width);
            int y = Math.floorMod(i * 3571 + 19, height);
            int size = i % 13 == 0 ? 2 : 1;
            int brightness = 150 + Math.floorMod(i * 47, 106);
            int color = (int) (brightness * alpha);
            int argb = (color << 24) | (0x00B9DFFF + (i % 3) * 0x000A0710);
            event.getGuiGraphics().fill(x, y, x + size, y + size, argb);
            if (size == 2) {
                event.getGuiGraphics().fill(x - 2, y, x + 4, y + 1, (color / 2 << 24) | 0x00C8E8FF);
                event.getGuiGraphics().fill(x, y - 2, x + 1, y + 4, (color / 2 << 24) | 0x00C8E8FF);
            }
        }
    }

    private static void drawPlanet(
            RenderGuiOverlayEvent.Post event,
            int centerX,
            int centerY,
            int radius,
            float alpha
    ) {
        for (int y = -radius; y <= radius; y++) {
            double normalizedY = y / (double) radius;
            int halfWidth = (int) (radius * Math.sqrt(1.0D - normalizedY * normalizedY));
            int shade = Math.max(0, Math.min(38, (int) (38.0D * (1.0D - normalizedY))));
            int red = 14 + shade / 3;
            int green = 66 + shade;
            int blue = 112 + shade * 2;
            int rowAlpha = (int) (alpha * Math.max(0.0F, Math.min(1.0F, (radius - y) / (radius * 0.18F))));
            int color = (rowAlpha << 24) | (red << 16) | (green << 8) | blue;
            event.getGuiGraphics().fill(
                    centerX - halfWidth,
                    centerY + y,
                    centerX + halfWidth + 1,
                    centerY + y + 1,
                    color
            );
        }

        int atmosphereAlpha = (int) (alpha * 120.0F);
        drawEllipse(
                event,
                centerX,
                centerY,
                radius + 3,
                Math.max(3, radius / 5),
                (atmosphereAlpha << 24) | 0x003DAFFF
        );
    }

    private static void drawEllipse(
            RenderGuiOverlayEvent.Post event,
            int centerX,
            int centerY,
            int radiusX,
            int radiusY,
            int color
    ) {
        for (int y = -radiusY; y <= radiusY; y++) {
            double normalizedY = y / (double) radiusY;
            int x = (int) (radiusX * Math.sqrt(1.0D - normalizedY * normalizedY));
            event.getGuiGraphics().fill(
                    centerX - x,
                    centerY + y,
                    centerX - x + 2,
                    centerY + y + 2,
                    color
            );
            event.getGuiGraphics().fill(
                    centerX + x - 1,
                    centerY + y,
                    centerX + x + 1,
                    centerY + y + 2,
                    color
            );
        }
    }

    private static void drawImpactScene(
            RenderGuiOverlayEvent.Post event,
            int width,
            int height,
            int centerX,
            int centerY,
            float progress
    ) {
        event.getGuiGraphics().fill(0, 0, width, height, 0x40030A18);

        float beamProgress = Math.max(
                0.0F,
                Math.min(1.0F, (progress - IMPACT_SCENE_START) / (IMPACT_BEAM_END - IMPACT_SCENE_START))
        );
        int beamBottom = centerY + (int) ((height - centerY) * beamProgress);
        event.getGuiGraphics().fill(centerX - 9, 0, centerX + 10, beamBottom, 0x99FF4265);
        event.getGuiGraphics().fill(centerX - 3, 0, centerX + 4, beamBottom, 0xDDFFF4E4);
        event.getGuiGraphics().fill(0, centerY - 1, width, centerY + 2, 0x4437BFFF);

        if (progress >= IMPACT_BEAM_END) {
            float impactProgress = Math.min(
                    1.0F,
                    (progress - IMPACT_BEAM_END) / (1.0F - IMPACT_BEAM_END)
            );
            int maxRadius = Math.max(8, Math.min(width, height) * 2 / 5
                    * strikeRadius / EntropyInversionRequestorItem.MAX_STRIKE_RADIUS);
            int ringRadius = Math.max(2, (int) (maxRadius * impactProgress));
            int ringAlpha = (int) (210.0F * (1.0F - impactProgress));
            drawEllipse(
                    event,
                    centerX,
                    centerY,
                    ringRadius,
                    Math.max(2, ringRadius / 2),
                    (ringAlpha << 24) | 0x00FF9B75
            );
            event.getGuiGraphics().fill(
                    0,
                    0,
                    width,
                    height,
                    ((int) (105.0F * (1.0F - impactProgress)) << 24) | 0x00E7F5FF
            );
        }

        drawSceneLabels(
                event,
                width,
                height,
                1.0F,
                progress < IMPACT_BEAM_END
                        ? "gui.entropyinversion.cutscene.impact"
                        : "gui.entropyinversion.cutscene.detonation"
        );
    }

    private static void drawSceneLabels(
            RenderGuiOverlayEvent.Post event,
            int width,
            int height,
            float alpha,
            String stageKey
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        int textAlpha = (int) (255.0F * Math.max(0.0F, Math.min(1.0F, alpha)));
        int titleColor = (textAlpha << 24) | 0x00E7F4FF;
        int detailColor = (textAlpha << 24) | 0x009EDBFF;
        int centerX = width / 2;

        event.getGuiGraphics().drawCenteredString(
                minecraft.font,
                Component.translatable(stageKey),
                centerX,
                height / 5,
                titleColor
        );
        event.getGuiGraphics().drawCenteredString(
                minecraft.font,
                Component.translatable(
                        "gui.entropyinversion.cutscene.target",
                        (int) Math.floor(targetX),
                        (int) Math.floor(targetZ)
                ),
                centerX,
                height / 5 + 14,
                detailColor
        );
    }

}
