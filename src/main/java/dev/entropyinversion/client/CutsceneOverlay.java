package dev.entropyinversion.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.entropyinversion.item.EntropyInversionRequestorItem;
import dev.entropyinversion.item.AttackMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    private static final ResourceLocation ORBITAL_ARRAY_TEXTURE =
            new ResourceLocation("entropyinversion", "textures/gui/orbital_array.png");
    private static final float SPACE_SCENE_END = 0.43F;
    private static final float CHARGE_SCENE_START = 0.32F;
    private static final float LOCK_SCENE_START = 0.34F;
    private static final float IMPACT_SCENE_START = 0.51F;
    private static final float IMPACT_BEAM_END = 0.72F;
    private static final float ASTEROID_COVER_END = 0.5F;
    private static final float MICROBOT_BACKGROUND_FADE_START = 0.48F;
    private static final float MICROBOT_BACKGROUND_CLEAR = 0.5F;
    private static final int STAR_COUNT = 176;
    private static final int SCANLINE_COUNT = 30;
    private static final int CHARGE_PARTICLE_INTERVAL = 2;
    private static final int SHOCKWAVE_PARTICLE_INTERVAL = 2;
    private static final int SHOCKWAVE_PARTICLE_SEGMENTS = 48;
    private static final double SHOCKWAVE_PARTICLE_VIEW_DISTANCE_SQUARED = 96.0D * 96.0D;
    private static long startedAt;
    private static long lastChargeParticleTick = Long.MIN_VALUE;
    private static long lastShockwaveParticleTick = Long.MIN_VALUE;
    private static int duration;
    private static int strikeRadius;
    private static AttackMode attackMode = AttackMode.ENTROPY_INVERSION;
    private static int soundStage;
    private static boolean impactBurstSpawned;
    private static double targetX;
    private static double targetY;
    private static double targetZ;
    private static boolean active;

    private CutsceneOverlay() {
    }

    public static void begin(
            double x,
            double y,
            double z,
            int ticks,
            int radius,
            AttackMode mode
    ) {
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
        attackMode = mode;
        startedAt = minecraft.level.getGameTime();
        lastChargeParticleTick = Long.MIN_VALUE;
        lastShockwaveParticleTick = Long.MIN_VALUE;
        soundStage = 0;
        impactBurstSpawned = false;
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
            if (attackMode != AttackMode.ENTROPY_INVERSION) {
                return;
            }
            spawnChargeParticles(minecraft, progress);
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

    private static void drawMeteors(
            RenderGuiOverlayEvent.Post event,
            int width,
            int height,
            float progress,
            float alpha
    ) {
        int meteorCount = progress < CHARGE_SCENE_START ? 4 : 10;
        for (int i = 0; i < meteorCount; i++) {
            int cycle = Math.floorMod((int) (progress * 900.0F) + i * 137, 900);
            float travel = cycle / 900.0F;
            int startX = Math.floorMod(i * 271 + 31, Math.max(1, width));
            int startY = Math.floorMod(i * 149 + 17, Math.max(1, height / 2));
            int x = startX + (int) (travel * width * 0.72F);
            int y = startY + (int) (travel * height * 0.48F);
            int length = 8 + (i % 4) * 5;
            int meteorAlpha = (int) (alpha * (95.0F + (i % 3) * 45.0F));
            int color = (meteorAlpha << 24) | (i % 3 == 0 ? 0x00FF8870 : 0x0066DFFF);
            event.getGuiGraphics().fill(x - length, y - length / 2, x, y, color);
            event.getGuiGraphics().fill(
                    x - 2,
                    y - 1,
                    x + 2,
                    y + 2,
                    ((meteorAlpha + 35) << 24) | 0x00F5FFFF
            );
        }
    }

    private static void drawDedicatedCutsceneFrame(
            RenderGuiOverlayEvent.Post event,
            int width,
            int height,
            float progress,
            int accentColor,
            Component systemLabel
    ) {
        int barHeight = Math.max(12, height / 22);
        int meterWidth = Math.max(80, width / 3);
        int meterLeft = (width - meterWidth) / 2;
        int meterY = height - barHeight + 5;
        event.getGuiGraphics().fill(0, 0, width, barHeight, 0xE004080B);
        event.getGuiGraphics().fill(0, height - barHeight, width, height, 0xE004080B);
        event.getGuiGraphics().fill(meterLeft, meterY, meterLeft + meterWidth, meterY + 3, 0xFF26363A);
        int filled = (int) (meterWidth * clamp01(progress));
        if (filled > 0) {
            event.getGuiGraphics().fill(meterLeft, meterY, meterLeft + filled, meterY + 3, accentColor);
        }
        event.getGuiGraphics().drawString(
                Minecraft.getInstance().font,
                systemLabel,
                12,
                height - barHeight + 5,
                accentColor
        );
        event.getGuiGraphics().drawString(
                Minecraft.getInstance().font,
                Component.literal(String.format(java.util.Locale.ROOT, "%03d%%", (int) (progress * 100.0F))),
                width - 42,
                height - barHeight + 5,
                accentColor
        );
    }

    private static void drawAsteroidCutscene(
            RenderGuiOverlayEvent.Post event,
            int width,
            int height,
            float progress
    ) {
        float descent = clamp01(progress / ASTEROID_COVER_END);
        float fade = clamp01((ASTEROID_COVER_END - progress) / 0.07F);
        int alpha = (int) (255.0F * fade);
        int centerX = width / 2;
        int surfaceY = height * 4 / 5;
        int planetRadius = Math.max(24, Math.min(width / 3, height / 3));
        int planetCenterY = surfaceY + planetRadius;
        int meteorSize = Math.max(24, Math.min(width / 10, height / 8));
        int meteorX = centerX;
        int meteorY = height / 9 + (int) (descent * height * 0.46F);

        event.getGuiGraphics().fill(0, 0, width, height, (alpha << 24) | 0x00070710);
        drawStars(event, width, height, fade, progress * 0.7F);
        drawPlanet(event, centerX, planetCenterY, planetRadius, fade);
        drawEllipse(
                event,
                centerX,
                surfaceY,
                planetRadius + 8,
                Math.max(5, planetRadius / 8),
                ((int) (fade * 170.0F) << 24) | 0x00FF7A32
        );

        for (int i = 0; i < 7; i++) {
            int trailWidth = meteorSize / 5 + (i % 3) * 2;
            int trailLength = meteorSize + i * 9;
            int offsetX = (i - 3) * Math.max(2, meteorSize / 10);
            int trailAlpha = (int) (fade * (150 - i * 12));
            drawScaledSegment(
                    event,
                    meteorX,
                    meteorY,
                    1.0F,
                    offsetX,
                    -meteorSize / 3 - 6,
                    offsetX - meteorSize / 5,
                    -meteorSize / 3 - trailLength,
                    trailWidth,
                    (trailAlpha << 24) | (i % 2 == 0 ? 0x00FF5425 : 0xFFFFC04D)
            );
        }

        int rock = (alpha << 24) | 0x00322B2A;
        int rockLight = (alpha << 24) | 0x00705A46;
        int glow = ((int) (fade * 235.0F) << 24) | 0x00FFB84A;
        event.getGuiGraphics().fill(
                meteorX - meteorSize / 2,
                meteorY - meteorSize / 3,
                meteorX + meteorSize / 2,
                meteorY + meteorSize / 3,
                rock
        );
        event.getGuiGraphics().fill(
                meteorX - meteorSize / 3,
                meteorY - meteorSize / 2,
                meteorX + meteorSize / 3,
                meteorY + meteorSize / 2,
                rock
        );
        event.getGuiGraphics().fill(
                meteorX - meteorSize / 3,
                meteorY - meteorSize / 3,
                meteorX + meteorSize / 4,
                meteorY - meteorSize / 8,
                rockLight
        );
        event.getGuiGraphics().fill(
                meteorX - 2,
                meteorY - 2,
                meteorX + 3,
                meteorY + 3,
                glow
        );
        drawEllipse(
                event,
                meteorX,
                meteorY,
                meteorSize,
                Math.max(5, meteorSize / 3),
                ((int) (fade * 95.0F) << 24) | 0x00FF6A24
        );

        int guideBottom = Math.min(height - 20, surfaceY);
        int guideTop = Math.max(height / 10, meteorY + meteorSize / 2);
        int guideX = width - Math.max(24, width / 18);
        event.getGuiGraphics().fill(guideX, guideTop, guideX + 2, guideBottom, (alpha << 24) | 0x00D8D2C7);
        event.getGuiGraphics().fill(
                guideX - 5,
                guideTop + (int) ((guideBottom - guideTop) * descent),
                guideX + 8,
                guideTop + (int) ((guideBottom - guideTop) * descent) + 2,
                glow
        );
        drawSceneLabels(
                event,
                width,
                height,
                fade,
                getStageKey(progress)
        );
        drawDedicatedCutsceneFrame(
                event,
                width,
                height,
                descent,
                0xFFFF8742,
                Component.translatable("gui.entropyinversion.cutscene.asteroid.system")
        );
        int targetColor = ((int) (fade * 230.0F) << 24) | 0x00FF9C42;
        drawEllipse(event, centerX, surfaceY, Math.max(10, strikeRadius), 5, targetColor);
        drawScaledCenteredString(
                event,
                Minecraft.getInstance(),
                String.format(
                        java.util.Locale.ROOT,
                        "00:%02d",
                        Math.max(0, (int) Math.ceil((1.0F - progress) * duration / 20.0F))
                ),
                centerX,
                height - Math.max(24, height / 18) + 5,
                1.5F,
                0xFFFFD49A
        );
    }

    private static void drawMicrobotCutscene(
            RenderGuiOverlayEvent.Post event,
            int width,
            int height,
            float progress
    ) {
        int centerX = width / 2;
        int centerY = height / 2;
        float sweep = progress * 8.0F;
        float release = clamp01(progress / 0.25F);
        float terminate = clamp01((progress - 0.72F) / 0.28F);
        float backgroundOpacity = 1.0F - clamp01(
                (progress - MICROBOT_BACKGROUND_FADE_START)
                        / (MICROBOT_BACKGROUND_CLEAR - MICROBOT_BACKGROUND_FADE_START)
        );
        int backgroundAlpha = (int) (255.0F * backgroundOpacity);
        if (backgroundAlpha > 0) {
            event.getGuiGraphics().fill(
                    0,
                    0,
                    width,
                    height,
                    (backgroundAlpha << 24) | 0x00030B0B
            );
        }

        int gridColor = ((int) (38.0F * backgroundOpacity) << 24) | 0x0030B78D;
        int gridStep = Math.max(18, width / 36);
        for (int x = Math.floorMod((int) (progress * 40.0F), gridStep); x < width; x += gridStep) {
            event.getGuiGraphics().fill(x, 0, x + 1, height, gridColor);
        }
        for (int y = Math.floorMod((int) (progress * 24.0F), gridStep); y < height; y += gridStep) {
            event.getGuiGraphics().fill(0, y, width, y + 1, gridColor);
        }

        int targetY = centerY + 8;
        int targetColor = ((int) (255.0F * (0.35F + terminate * 0.65F)) << 24)
                | (terminate > 0.0F ? 0x00FF5D5D : 0x005CE6B5);
        int silhouetteWidth = Math.max(18, Math.min(width / 18, 38));
        int silhouetteHeight = Math.max(56, Math.min(height / 3, 110));
        int headSize = Math.max(10, silhouetteWidth / 2);
        drawEllipse(event, centerX, targetY - silhouetteHeight / 2, headSize, headSize, targetColor);
        event.getGuiGraphics().fill(
                centerX - silhouetteWidth / 3,
                targetY - silhouetteHeight / 2 + headSize,
                centerX + silhouetteWidth / 3 + 1,
                targetY + silhouetteHeight / 4,
                targetColor
        );
        drawScaledSegment(event, centerX, targetY, 1.0F,
                -silhouetteWidth / 4, 0, -silhouetteWidth, silhouetteHeight / 3, 4, targetColor);
        drawScaledSegment(event, centerX, targetY, 1.0F,
                silhouetteWidth / 4, 0, silhouetteWidth, silhouetteHeight / 3, 4, targetColor);
        drawScaledSegment(event, centerX, targetY, 1.0F,
                -silhouetteWidth / 5, silhouetteHeight / 4, -silhouetteWidth / 2, silhouetteHeight / 2, 4, targetColor);
        drawScaledSegment(event, centerX, targetY, 1.0F,
                silhouetteWidth / 5, silhouetteHeight / 4, silhouetteWidth / 2, silhouetteHeight / 2, 4, targetColor);

        int scanY = Math.floorMod((int) (progress * height * 2.0F), Math.max(1, height));
        int scanAlpha = (int) (85.0F * backgroundOpacity);
        event.getGuiGraphics().fill(
                0,
                scanY,
                width,
                scanY + 2,
                (scanAlpha << 24) | 0x003CE8AB
        );
        drawEllipse(
                event,
                centerX,
                targetY,
                Math.max(18, width / 10),
                Math.max(12, height / 12),
                ((int) (90.0F + 120.0F * terminate) << 24) | 0x0037F4B0
        );

        int particleCount = 84;
        double swarmRadius = Math.max(28.0D, Math.min(width, height) * (0.34D - release * 0.18D));
        for (int i = 0; i < particleCount; i++) {
            double baseAngle = Math.PI * 2.0D * i / particleCount;
            double angle = baseAngle + sweep * (0.55D + (i % 5) * 0.08D);
            double radius = swarmRadius * (0.72D + (i % 7) * 0.045D);
            int x = centerX + (int) (Math.cos(angle) * radius);
            int y = centerY + (int) (Math.sin(angle) * radius * 0.72D);
            int dotSize = i % 9 == 0 ? 3 : 2;
            int color = i % 4 == 0 ? 0xFFE8FFB3 : 0xFF43EBA7;
            event.getGuiGraphics().fill(x, y, x + dotSize, y + dotSize, color);
            if (i % 3 == 0) {
                drawScaledSegment(
                        event,
                        x,
                        y,
                        1.0F,
                        0,
                        0,
                        centerX - x,
                        centerY - y,
                        1,
                        0x5535DFA3
                );
            }
        }

        for (int i = 0; i < 4; i++) {
            int ringRadius = Math.max(24, Math.min(width, height) / 5 + i * 13);
            int ringY = Math.max(8, ringRadius / 3);
            int ringAlpha = (int) (150.0F * (1.0F - terminate * 0.65F));
            drawEllipse(
                    event,
                    centerX,
                    centerY,
                    ringRadius,
                    ringY,
                    (ringAlpha << 24) | (i % 2 == 0 ? 0x0039DDA3 : 0x007EF2C3)
            );
        }

        int panelTop = Math.max(42, height / 10);
        event.getGuiGraphics().fill(12, panelTop, 142, panelTop + 36, 0xB2081715);
        event.getGuiGraphics().drawString(
                Minecraft.getInstance().font,
                Component.translatable(
                        terminate > 0.0F
                                ? "gui.entropyinversion.cutscene.microbots.terminating"
                                : "gui.entropyinversion.cutscene.microbots.locked"
                ),
                20,
                panelTop + 7,
                0xFF9DFFD6
        );
        event.getGuiGraphics().drawString(
                Minecraft.getInstance().font,
                Component.translatable("gui.entropyinversion.cutscene.microbots.density"),
                20,
                panelTop + 20,
                0xFF4DCEA2
        );
        int barWidth = 112;
        event.getGuiGraphics().fill(20, panelTop + 31, 20 + barWidth, panelTop + 34, 0xFF173B30);
        int fillWidth = (int) (barWidth * clamp01(release + terminate * 0.35F));
        event.getGuiGraphics().fill(20, panelTop + 31, 20 + fillWidth, panelTop + 34, 0xFF56EBAA);

        drawSceneLabels(event, width, height, 1.0F, getStageKey(progress));
        drawDedicatedCutsceneFrame(
                event,
                width,
                height,
                progress,
                0xFF46E7A7,
                Component.translatable("gui.entropyinversion.cutscene.microbots.system")
        );
        drawScaledCenteredString(
                event,
                Minecraft.getInstance(),
                String.format(
                        java.util.Locale.ROOT,
                        "00:%02d",
                        Math.max(0, (int) Math.ceil((1.0F - progress) * duration / 20.0F))
                ),
                centerX,
                height - Math.max(24, height / 18) + 5,
                1.5F,
                0xFFB8FFE0
        );
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
        boolean asteroid = attackMode == AttackMode.ASTEROID_BOMBARDMENT;
        if (asteroid) {
            if (progress < ASTEROID_COVER_END) {
                drawAsteroidCutscene(event, width, height, progress);
            }
            return;
        }
        if (attackMode == AttackMode.ANTI_ORGANIC_MICROBOTS) {
            drawMicrobotCutscene(event, width, height, progress);
            return;
        }
        float sceneEnd = IMPACT_SCENE_START;
        int centerX = width / 2;
        int centerY = height / 2;
        if (progress < sceneEnd) {
            float sceneAlpha = progress <= SPACE_SCENE_END
                    ? 1.0F
                    : 1.0F - (progress - SPACE_SCENE_END)
                            / (sceneEnd - SPACE_SCENE_END);
            drawSpaceScene(event, width, height, centerX, progress, sceneAlpha);
        } else {
            drawImpactScene(event, width, height, centerX, progress);
        }
        drawCinematicFrame(event, width, height, progress);
        if (progress >= LOCK_SCENE_START) {
            drawTargetReticle(event, width, height, progress);
        }
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
        if (progress < CHARGE_SCENE_START || attackMode != AttackMode.ENTROPY_INVERSION) {
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

        float beamCharge = clamp01(
                (progress - CHARGE_SCENE_START) / (IMPACT_BEAM_END - CHARGE_SCENE_START)
        );
        float beamPulse = (0.28F + beamCharge * 0.72F)
                * (0.88F + 0.12F
                * (float) Math.sin((minecraft.level.getGameTime() + event.getPartialTick()) * 0.65D));
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
                0.62F * beamPulse,
                0.82F * beamPulse
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
                0.32F * beamPulse,
                0.62F * beamPulse
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
                0.1F * beamPulse,
                0.22F * beamPulse
        );
        minecraft.renderBuffers().bufferSource().endBatch(
                RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, false)
        );
        minecraft.renderBuffers().bufferSource().endBatch(
                RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true)
        );

        if (progress >= IMPACT_SCENE_START && progress < IMPACT_BEAM_END) {
            drawImpactCore(
                    minecraft,
                    poseStack,
                    targetX - targetBlock.getX(),
                    targetY - targetBlock.getY(),
                    targetZ - targetBlock.getZ(),
                    progress
            );
        }

        if (progress >= CHARGE_SCENE_START && progress < IMPACT_BEAM_END) {
            drawChargeLattice(
                    minecraft,
                    poseStack,
                    targetX - targetBlock.getX(),
                    targetY - targetBlock.getY(),
                    targetZ - targetBlock.getZ(),
                    progress,
                    beamCharge
            );
        }

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
            drawWorldRing(lines, poseStack, localX, localZ, localY + 0.08D,
                    shockwaveRadius * 0.76D, 1.0F, 0.18F, 0.12F, alpha * 0.58F);
            drawWorldRing(lines, poseStack, localX, localZ, localY + 0.12D,
                    shockwaveRadius * 0.48D, 1.0F, 0.48F, 0.12F, alpha * 0.32F);
            drawShockwaveCrown(lines, poseStack, localX, localZ, localY, shockwaveRadius, alpha);
            drawImpactLightning(
                    lines,
                    poseStack,
                    localX,
                    localY + 0.9D,
                    localZ,
                    progress,
                    minecraft.level.getGameTime() + event.getPartialTick()
            );
            minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
        }
        poseStack.popPose();
    }

    private static void drawImpactLightning(
            VertexConsumer lines,
            PoseStack poseStack,
            double centerX,
            double baseY,
            double centerZ,
            float progress,
            double gameTime
    ) {
        double elapsedTicks = (progress - IMPACT_BEAM_END) * duration;
        if (elapsedTicks < 0.0D || elapsedTicks >= 24.0D) {
            return;
        }

        int strikeIndex = (int) (elapsedTicks / 6.0D);
        double flashProgress = elapsedTicks - strikeIndex * 6.0D;
        if (flashProgress >= 2.4D) {
            return;
        }

        float flicker = (float) (0.72D + 0.28D * Math.sin(gameTime * 2.7D + strikeIndex * 1.9D));
        double height = 11.0D + (strikeIndex % 3) * 4.0D;
        double spread = Math.min(12.0D, Math.max(2.5D, strikeRadius * 0.08D));
        int boltCount = strikeRadius >= 40 ? 3 : 2;
        for (int bolt = 0; bolt < boltCount; bolt++) {
            double seed = strikeIndex * 17.0D + bolt * 31.0D;
            double startX = Math.sin(seed * 1.31D) * spread;
            double startZ = Math.cos(seed * 0.91D) * spread;
            double endX = Math.sin(seed * 0.73D) * spread * 0.2D;
            double endZ = Math.cos(seed * 1.17D) * spread * 0.2D;
            drawLightningBolt(
                    lines,
                    poseStack,
                    centerX + startX,
                    baseY,
                    centerZ + startZ,
                    centerX + endX,
                    centerZ + endZ,
                    height,
                    seed,
                    flicker
            );

            if (bolt == 0) {
                double branchX = centerX + startX * 0.35D + spread * 0.55D;
                double branchZ = centerZ + startZ * 0.35D - spread * 0.35D;
                drawLightningBolt(
                        lines,
                        poseStack,
                        branchX,
                        baseY,
                        branchZ,
                        centerX + endX,
                        centerZ + endZ,
                        height * 0.52D,
                        seed + 8.0D,
                        flicker * 0.72F
                );
            }
        }
    }

    private static void drawLightningBolt(
            VertexConsumer lines,
            PoseStack poseStack,
            double startX,
            double baseY,
            double startZ,
            double endX,
            double endZ,
            double height,
            double seed,
            float alpha
    ) {
        int segments = 8;
        double previousX = startX;
        double previousY = baseY + height;
        double previousZ = startZ;
        for (int segment = 1; segment <= segments; segment++) {
            double amount = segment / (double) segments;
            double jitter = segment == segments ? 0.0D : Math.sin(seed + segment * 8.13D) * 1.15D;
            double jitterZ = segment == segments ? 0.0D : Math.cos(seed * 0.7D + segment * 5.37D) * 1.15D;
            double x = startX + (endX - startX) * amount + jitter;
            double y = baseY + height * (1.0D - amount);
            double z = startZ + (endZ - startZ) * amount + jitterZ;
            drawWorldLine(
                    lines,
                    poseStack,
                    previousX,
                    previousY,
                    previousZ,
                    x,
                    y,
                    z,
                    0.25F,
                    0.78F,
                    1.0F,
                    alpha * 0.72F
            );
            drawWorldLine(
                    lines,
                    poseStack,
                    previousX + 0.025D,
                    previousY,
                    previousZ + 0.025D,
                    x + 0.025D,
                    y,
                    z + 0.025D,
                    0.88F,
                    0.97F,
                    1.0F,
                    alpha
            );
            previousX = x;
            previousY = y;
            previousZ = z;
        }
    }

    private static void drawChargeLattice(
            Minecraft minecraft,
            PoseStack poseStack,
            double localX,
            double localY,
            double localZ,
            float progress,
            float charge
    ) {
        VertexConsumer lines = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        double radius = Math.max(1.5D, strikeRadius * (0.22D + charge * 0.78D));
        double rotation = progress * Math.PI * 7.0D;
        float alpha = 0.18F + charge * 0.42F;

        drawWorldRing(lines, poseStack, localX, localZ, localY + 0.16D, radius,
                0.18F, 0.82F, 1.0F, alpha);
        drawWorldRing(lines, poseStack, localX, localZ, localY + 0.22D, radius * 0.76D,
                0.48F, 0.22F, 1.0F, alpha * 0.8F);
        drawWorldRing(lines, poseStack, localX, localZ, localY + 0.28D, radius * 0.42D,
                1.0F, 0.24F, 0.34F, alpha * 0.9F);

        for (int i = 0; i < 12; i++) {
            double angle = rotation + Math.PI * 2.0D * i / 12.0D;
            double x = localX + Math.cos(angle) * radius;
            double z = localZ + Math.sin(angle) * radius;
            double innerX = localX + Math.cos(angle - 0.34D) * radius * 0.42D;
            double innerZ = localZ + Math.sin(angle - 0.34D) * radius * 0.42D;
            drawWorldLine(lines, poseStack, x, localY + 0.1D, z,
                    innerX, localY + 0.1D + charge * 5.0D, innerZ,
                    0.28F, 0.84F, 1.0F, alpha);

            if (i % 2 == 0) {
                double pillarHeight = 5.0D + charge * 26.0D;
                drawWorldLine(lines, poseStack, x, localY, z,
                        x, localY + pillarHeight, z,
                        0.22F, 0.64F, 1.0F, alpha * 0.7F);
            }
        }

        minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static void drawImpactCore(
            Minecraft minecraft,
            PoseStack poseStack,
            double localX,
            double localY,
            double localZ,
            float progress
    ) {
        VertexConsumer lines = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        float impact = clamp01((progress - IMPACT_SCENE_START) / (IMPACT_BEAM_END - IMPACT_SCENE_START));
        float pulse = 0.62F + 0.38F * (float) Math.sin(progress * Math.PI * 90.0D);
        double radius = Math.max(1.0D, strikeRadius * (0.12D + impact * 0.2D));
        double rotation = progress * Math.PI * 11.0D;

        drawWorldRing(lines, poseStack, localX, localZ, localY + 0.08D, radius,
                1.0F, 0.86F, 0.52F, pulse);
        drawWorldRing(lines, poseStack, localX, localZ, localY + 0.2D, radius * 0.66D,
                1.0F, 0.24F, 0.12F, pulse * 0.8F);
        for (int i = 0; i < 8; i++) {
            double angle = rotation + Math.PI * 2.0D * i / 8.0D;
            double x = localX + Math.cos(angle) * radius;
            double z = localZ + Math.sin(angle) * radius;
            drawWorldLine(lines, poseStack, localX, localY + 0.08D, localZ,
                    x, localY + 0.08D, z,
                    1.0F, 0.44F, 0.12F, pulse * 0.65F);
        }
        minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static void spawnChargeParticles(Minecraft minecraft, float progress) {
        if (progress < CHARGE_SCENE_START
                || progress >= IMPACT_BEAM_END
                || (lastChargeParticleTick != Long.MIN_VALUE
                && minecraft.level.getGameTime() - lastChargeParticleTick < CHARGE_PARTICLE_INTERVAL)) {
            return;
        }
        lastChargeParticleTick = minecraft.level.getGameTime();

        float charge = clamp01(
                (progress - CHARGE_SCENE_START) / (IMPACT_SCENE_START - CHARGE_SCENE_START)
        );
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        double radius = Math.max(1.5D, strikeRadius * (0.42D - charge * 0.37D));
        double rotation = minecraft.level.getGameTime() * 0.16D;
        for (int i = 0; i < 24; i++) {
            double angle = rotation + Math.PI * 2.0D * i / 24.0D;
            double x = targetX + Math.cos(angle) * radius;
            double z = targetZ + Math.sin(angle) * radius;
            double y = targetY + 1.0D + charge * (10.0D + i % 5 * 3.0D);
            double dx = x - camera.x;
            double dy = y - camera.y;
            double dz = z - camera.z;
            if (dx * dx + dy * dy + dz * dz > SHOCKWAVE_PARTICLE_VIEW_DISTANCE_SQUARED) {
                continue;
            }

            minecraft.level.addParticle(
                    i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK,
                    x,
                    y,
                    z,
                    -Math.cos(angle) * 0.08D,
                    -0.14D - charge * 0.12D,
                    -Math.sin(angle) * 0.08D
            );
            if (i % 4 == 0) {
                minecraft.level.addParticle(
                        ParticleTypes.REVERSE_PORTAL,
                        x,
                        y,
                        z,
                        (targetX - x) * 0.012D,
                        -0.08D,
                        (targetZ - z) * 0.012D
                );
            }
        }
    }

    private static void spawnShockwaveParticles(Minecraft minecraft, float progress) {
        if (progress >= IMPACT_BEAM_END && !impactBurstSpawned) {
            impactBurstSpawned = true;
            spawnImpactBursts(minecraft);
        }

        if (progress < IMPACT_BEAM_END
                || (lastShockwaveParticleTick != Long.MIN_VALUE
                && minecraft.level.getGameTime() - lastShockwaveParticleTick
                < SHOCKWAVE_PARTICLE_INTERVAL)) {
            return;
        }
        lastShockwaveParticleTick = minecraft.level.getGameTime();

        spawnLightningParticles(minecraft, progress);

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

    private static void spawnLightningParticles(Minecraft minecraft, float progress) {
        double elapsedTicks = (progress - IMPACT_BEAM_END) * duration;
        if (elapsedTicks < 0.0D || elapsedTicks >= 24.0D) {
            return;
        }

        int strikeIndex = (int) (elapsedTicks / 6.0D);
        if (elapsedTicks - strikeIndex * 6.0D >= 4.0D) {
            return;
        }

        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        double baseY = Math.floor(targetY) + 1.05D;
        double height = 9.0D + (strikeIndex % 3) * 3.0D;
        double spread = Math.min(9.0D, Math.max(2.0D, strikeRadius * 0.06D));
        int boltCount = strikeRadius >= 40 ? 3 : 2;
        for (int bolt = 0; bolt < boltCount; bolt++) {
            double seed = strikeIndex * 17.0D + bolt * 31.0D;
            double startX = targetX + Math.sin(seed * 1.31D) * spread;
            double startZ = targetZ + Math.cos(seed * 0.91D) * spread;
            double endX = targetX + Math.sin(seed * 0.73D) * spread * 0.16D;
            double endZ = targetZ + Math.cos(seed * 1.17D) * spread * 0.16D;
            double previousX = startX;
            double previousY = baseY + height;
            double previousZ = startZ;
            for (int segment = 1; segment <= 8; segment++) {
                double amount = segment / 8.0D;
                double jitter = segment == 8 ? 0.0D : Math.sin(seed + segment * 8.13D) * 1.1D;
                double jitterZ = segment == 8 ? 0.0D : Math.cos(seed * 0.7D + segment * 5.37D) * 1.1D;
                double x = startX + (endX - startX) * amount + jitter;
                double y = baseY + height * (1.0D - amount);
                double z = startZ + (endZ - startZ) * amount + jitterZ;
                if (camera.distanceToSqr(x, y, z) <= SHOCKWAVE_PARTICLE_VIEW_DISTANCE_SQUARED) {
                    minecraft.level.addParticle(
                            ParticleTypes.ELECTRIC_SPARK,
                            x,
                            y,
                            z,
                            (x - previousX) * 2.0D,
                            (y - previousY) * 2.0D,
                            (z - previousZ) * 2.0D
                    );
                    if (segment % 2 == 0) {
                        minecraft.level.addParticle(
                                ParticleTypes.END_ROD,
                                x,
                                y,
                                z,
                                0.0D,
                                -0.04D,
                                0.0D
                        );
                    }
                }
                previousX = x;
                previousY = y;
                previousZ = z;
            }
        }
    }

    private static void spawnImpactBursts(Minecraft minecraft) {
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        int burstCount = Math.min(32, 6 + strikeRadius / 8);
        int ringCount = Math.min(4, 1 + strikeRadius / 50);
        double viewDistanceSquared = SHOCKWAVE_PARTICLE_VIEW_DISTANCE_SQUARED;
        spawnImpactBurst(minecraft, camera, targetX, targetY + 0.5D, targetZ, viewDistanceSquared);

        for (int i = 0; i < burstCount; i++) {
            int ring = i % ringCount;
            double ringRadius = strikeRadius * (ring + 1.0D) / ringCount;
            double angle = i * 2.399963229728653D + ring * 0.42D;
            double x = targetX + Math.cos(angle) * ringRadius;
            double z = targetZ + Math.sin(angle) * ringRadius;
            double y = targetY + 0.35D + (ring % 2) * 0.45D;
            if (camera.distanceToSqr(x, y, z) > viewDistanceSquared) {
                continue;
            }

            spawnImpactBurst(minecraft, camera, x, y, z, viewDistanceSquared);
            minecraft.level.addParticle(
                    ParticleTypes.FLAME,
                    x,
                    y,
                    z,
                    Math.cos(angle) * (0.35D + ring * 0.12D),
                    0.2D + ring * 0.08D,
                    Math.sin(angle) * (0.35D + ring * 0.12D)
            );
        }
    }

    private static void spawnImpactBurst(
            Minecraft minecraft,
            Vec3 camera,
            double x,
            double y,
            double z,
            double viewDistanceSquared
    ) {
        if (camera.distanceToSqr(x, y, z) > viewDistanceSquared) {
            return;
        }
        minecraft.level.addParticle(
                ParticleTypes.EXPLOSION_EMITTER,
                x,
                y,
                z,
                0.0D,
                0.0D,
                0.0D
        );
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

    private static void drawShockwaveCrown(
            VertexConsumer lines,
            PoseStack poseStack,
            double centerX,
            double centerZ,
            double y,
            double radius,
            float alpha
    ) {
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2.0D * i / 16.0D;
            double x = centerX + Math.cos(angle) * radius;
            double z = centerZ + Math.sin(angle) * radius;
            double height = 0.7D + (i % 4 == 0 ? 4.2D : 2.1D) * alpha;
            float green = i % 4 == 0 ? 0.72F : 0.22F;
            float blue = i % 4 == 0 ? 0.18F : 0.06F;
            drawWorldLine(
                    lines,
                    poseStack,
                    x,
                    y,
                    z,
                    x,
                    y + height,
                    z,
                    1.0F,
                    green,
                    blue,
                    alpha * (i % 4 == 0 ? 0.88F : 0.45F)
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
        drawMeteors(event, width, height, progress, sceneAlpha);

        int planetRadius = Math.max(24, Math.min(width / 4, height / 4));
        int planetCenterY = height * 4 / 5;
        int planetTop = planetCenterY - planetRadius;
        int titleSafeTop = Math.max(38, height / 18 + 32);
        int imageTopLimit = titleSafeTop + Math.max(12, height / 24);
        int imageBottomLimit = Math.min(
                planetTop - 4,
                height / 2 - Math.max(28, height / 10)
        );
        int availableHeight = Math.max(0, imageBottomLimit - imageTopLimit);
        int orbitalWidth = Math.min(
                Math.min(width - 24, availableHeight * 512 / 320),
                Math.max(160, (int) (planetRadius * 1.8F))
        );
        int orbitalHeight = orbitalWidth * 320 / 512;
        int orbitalCenterY = imageTopLimit + availableHeight / 2;
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
        drawOrbitalWeapon(
                event,
                centerX,
                orbitalCenterY,
                planetTop,
                orbitalWidth,
                orbitalHeight,
                progress,
                sceneAlpha
        );
        drawAtmosphericHorizon(event, planetTop, centerX, planetRadius, progress, sceneAlpha);
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
        if (attackMode == AttackMode.ASTEROID_BOMBARDMENT) {
            if (progress < 0.24F) {
                return "gui.entropyinversion.cutscene.asteroid_approach";
            }
            return progress < 0.42F
                    ? "gui.entropyinversion.cutscene.asteroid.impact"
                    : "gui.entropyinversion.cutscene.asteroid.crater";
        }
        if (attackMode == AttackMode.ANTI_ORGANIC_MICROBOTS) {
            if (progress < 0.22F) {
                return "gui.entropyinversion.cutscene.microbots.deploy";
            }
            return progress < 0.72F
                    ? "gui.entropyinversion.cutscene.microbots.sweep"
                    : "gui.entropyinversion.cutscene.microbots.terminate";
        }
        if (progress < 0.18F) {
            return "gui.entropyinversion.cutscene.space";
        }
        if (progress < LOCK_SCENE_START) {
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
        int lineAlpha = (int) (alpha * 11.0F);
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
            int rowAlpha = (int) (255.0F * alpha
                    * Math.max(0.0F, Math.min(1.0F, (radius - y) / (radius * 0.18F))));
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

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
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
        drawImpactTelemetry(event, width, height, progress);
    }

    private static void drawOrbitalWeapon(
            RenderGuiOverlayEvent.Post event,
            int centerX,
            int centerY,
            int planetSurfaceY,
            int imageWidth,
            int imageHeight,
            float progress,
            float alpha
    ) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return;
        }

        float charge = clamp01((progress - CHARGE_SCENE_START) / (IMPACT_SCENE_START - CHARGE_SCENE_START));
        float pulse = 1.0F + 0.018F * (float) Math.sin(progress * 38.0D);
        int tetherY = centerY + (int) (imageHeight * 0.36F);
        int tetherBottom = tetherY + (int) ((planetSurfaceY - tetherY) * (0.08F + charge * 0.92F));
        int tetherAlpha = (int) (alpha * (40.0F + charge * 115.0F));
        PoseStack pose = event.getGuiGraphics().pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0.0F);
        pose.scale(pulse, pulse, 1.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        event.getGuiGraphics().blit(
                ORBITAL_ARRAY_TEXTURE,
                -imageWidth / 2,
                -imageHeight / 2,
                imageWidth,
                imageHeight,
                0.0F,
                0.0F,
                512,
                320,
                512,
                320
        );
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();

        drawOrbitalWeaponSilhouette(event, centerX, centerY, imageWidth, imageHeight, progress, alpha);

        event.getGuiGraphics().fill(
                centerX - 2,
                tetherY - 1,
                centerX + 2,
                tetherBottom,
                ((int) (alpha * (90.0F + charge * 145.0F)) << 24) | 0x004ADFFF
        );
        event.getGuiGraphics().fill(
                centerX - 1,
                tetherY,
                centerX + 1,
                tetherBottom,
                (tetherAlpha << 24) | 0x004ADFFF
        );
        int scanY = tetherY
                + Math.floorMod((int) (progress * 700.0F), Math.max(1, tetherBottom - tetherY));
        event.getGuiGraphics().fill(
                centerX - 5,
                scanY,
                centerX + 6,
                scanY + 1,
                ((int) (alpha * 220.0F) << 24) | 0x00FFFFFF
        );
    }

    private static void drawOrbitalWeaponSilhouette(
            RenderGuiOverlayEvent.Post event,
            int centerX,
            int centerY,
            int imageWidth,
            int imageHeight,
            float progress,
            float alpha
    ) {
        float scale = imageWidth / 512.0F;
        int pulseAlpha = (int) (alpha * 232.0F);
        int hull = (pulseAlpha << 24) | 0x0008172C;
        int hullEdge = (pulseAlpha << 24) | 0x0065E9FF;
        int glow = ((int) (alpha * 220.0F) << 24) | 0x00D7FBFF;
        int reactor = ((int) (alpha * 245.0F) << 24) | 0x00FF5B9B;
        int coreSize = Math.max(3, (int) ((5.0F + 3.0F * (float) Math.sin(progress * 44.0D)) * scale));

        fillScaled(event, centerX, centerY, scale, -88, -15, 176, 30, hull);
        fillScaled(event, centerX, centerY, scale, -56, -27, 112, 54, hull);
        fillScaled(event, centerX, centerY, scale, -34, -38, 68, 76, hull);

        drawScaledSegment(event, centerX, centerY, scale, -188, -34, -40, -12, 20, hull);
        drawScaledSegment(event, centerX, centerY, scale, 188, -34, 40, -12, 20, hull);
        drawScaledSegment(event, centerX, centerY, scale, -186, 34, -46, 15, 14, hull);
        drawScaledSegment(event, centerX, centerY, scale, 186, 34, 46, 15, 14, hull);
        drawScaledSegment(event, centerX, centerY, scale, -174, -34, -50, -17, 3, hullEdge);
        drawScaledSegment(event, centerX, centerY, scale, 174, -34, 50, -17, 3, hullEdge);
        drawScaledSegment(event, centerX, centerY, scale, -174, 34, -55, 18, 2, hullEdge);
        drawScaledSegment(event, centerX, centerY, scale, 174, 34, 55, 18, 2, hullEdge);

        drawScaledSegment(event, centerX, centerY, scale, -28, -26, 0, -48, 5, hullEdge);
        drawScaledSegment(event, centerX, centerY, scale, 28, -26, 0, -48, 5, hullEdge);
        drawScaledSegment(event, centerX, centerY, scale, -17, 31, 0, 74, 4, hullEdge);
        drawScaledSegment(event, centerX, centerY, scale, 17, 31, 0, 74, 4, hullEdge);
        drawScaledSegment(event, centerX, centerY, scale, -7, 36, 0, 91, 2, glow);
        drawScaledSegment(event, centerX, centerY, scale, 7, 36, 0, 91, 2, glow);
        drawScaledSegment(event, centerX, centerY, scale, -182, 0, -42, 0, 3, glow);
        drawScaledSegment(event, centerX, centerY, scale, 182, 0, 42, 0, 3, glow);

        fillScaled(event, centerX, centerY, scale, -23, -23, 46, 46, hullEdge);
        fillScaled(event, centerX, centerY, scale, -18, -18, 36, 36, hull);
        fillScaled(event, centerX, centerY, scale, -coreSize, -coreSize, coreSize * 2, coreSize * 2, reactor);
        fillScaled(event, centerX, centerY, scale, -2, -2, 4, 4, glow);

        for (int i = 0; i < 4; i++) {
            int x = (int) ((i * 38 - 57) * scale);
            fillScaled(event, centerX, centerY, scale, x, -4, 10, 8,
                    i % 2 == 0 ? reactor : hullEdge);
        }
    }

    private static void drawAtmosphericHorizon(
            RenderGuiOverlayEvent.Post event,
            int planetTop,
            int centerX,
            int planetRadius,
            float progress,
            float alpha
    ) {
        int horizonY = planetTop + Math.max(2, planetRadius / 18);
        int glowAlpha = (int) (alpha * (50.0F + 55.0F
                * (0.5F + 0.5F * (float) Math.sin(progress * Math.PI * 10.0D))));
        int horizonColor = (glowAlpha << 24) | 0x0047CFFF;
        event.getGuiGraphics().fill(
                centerX - planetRadius - 18,
                horizonY,
                centerX + planetRadius + 18,
                horizonY + 2,
                horizonColor
        );
        event.getGuiGraphics().fill(
                centerX - planetRadius / 2,
                horizonY - 2,
                centerX + planetRadius / 2,
                horizonY,
                ((glowAlpha / 2) << 24) | 0x00FF5F9C
        );

        for (int i = 0; i < 7; i++) {
            int x = centerX - planetRadius + (i + 1) * (planetRadius * 2) / 8;
            int pulse = (int) (alpha * (70.0F + 120.0F
                    * (0.5F + 0.5F * (float) Math.sin(progress * 28.0D + i))));
            event.getGuiGraphics().fill(
                    x - 1,
                    horizonY - 3 - (i % 3),
                    x + 2,
                    horizonY + 4,
                    (pulse << 24) | (i % 2 == 0 ? 0x00FF5F9C : 0x006FEAFF)
            );
        }

    }

    private static void fillScaled(
            RenderGuiOverlayEvent.Post event,
            int centerX,
            int centerY,
            float scale,
            int offsetX,
            int offsetY,
            int width,
            int height,
            int color
    ) {
        int left = centerX + Math.round(offsetX * scale);
        int top = centerY + Math.round(offsetY * scale);
        int right = left + Math.max(1, Math.round(width * scale));
        int bottom = top + Math.max(1, Math.round(height * scale));
        event.getGuiGraphics().fill(left, top, right, bottom, color);
    }

    private static void drawScaledSegment(
            RenderGuiOverlayEvent.Post event,
            int centerX,
            int centerY,
            float scale,
            int startX,
            int startY,
            int endX,
            int endY,
            int thickness,
            int color
    ) {
        int x0 = centerX + Math.round(startX * scale);
        int y0 = centerY + Math.round(startY * scale);
        int x1 = centerX + Math.round(endX * scale);
        int y1 = centerY + Math.round(endY * scale);
        float deltaX = x1 - x0;
        float deltaY = y1 - y0;
        float length = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        PoseStack pose = event.getGuiGraphics().pose();
        pose.pushPose();
        pose.translate((x0 + x1) / 2.0F, (y0 + y1) / 2.0F, 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees((float) Math.toDegrees(Math.atan2(deltaY, deltaX))));
        int lineWidth = Math.max(1, Math.round(thickness * scale));
        event.getGuiGraphics().fill(-Math.round(length / 2.0F), -lineWidth / 2,
                Math.round(length / 2.0F), (lineWidth + 1) / 2, color);
        pose.popPose();
    }

    private static void drawImpactTelemetry(
            RenderGuiOverlayEvent.Post event,
            int width,
            int height,
            float progress
    ) {
        int centerX = width / 2;
        int centerY = height / 2;
        float impactProgress = clamp01(
                (progress - IMPACT_SCENE_START) / (1.0F - IMPACT_SCENE_START)
        );
        int ringRadius = 22 + (int) (impactProgress * Math.min(width, height) * 0.42F);
        int ringAlpha = (int) ((1.0F - impactProgress * 0.45F) * 125.0F);
        int ringColor = (ringAlpha << 24) | 0x00FF5366;
        drawEllipse(event, centerX, centerY, ringRadius, Math.max(8, ringRadius / 3), ringColor);
        drawEllipse(event, centerX, centerY, Math.max(12, ringRadius - 8), Math.max(6, ringRadius / 3 - 4),
                ((ringAlpha / 2) << 24) | 0x00FFB156);

        int sweepY = Math.floorMod((int) (progress * height * 9.0F), Math.max(1, height));
        event.getGuiGraphics().fill(0, sweepY, width, sweepY + 1, 0x28FF5E68);
        event.getGuiGraphics().fill(
                0,
                sweepY - 3,
                width,
                sweepY - 2,
                0x16FF8B63
        );

        int bracketSize = 18 + (int) ((1.0F - impactProgress) * 32.0F);
        int bracketColor = 0xD9FF6574;
        drawReticleCorner(event, centerX - ringRadius, centerY - ringRadius / 3, bracketSize, 4,
                bracketColor, -1, -1);
        drawReticleCorner(event, centerX + ringRadius, centerY - ringRadius / 3, bracketSize, 4,
                bracketColor, 1, -1);
        drawReticleCorner(event, centerX - ringRadius, centerY + ringRadius / 3, bracketSize, 4,
                bracketColor, -1, 1);
        drawReticleCorner(event, centerX + ringRadius, centerY + ringRadius / 3, bracketSize, 4,
                bracketColor, 1, 1);
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
                Math.max(10, height / 18 + 6),
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
                Math.max(10, height / 18 + 6) + 14,
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
