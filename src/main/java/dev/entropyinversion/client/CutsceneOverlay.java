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
    private static final float SPACE_SCENE_END = 0.36F;
    private static final float IMPACT_SCENE_START = 0.43F;
    private static final float IMPACT_BEAM_END = 0.82F;
    private static final int STAR_COUNT = 112;
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
        if (progress < IMPACT_SCENE_START) {
            float sceneAlpha = progress <= SPACE_SCENE_END
                    ? 1.0F
                    : 1.0F - (progress - SPACE_SCENE_END)
                            / (IMPACT_SCENE_START - SPACE_SCENE_END);
            drawSpaceScene(event, width, height, centerX, progress, sceneAlpha);
            return;
        }

        drawImpactScene(event, width, height, centerX, progress);
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
                0.38F,
                0.48F
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
                0.2F,
                0.3F
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
                0.045F,
                0.09F
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
        drawStars(event, width, height, sceneAlpha);

        int planetRadius = Math.max(24, Math.min(width / 4, height / 3));
        int planetCenterY = height * 3 / 4;
        drawPlanet(event, centerX, planetCenterY, planetRadius, sceneAlpha);

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
