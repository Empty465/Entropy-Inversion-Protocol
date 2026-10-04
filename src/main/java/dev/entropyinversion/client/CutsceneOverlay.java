package dev.entropyinversion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.entropyinversion.item.EntropyInversionRequestorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
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
    private static final float SPACE_SCENE_END = 0.43F;
    private static final float IMPACT_SCENE_START = 0.51F;
    private static final float IMPACT_BEAM_END = 0.72F;
    private static final int STAR_COUNT = 176;
    private static final int SCANLINE_COUNT = 42;
    private static final int SHOCKWAVE_PARTICLE_INTERVAL = 2;
    private static final int SHOCKWAVE_PARTICLE_SEGMENTS = 48;
    private static final double SHOCKWAVE_PARTICLE_VIEW_DISTANCE_SQUARED = 96.0D * 96.0D;
    private static long startedAt;
    private static long lastShockwaveParticleTick = Long.MIN_VALUE;
    private static int duration;
    private static int strikeRadius;
    private static int soundStage;
    private static double targetX;
    private static double targetY;
    private static double targetZ;
    private static boolean active;

    private CutsceneOverlay() {
    }

    public static void begin(double x, double y, double z, int ticks, int radius) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        targetX = x;
        targetY = y;
        targetZ = z;
        duration = Math.max(1, ticks);
        strikeRadius = Math.max(
                EntropyInversionRequestorItem.MIN_STRIKE_RADIUS,
                Math.min(EntropyInversionRequestorItem.MAX_STRIKE_RADIUS, radius)
        );
        startedAt = minecraft.level.getGameTime();
        lastShockwaveParticleTick = Long.MIN_VALUE;
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
            spawnShockwaveParticles(minecraft, progress);
            if (progress >= 0.18F && soundStage == 0) {
                soundStage = 1;
                minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.BEACON_AMBIENT, 0.5F, 0.7F)
                );
            }
            if (progress >= 0.34F && soundStage == 1) {
                soundStage = 2;
                minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 0.75F, 0.9F)
                );
            }
            if (progress >= IMPACT_SCENE_START && soundStage == 2) {
                soundStage = 3;
                minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.ENDERMAN_TELEPORT, 0.7F, 0.8F)
                );
            }
            if (progress >= IMPACT_BEAM_END && soundStage == 3) {
                soundStage = 4;
                minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.GENERIC_EXPLODE, 0.85F, 0.65F)
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
        } else {
            drawImpactScene(event, width, height, centerX, progress);
        }
        drawCinematicFrame(event, width, height, progress);
        drawTargetReticle(event, width, height, progress);
        drawCountdown(event, centerX, centerY, progress);
        drawTransitionFlash(event, width, height, progress);
    }

    @SubscribeEvent
    public static void onWorldRender(RenderLevelStageEvent event) {
        if (!active || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            active = false;
            return;
        }

        float progress = (float) (
                (minecraft.level.getGameTime() - startedAt + event.getPartialTick()) / duration
        );
        if (progress < IMPACT_SCENE_START) {
            return;
        }

        BlockPos targetBlock = BlockPos.containing(targetX, targetY, targetZ);
        int beamHeight = Math.min(
                BeaconRenderer.MAX_RENDER_Y,
                minecraft.level.getMaxBuildHeight() - targetBlock.getY()
        );
        if (beamHeight <= 0) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(
                targetBlock.getX() - camera.x,
                targetBlock.getY() - camera.y,
                targetBlock.getZ() - camera.z
        );

        float beamPulse = 0.94F + 0.06F
                * (float) Math.sin((minecraft.level.getGameTime() + event.getPartialTick()) * 0.65D);
        BeaconRenderer.renderBeaconBeam(
                poseStack,
                minecraft.renderBuffers().bufferSource(),
                BeaconRenderer.BEAM_LOCATION,
                event.getPartialTick(),
                1.0F,
                minecraft.level.getGameTime(),
                0,
                beamHeight,
                new float[]{1.0F, 0.025F, 0.08F},
                0.45F * beamPulse,
                0.54F * beamPulse
        );
        BeaconRenderer.renderBeaconBeam(
                poseStack,
                minecraft.renderBuffers().bufferSource(),
                BeaconRenderer.BEAM_LOCATION,
                event.getPartialTick(),
                1.0F,
                minecraft.level.getGameTime(),
                0,
                beamHeight,
                new float[]{1.0F, 0.16F, 0.32F},
                0.23F * beamPulse,
                0.34F * beamPulse
        );
        BeaconRenderer.renderBeaconBeam(
                poseStack,
                minecraft.renderBuffers().bufferSource(),
                BeaconRenderer.BEAM_LOCATION,
                event.getPartialTick(),
                1.0F,
                minecraft.level.getGameTime(),
                0,
                beamHeight,
                new float[]{1.0F, 0.88F, 0.68F},
                0.06F * beamPulse,
                0.12F * beamPulse
        );
        minecraft.renderBuffers().bufferSource().endBatch(
                RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, false)
        );
        minecraft.renderBuffers().bufferSource().endBatch(
                RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true)
        );

        if (progress >= IMPACT_BEAM_END) {
            float shockwaveProgress = Math.min(
                    1.0F,
                    (progress - IMPACT_BEAM_END) / (1.0F - IMPACT_BEAM_END)
            );
            double shockwaveRadius = Math.max(1.0D, strikeRadius * shockwaveProgress);
            VertexConsumer lines = minecraft.renderBuffers()
                    .bufferSource()
                    .getBuffer(RenderType.lines());
            double localX = targetX - targetBlock.getX();
            double localZ = targetZ - targetBlock.getZ();
            double localY = targetY - targetBlock.getY() + 0.12D;
            float alpha = (float) Math.pow(1.0F - shockwaveProgress, 0.45F);

            for (int layer = 0; layer < 5; layer++) {
                double trailOffset = strikeRadius * (0.012D + layer * 0.009D);
                double layerRadius = Math.max(0.5D, shockwaveRadius - trailOffset);
                float layerAlpha = alpha * (1.0F - layer * 0.12F);
                if (layer == 0) {
                    drawWorldRing(
                            lines,
                            poseStack,
                            localX,
                            localZ,
                            localY,
                            layerRadius,
                            1.0F,
                            0.95F,
                            0.72F,
                            layerAlpha
                    );
                } else if (layer <= 2) {
                    drawWorldRing(
                            lines,
                            poseStack,
                            localX,
                            localZ,
                            localY + layer * 0.035D,
                            layerRadius,
                            1.0F,
                            0.34F,
                            0.06F,
                            layerAlpha
                    );
                } else {
                    drawWorldRing(
                            lines,
                            poseStack,
                            localX,
                            localZ,
                            localY + layer * 0.035D,
                            layerRadius,
                            0.95F,
                            0.08F,
                            0.025F,
                            layerAlpha
                    );
                }
            }

            drawShockwaveSpokes(
                    lines,
                    poseStack,
                    localX,
                    localZ,
                    localY,
                    shockwaveRadius,
                    alpha
            );
            minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
        }
        poseStack.popPose();
    }

    private static void spawnShockwaveParticles(Minecraft minecraft, float progress) {
        if (progress < IMPACT_BEAM_END
                || (lastShockwaveParticleTick != Long.MIN_VALUE
                && minecraft.level.getGameTime() - lastShockwaveParticleTick
                < SHOCKWAVE_PARTICLE_INTERVAL)) {
            return;
        }
        lastShockwaveParticleTick = minecraft.level.getGameTime();

        float shockwaveProgress = Math.min(
                1.0F,
                (progress - IMPACT_BEAM_END) / (1.0F - IMPACT_BEAM_END)
        );
        double radius = Math.max(1.0D, strikeRadius * shockwaveProgress);
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        for (int i = 0; i < SHOCKWAVE_PARTICLE_SEGMENTS; i++) {
            double angle = Math.PI * 2.0D * i / SHOCKWAVE_PARTICLE_SEGMENTS;
            double x = targetX + Math.cos(angle) * radius;
            double z = targetZ + Math.sin(angle) * radius;
            double dx = Math.cos(angle) * 0.28D;
            double dz = Math.sin(angle) * 0.28D;
            double distanceX = x - camera.x;
            double distanceY = targetY - camera.y;
            double distanceZ = z - camera.z;
            if (distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ
                    > SHOCKWAVE_PARTICLE_VIEW_DISTANCE_SQUARED) {
                continue;
            }

            minecraft.level.addParticle(
                    ParticleTypes.FLAME,
                    x,
                    targetY + 0.15D,
                    z,
                    dx,
                    0.08D,
                    dz
            );
            if (i % 4 == 0) {
                minecraft.level.addParticle(
                        ParticleTypes.END_ROD,
                        x,
                        targetY + 0.3D,
                        z,
                        dx * 0.65D,
                        0.16D,
                        dz * 0.65D
                );
            }
        }
    }

    private static void drawShockwaveSpokes(
            VertexConsumer lines,
            PoseStack poseStack,
            double centerX,
            double centerZ,
            double y,
            double radius,
            float alpha
    ) {
        for (int i = 0; i < 32; i++) {
            double angle = Math.PI * 2.0D * i / 32.0D;
            double innerRadius = radius * 0.91D;
            double outerRadius = radius * (i % 4 == 0 ? 1.035D : 0.985D);
            drawWorldLine(
                    lines,
                    poseStack,
                    centerX + Math.cos(angle) * innerRadius,
                    y + 0.04D,
                    centerZ + Math.sin(angle) * innerRadius,
                    centerX + Math.cos(angle) * outerRadius,
                    y + 0.04D,
                    centerZ + Math.sin(angle) * outerRadius,
                    1.0F,
                    i % 4 == 0 ? 0.76F : 0.24F,
                    0.08F,
                    alpha * (i % 4 == 0 ? 0.95F : 0.55F)
            );
        }
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
        drawStars(event, width, height, sceneAlpha, progress);

        int planetRadius = Math.max(24, Math.min(width / 4, height / 3));
        int planetCenterY = height * 3 / 4;
        drawPlanet(event, centerX, planetCenterY, planetRadius, sceneAlpha);
        drawEllipse(
                event,
                centerX,
                planetCenterY,
                planetRadius + 35,
                Math.max(8, planetRadius / 4),
                ((int) (sceneAlpha * 110.0F) << 24) | 0x00FF5278
        );
        drawEllipse(
                event,
                centerX,
                planetCenterY,
                planetRadius + 52,
                Math.max(10, planetRadius / 3),
                ((int) (sceneAlpha * 58.0F) << 24) | 0x0047BFFF
        );
        drawScanLines(event, width, height, sceneAlpha);
        drawCosmicVortex(event, centerX, planetCenterY, planetRadius, progress, sceneAlpha);
        drawSceneLabels(
                event,
                width,
                height,
                sceneAlpha,
                getStageKey(progress)
        );
    }

    private static String getStageKey(float progress) {
        if (progress < 0.18F) {
            return "gui.entropyinversion.cutscene.space";
        }
        if (progress < 0.34F) {
            return "gui.entropyinversion.cutscene.charge";
        }
        if (progress < IMPACT_SCENE_START) {
            return "gui.entropyinversion.cutscene.lock";
        }
        if (progress < IMPACT_BEAM_END) {
            return "gui.entropyinversion.cutscene.impact";
        }
        return "gui.entropyinversion.cutscene.detonation";
    }

    private static void drawScanLines(
            RenderGuiOverlayEvent.Post event,
            int width,
            int height,
            float alpha
    ) {
        int lineAlpha = (int) (alpha * 20.0F);
        for (int i = 0; i < SCANLINE_COUNT; i++) {
            int y = i * height / SCANLINE_COUNT;
            event.getGuiGraphics().fill(
                    0,
                    y,
                    width,
                    y + 1,
                    (lineAlpha << 24) | 0x0039BDEB
            );
        }
    }

    private static void drawCosmicVortex(
            RenderGuiOverlayEvent.Post event,
            int centerX,
            int centerY,
            int planetRadius,
            float progress,
            float alpha
    ) {
        for (int i = 0; i < 5; i++) {
            double phase = progress * (Math.PI * 5.0D) + i * Math.PI * 0.4D;
            int shiftX = (int) (Math.cos(phase) * 13.0D);
            int shiftY = (int) (Math.sin(phase) * 7.0D);
            int orbitX = planetRadius + 25 + i * 9;
            int orbitY = Math.max(8, planetRadius / 5 + i * 4);
            int colorAlpha = (int) (alpha * (92.0F - i * 10.0F));
            drawEllipse(
                    event,
                    centerX + shiftX,
                    centerY + shiftY,
                    orbitX,
                    orbitY,
                    (colorAlpha << 24) | (i % 2 == 0 ? 0x00FF4E78 : 0x0047CFFF)
            );
        }

        int flareSize = 3 + (int) ((Math.sin(progress * Math.PI * 16.0D) + 1.0D) * 3.0D);
        int flareAlpha = (int) (alpha * 210.0F);
        event.getGuiGraphics().fill(
                centerX - flareSize,
                centerY - flareSize,
                centerX + flareSize + 1,
                centerY + flareSize + 1,
                (flareAlpha << 24) | 0x00FFF0CB
        );
        event.getGuiGraphics().fill(
                centerX - flareSize * 4,
                centerY - 1,
                centerX + flareSize * 4 + 1,
                centerY + 2,
                ((flareAlpha / 2) << 24) | 0x00FF738D
        );
    }

    private static void drawStars(
            RenderGuiOverlayEvent.Post event,
            int width,
            int height,
            float alpha,
            float progress
    ) {
        int driftX = (int) (progress * width * 0.035F);
        int driftY = (int) (progress * height * 0.012F);
        for (int i = 0; i < STAR_COUNT; i++) {
            int x = Math.floorMod(i * 7919 + 37 + driftX * (1 + i % 3), width);
            int y = Math.floorMod(i * 3571 + 19 + driftY * (1 + i % 2), height);
            int size = i % 13 == 0 ? 2 : 1;
            int brightness = 150 + Math.floorMod(i * 47, 106);
            int color = (int) (brightness * alpha);
            int argb = (color << 24) | (0x00B9DFFF + (i % 3) * 0x000A0710);
            event.getGuiGraphics().fill(x, y, x + size, y + size, argb);
            if (progress > 0.2F && i % 3 == 0) {
                int trailLength = 4 + (int) (progress * 42.0F * (1 + i % 3));
                event.getGuiGraphics().fill(
                        x - trailLength,
                        y,
                        x,
                        y + 1,
                        ((color / 2) << 24) | 0x004FCBFF
                );
            }
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

    private static void drawWorldRing(
            VertexConsumer lines,
            PoseStack poseStack,
            double centerX,
            double centerZ,
            double y,
            double radius,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        for (int i = 0; i < 128; i++) {
            double angle0 = Math.PI * 2.0D * i / 128.0D;
            double angle1 = Math.PI * 2.0D * (i + 1) / 128.0D;
            drawWorldLine(
                    lines,
                    poseStack,
                    centerX + Math.cos(angle0) * radius,
                    y,
                    centerZ + Math.sin(angle0) * radius,
                    centerX + Math.cos(angle1) * radius,
                    y,
                    centerZ + Math.sin(angle1) * radius,
                    red,
                    green,
                    blue,
                    alpha
            );
        }
    }

    private static void drawWorldLine(
            VertexConsumer lines,
            PoseStack poseStack,
            double x0,
            double y0,
            double z0,
            double x1,
            double y1,
            double z1,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        PoseStack.Pose pose = poseStack.last();
        lines.vertex(pose.pose(), (float) x0, (float) y0, (float) z0)
                .color(red, green, blue, alpha)
                .normal(pose.normal(), 0.0F, 1.0F, 0.0F)
                .endVertex();
        lines.vertex(pose.pose(), (float) x1, (float) y1, (float) z1)
                .color(red, green, blue, alpha)
                .normal(pose.normal(), 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    private static void drawImpactScene(
            RenderGuiOverlayEvent.Post event,
            int width,
            int height,
            int centerX,
            float progress
    ) {
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

    private static void drawTargetReticle(
            RenderGuiOverlayEvent.Post event,
            int width,
            int height,
            float progress
    ) {
        float lockProgress = Math.max(
                0.0F,
                Math.min(1.0F, (progress - 0.2F) / (IMPACT_SCENE_START - 0.2F))
        );
        int centerX = width / 2;
        int centerY = height / 2;
        int halfSize = (int) (52.0F - 34.0F * lockProgress);
        int color = progress < IMPACT_SCENE_START ? 0xFF60E5FF : 0xFFFF667A;
        drawReticleCorner(event, centerX - halfSize, centerY - halfSize, 7, 5, color, -1, -1);
        drawReticleCorner(event, centerX + halfSize, centerY - halfSize, 7, 5, color, 1, -1);
        drawReticleCorner(event, centerX - halfSize, centerY + halfSize, 7, 5, color, -1, 1);
        drawReticleCorner(event, centerX + halfSize, centerY + halfSize, 7, 5, color, 1, 1);

        int markerSize = 3 + (int) (lockProgress * 3.0F);
        event.getGuiGraphics().fill(
                centerX - markerSize,
                centerY - markerSize,
                centerX + markerSize + 1,
                centerY + markerSize + 1,
                0xD9FF546B
        );
        event.getGuiGraphics().fill(
                centerX - 1,
                centerY - halfSize - 13,
                centerX + 2,
                centerY - halfSize - 5,
                color
        );
        event.getGuiGraphics().fill(
                centerX - 1,
                centerY + halfSize + 5,
                centerX + 2,
                centerY + halfSize + 13,
                color
        );
        event.getGuiGraphics().fill(
                centerX - halfSize - 13,
                centerY - 1,
                centerX - halfSize - 5,
                centerY + 2,
                color
        );
        event.getGuiGraphics().fill(
                centerX + halfSize + 5,
                centerY - 1,
                centerX + halfSize + 13,
                centerY + 2,
                color
        );
    }

    private static void drawReticleCorner(
            RenderGuiOverlayEvent.Post event,
            int x,
            int y,
            int arm,
            int gap,
            int color,
            int directionX,
            int directionY
    ) {
        int horizontalStart = directionX < 0 ? x - gap - arm : x + gap;
        int horizontalEnd = directionX < 0 ? x - gap : x + gap + arm;
        int verticalStart = directionY < 0 ? y - gap - arm : y + gap;
        int verticalEnd = directionY < 0 ? y - gap : y + gap + arm;
        event.getGuiGraphics().fill(horizontalStart, y - 1, horizontalEnd, y + 2, color);
        event.getGuiGraphics().fill(x - 1, verticalStart, x + 2, verticalEnd, color);
    }

    private static void drawCountdown(
            RenderGuiOverlayEvent.Post event,
            int centerX,
            int centerY,
            float progress
    ) {
        if (progress >= IMPACT_BEAM_END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int seconds = Math.max(0, (int) Math.ceil((1.0F - progress) * duration / 20.0F));
        String countdown = String.format(java.util.Locale.ROOT, "00:%02d", seconds);
        int color = progress < IMPACT_SCENE_START ? 0xFFF2FBFF : 0xFFFFE7D4;
        if (progress < IMPACT_SCENE_START) {
            drawScaledCenteredString(
                    event,
                    minecraft,
                    countdown,
                    centerX,
                    centerY + 42,
                    3.2F,
                    color
            );
        } else {
            event.getGuiGraphics().fill(centerX - 39, 32, centerX + 39, 64, 0xB40A111B);
            drawScaledCenteredString(event, minecraft, countdown, centerX, 42, 1.7F, color);
        }
    }

    private static void drawScaledCenteredString(
            RenderGuiOverlayEvent.Post event,
            Minecraft minecraft,
            String text,
            int centerX,
            int y,
            float scale,
            int color
    ) {
        event.getGuiGraphics().pose().pushPose();
        event.getGuiGraphics().pose().translate(centerX, y, 0.0F);
        event.getGuiGraphics().pose().scale(scale, scale, 1.0F);
        event.getGuiGraphics().drawCenteredString(minecraft.font, text, 0, 0, color);
        event.getGuiGraphics().pose().popPose();
    }

    private static void drawTransitionFlash(
            RenderGuiOverlayEvent.Post event,
            int width,
            int height,
            float progress
    ) {
        float lockFlash = Math.max(
                0.0F,
                1.0F - Math.abs(progress - IMPACT_SCENE_START) / 0.018F
        );
        float impactFlash = Math.max(
                0.0F,
                1.0F - Math.abs(progress - IMPACT_BEAM_END) / 0.012F
        );
        int alpha = (int) Math.max(lockFlash * 74.0F, impactFlash * 112.0F);
        if (alpha > 0) {
            event.getGuiGraphics().fill(
                    0,
                    0,
                    width,
                    height,
                    (alpha << 24) | 0x00FFD9C4
            );
        }
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

    private static void drawCinematicFrame(
            RenderGuiOverlayEvent.Post event,
            int width,
            int height,
            float progress
    ) {
        int barHeight = Math.max(8, height / 18);
        event.getGuiGraphics().fill(0, 0, width, barHeight, 0xD9000309);
        event.getGuiGraphics().fill(0, height - barHeight, width, height, 0xD9000309);

        int meterWidth = Math.max(80, width / 3);
        int meterLeft = (width - meterWidth) / 2;
        int meterY = height - barHeight + 4;
        event.getGuiGraphics().fill(
                meterLeft,
                meterY,
                meterLeft + meterWidth,
                meterY + 2,
                0xFF263643
        );
        int progressWidth = (int) (meterWidth * Math.max(0.0F, Math.min(1.0F, progress)));
        if (progressWidth > 0) {
            event.getGuiGraphics().fill(
                    meterLeft,
                    meterY,
                    meterLeft + progressWidth,
                    meterY + 2,
                    progress < IMPACT_SCENE_START ? 0xFF56CFFF : 0xFFFF526C
            );
        }
        event.getGuiGraphics().fill(
                meterLeft + progressWidth - 1,
                meterY - 1,
                meterLeft + progressWidth + 2,
                meterY + 3,
                0xFFFFF0D0
        );
        for (int i = 1; i < 10; i++) {
            int tickX = meterLeft + meterWidth * i / 10;
            event.getGuiGraphics().fill(tickX, meterY - 1, tickX + 1, meterY + 3, 0xAA9ABAC8);
        }
        event.getGuiGraphics().drawString(
                Minecraft.getInstance().font,
                Component.literal("E.I.P. // REALITY BREACH PROTOCOL"),
                12,
                barHeight / 2 - 4,
                0xFF9CCBE0,
                false
        );
        String phaseCode = progress < IMPACT_SCENE_START ? "ORBITAL LOCK" : "IMPACT SEQUENCE";
        int codeWidth = Minecraft.getInstance().font.width(phaseCode);
        event.getGuiGraphics().drawString(
                Minecraft.getInstance().font,
                phaseCode,
                width - codeWidth - 12,
                barHeight / 2 - 4,
                progress < IMPACT_SCENE_START ? 0xFF63D9FF : 0xFFFF6579,
                false
        );
    }

}
