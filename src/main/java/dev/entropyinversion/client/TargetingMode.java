package dev.entropyinversion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.entropyinversion.EntropyInversionMod;
import dev.entropyinversion.item.EntropyInversionRequestorItem;
import dev.entropyinversion.network.LockStrikeTargetPacket;
import dev.entropyinversion.network.RequestStrikePacket;
import dev.entropyinversion.network.StrikeNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(
        modid = EntropyInversionMod.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class TargetingMode {
    private static final int HOLD_TO_CONFIRM_TICKS = 20;
    private static final int CIRCLE_SEGMENTS = 128;
    private static final int DEFAULT_RADIUS_SCROLL_STEP = 5;
    private static final int PRECISE_RADIUS_SCROLL_STEP = 1;
    private static final int PREVIEW_MAX_RADIUS = 42;
    private static boolean active;
    private static boolean leftButtonDown;
    private static boolean longPressHandled;
    private static int holdTicks;
    private static int strikeRadius = EntropyInversionRequestorItem.DEFAULT_STRIKE_RADIUS;
    private static Vec3 target;

    private TargetingMode() {
    }

    public static void start() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        active = true;
        leftButtonDown = false;
        longPressHandled = false;
        holdTicks = 0;
        target = null;
        clearServerTarget();
        minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 0.65F, 1.25F)
        );
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!active) {
            return;
        }

        event.setCanceled(true);
        double scrollDelta = event.getScrollDelta();
        if (scrollDelta != 0.0D) {
            int wheelNotches = Math.max(1, (int) Math.round(Math.abs(scrollDelta)));
            int step = isAltDown()
                    ? PRECISE_RADIUS_SCROLL_STEP
                    : DEFAULT_RADIUS_SCROLL_STEP;
            long radiusChange = (long) wheelNotches * step * (long) Math.signum(scrollDelta);
            int updatedRadius = (int) Math.max(
                    EntropyInversionRequestorItem.MIN_STRIKE_RADIUS,
                    Math.min(
                            EntropyInversionRequestorItem.MAX_STRIKE_RADIUS,
                            strikeRadius + radiusChange
                    )
            );
            if (updatedRadius != strikeRadius) {
                strikeRadius = updatedRadius;
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(
                                SoundEvents.UI_BUTTON_CLICK.get(),
                                0.35F,
                                isAltDown() ? 1.65F : 1.2F
                        )
                );
            }
        }
    }

    private static boolean isAltDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!active) {
            return;
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && event.getAction() == GLFW.GLFW_PRESS) {
            cancel();
            event.setCanceled(true);
        } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            event.setCanceled(true);
            if (event.getAction() == GLFW.GLFW_PRESS) {
                leftButtonDown = true;
                longPressHandled = false;
                holdTicks = 0;
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                if (leftButtonDown && !longPressHandled) {
                    updateLockedTarget(Minecraft.getInstance().player);
                }
                leftButtonDown = false;
                longPressHandled = false;
                holdTicks = 0;
            }
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (active && event.getKey() == GLFW.GLFW_KEY_ESCAPE
                && event.getAction() == GLFW.GLFW_PRESS) {
            cancel();
            Minecraft.getInstance().setScreen(null);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null
                || !hasRequestor(player)) {
            cancel();
            return;
        }

        spawnTargetingParticles(minecraft);
        if (leftButtonDown && !longPressHandled) {
            holdTicks++;
            if (holdTicks >= HOLD_TO_CONFIRM_TICKS) {
                longPressHandled = true;
                if (target == null) {
                    player.displayClientMessage(
                            Component.translatable("message.entropyinversion.no_target"),
                            true
                    );
                } else if (minecraft.getConnection() != null) {
                    active = false;
                    leftButtonDown = false;
                    StrikeNetwork.CHANNEL.sendToServer(new RequestStrikePacket(strikeRadius));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onWorldRender(RenderLevelStageEvent event) {
        if (!active || target == null
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        int bottomY = minecraft.level.getMinBuildHeight();
        int topY = minecraft.level.getMaxBuildHeight();
        int targetY = BlockPos.containing(target).getY();
        float pulse = 0.72F + 0.22F * (float) Math.sin(minecraft.level.getGameTime() * 0.18D);

        drawRing(
                lines,
                poseStack,
                target.x,
                target.z,
                targetY + 0.08D,
                strikeRadius,
                0.18F,
                0.92F,
                1.0F,
                pulse
        );
        drawTargetBlockOutline(
                lines,
                poseStack,
                BlockPos.containing(target),
                pulse
        );
        drawRing(
                lines,
                poseStack,
                target.x,
                target.z,
                bottomY + 0.02D,
                strikeRadius,
                0.1F,
                0.85F,
                1.0F,
                0.9F
        );
        drawRing(
                lines,
                poseStack,
                target.x,
                target.z,
                topY - 0.02D,
                strikeRadius,
                0.1F,
                0.85F,
                1.0F,
                0.9F
        );
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2.0D * i / 16.0D;
            double x = target.x + Math.cos(angle) * strikeRadius;
            double z = target.z + Math.sin(angle) * strikeRadius;
            drawLine(
                    lines,
                    poseStack,
                    x,
                    bottomY,
                    z,
                    x,
                    topY,
                    z,
                    0.1F,
                    0.85F,
                    1.0F,
                    0.8F
            );
        }
        minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
        poseStack.popPose();
    }

    private static void drawTargetBlockOutline(
            VertexConsumer lines,
            PoseStack poseStack,
            BlockPos blockPos,
            float pulse
    ) {
        double minX = blockPos.getX() - 0.004D;
        double minY = blockPos.getY() - 0.004D;
        double minZ = blockPos.getZ() - 0.004D;
        double maxX = blockPos.getX() + 1.004D;
        double maxY = blockPos.getY() + 1.004D;
        double maxZ = blockPos.getZ() + 1.004D;
        float cyanAlpha = 0.72F + pulse * 0.28F;

        drawLine(lines, poseStack, minX, minY, minZ, maxX, minY, minZ, 0.18F, 0.92F, 1.0F, cyanAlpha);
        drawLine(lines, poseStack, minX, minY, maxZ, maxX, minY, maxZ, 0.18F, 0.92F, 1.0F, cyanAlpha);
        drawLine(lines, poseStack, minX, maxY, minZ, maxX, maxY, minZ, 0.18F, 0.92F, 1.0F, cyanAlpha);
        drawLine(lines, poseStack, minX, maxY, maxZ, maxX, maxY, maxZ, 0.18F, 0.92F, 1.0F, cyanAlpha);
        drawLine(lines, poseStack, minX, minY, minZ, minX, maxY, minZ, 0.18F, 0.92F, 1.0F, cyanAlpha);
        drawLine(lines, poseStack, maxX, minY, minZ, maxX, maxY, minZ, 0.18F, 0.92F, 1.0F, cyanAlpha);
        drawLine(lines, poseStack, minX, minY, maxZ, minX, maxY, maxZ, 0.18F, 0.92F, 1.0F, cyanAlpha);
        drawLine(lines, poseStack, maxX, minY, maxZ, maxX, maxY, maxZ, 0.18F, 0.92F, 1.0F, cyanAlpha);
    }

    @SubscribeEvent
    public static void onHudRender(RenderGuiOverlayEvent.Post event) {
        if (!active || event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        int centerX = width / 2;
        int y = height - 96;
        event.getGuiGraphics().fill(centerX - 145, y - 6, centerX + 145, y + 56, 0xCC081522);
        event.getGuiGraphics().drawCenteredString(
                minecraft.font,
                Component.translatable("gui.entropyinversion.targeting.title"),
                centerX,
                y,
                0xFF8EEBFF
        );
        event.getGuiGraphics().drawCenteredString(
                minecraft.font,
                target == null
                        ? Component.translatable("gui.entropyinversion.targeting.no_target")
                        : Component.translatable(
                                "gui.entropyinversion.targeting.target",
                                (int) Math.floor(target.x),
                                (int) Math.floor(target.y),
                                (int) Math.floor(target.z)
                        ),
                centerX,
                y + 12,
                0xFFFFFFFF
        );
        event.getGuiGraphics().drawCenteredString(
                minecraft.font,
                Component.translatable("gui.entropyinversion.targeting.hold"),
                centerX,
                y + 25,
                0xFFFFD27A
        );
        event.getGuiGraphics().drawCenteredString(
                minecraft.font,
                Component.translatable("gui.entropyinversion.targeting.cancel"),
                centerX,
                y + 36,
                0xFFB7C9D8
        );

        int barLeft = centerX - 80;
        int barTop = y + 50;
        event.getGuiGraphics().fill(barLeft, barTop, barLeft + 160, barTop + 4, 0xFF26394A);
        int filledWidth = 160 * holdTicks / HOLD_TO_CONFIRM_TICKS;
        if (filledWidth > 0) {
            event.getGuiGraphics().fill(
                    barLeft,
                    barTop,
                    barLeft + filledWidth,
                    barTop + 4,
                    0xFFFF7755
            );
        }

        drawRadiusPreview(event, width, height);
    }

    private static void drawRadiusPreview(RenderGuiOverlayEvent.Post event, int width, int height) {
        int panelWidth = 132;
        int panelRight = width - 12;
        int panelBottom = height - 12;
        int radius = Math.max(
                3,
                strikeRadius * PREVIEW_MAX_RADIUS
                        / EntropyInversionRequestorItem.MAX_STRIKE_RADIUS
        );
        int panelLeft = panelRight - panelWidth;
        int panelTop = panelBottom - (radius * 2 + 72);
        int centerX = panelLeft + panelWidth / 2;
        int centerY = panelTop + 18 + radius;
        event.getGuiGraphics().fill(
                panelLeft,
                panelTop,
                panelRight,
                panelBottom,
                0xD9081522
        );
        event.getGuiGraphics().drawCenteredString(
                Minecraft.getInstance().font,
                Component.translatable("gui.entropyinversion.targeting.radius"),
                centerX,
                panelTop + 5,
                0xFF8EEBFF
        );

        int previousX = centerX + radius;
        int previousY = centerY;
        for (int i = 1; i <= 96; i++) {
            double angle = Math.PI * 2.0D * i / 96.0D;
            int nextX = centerX + (int) Math.round(Math.cos(angle) * radius);
            int nextY = centerY + (int) Math.round(Math.sin(angle) * radius);
            drawHudLine(event, previousX, previousY, nextX, nextY, 0xFF49DFFF);
            previousX = nextX;
            previousY = nextY;
        }

        event.getGuiGraphics().fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, 0xFFFFD27A);
        event.getGuiGraphics().fill(centerX - radius - 3, centerY - 1, centerX - radius + 2, centerY + 2, 0xFFFFD27A);
        event.getGuiGraphics().fill(centerX + radius - 1, centerY - 1, centerX + radius + 4, centerY + 2, 0xFFFFD27A);
        event.getGuiGraphics().fill(centerX - 1, centerY - radius - 3, centerX + 2, centerY - radius + 2, 0xFFFFD27A);
        event.getGuiGraphics().fill(centerX - 1, centerY + radius - 1, centerX + 2, centerY + radius + 4, 0xFFFFD27A);

        event.getGuiGraphics().drawCenteredString(
                Minecraft.getInstance().font,
                Component.translatable(
                        "gui.entropyinversion.targeting.radius_value",
                        strikeRadius
                ),
                centerX,
                centerY + radius + 11,
                0xFFFFFFFF
        );
        event.getGuiGraphics().drawCenteredString(
                Minecraft.getInstance().font,
                Component.translatable("gui.entropyinversion.targeting.height"),
                centerX,
                centerY + radius + 22,
                0xFFB7C9D8
        );
        event.getGuiGraphics().drawCenteredString(
                Minecraft.getInstance().font,
                Component.translatable("gui.entropyinversion.targeting.radius_control"),
                centerX,
                centerY + radius + 33,
                0xFFFFD27A
        );
    }

    private static void drawHudLine(
            RenderGuiOverlayEvent.Post event,
            int x0,
            int y0,
            int x1,
            int y1,
            int color
    ) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int steps = Math.max(dx, dy);
        if (steps == 0) {
            event.getGuiGraphics().fill(x0 - 1, y0 - 1, x0 + 2, y0 + 2, color);
            return;
        }

        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            event.getGuiGraphics().fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    private static void updateLockedTarget(Player player) {
        if (player == null) {
            target = null;
            return;
        }
        HitResult hit = player.pick(EntropyInversionRequestorItem.TARGETING_RANGE, 1.0F, true);
        BlockHitResult blockHit = hit instanceof BlockHitResult block
                && hit.getType() == HitResult.Type.BLOCK
                ? block
                : null;
        if (blockHit == null) {
            target = null;
        } else {
            Vec3 faceNormal = new Vec3(
                    blockHit.getDirection().getStepX(),
                    blockHit.getDirection().getStepY(),
                    blockHit.getDirection().getStepZ()
            );
            target = blockHit.getLocation().subtract(faceNormal.scale(0.001D));
        }
        StrikeNetwork.CHANNEL.sendToServer(
                new LockStrikeTargetPacket(blockHit == null ? null : blockHit.getBlockPos(), target)
        );
        if (target != null) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.BEACON_AMBIENT, 0.55F, 1.35F)
            );
        }
    }

    private static void spawnTargetingParticles(Minecraft minecraft) {
        if (target == null || minecraft.level == null || (minecraft.level.getGameTime() & 3L) != 0L) {
            return;
        }

        double time = minecraft.level.getGameTime() * 0.12D;
        double boundaryX = target.x + Math.cos(time) * strikeRadius;
        double boundaryZ = target.z + Math.sin(time) * strikeRadius;
        minecraft.level.addParticle(
                ParticleTypes.END_ROD,
                target.x,
                target.y + 0.2D,
                target.z,
                0.0D,
                0.035D,
                0.0D
        );
        minecraft.level.addParticle(
                ParticleTypes.PORTAL,
                boundaryX,
                target.y + 0.1D,
                boundaryZ,
                0.0D,
                0.015D,
                0.0D
        );
    }

    private static boolean hasRequestor(Player player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND)
                .is(EntropyInversionMod.ENTROPY_INVERSION_REQUESTOR.get())
                || player.getItemInHand(InteractionHand.OFF_HAND)
                .is(EntropyInversionMod.ENTROPY_INVERSION_REQUESTOR.get());
    }

    private static void cancel() {
        active = false;
        leftButtonDown = false;
        longPressHandled = false;
        holdTicks = 0;
        target = null;
        clearServerTarget();
    }

    private static void clearServerTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            StrikeNetwork.CHANNEL.sendToServer(new LockStrikeTargetPacket(null, null));
        }
    }

    private static void drawRing(
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
        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double a0 = Math.PI * 2.0D * i / CIRCLE_SEGMENTS;
            double a1 = Math.PI * 2.0D * (i + 1) / CIRCLE_SEGMENTS;
            drawLine(
                    lines,
                    poseStack,
                    centerX + Math.cos(a0) * radius,
                    y,
                    centerZ + Math.sin(a0) * radius,
                    centerX + Math.cos(a1) * radius,
                    y,
                    centerZ + Math.sin(a1) * radius,
                    red,
                    green,
                    blue,
                    alpha
            );
        }
    }

    private static void drawLine(
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
}
