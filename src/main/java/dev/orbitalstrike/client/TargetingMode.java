package dev.orbitalstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.orbitalstrike.OrbitalStrikeMod;
import dev.orbitalstrike.item.OrbitalRequestorItem;
import dev.orbitalstrike.network.RequestStrikePacket;
import dev.orbitalstrike.network.StrikeNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
        modid = OrbitalStrikeMod.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class TargetingMode {
    private static final int HOLD_TO_CONFIRM_TICKS = 20;
    private static final int CIRCLE_SEGMENTS = 128;
    private static final double STRIKE_RADIUS = 200.0D;
    private static boolean active;
    private static boolean leftButtonDown;
    private static int holdTicks;
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
        holdTicks = 0;
        updateTarget(minecraft.player);
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
                holdTicks = 0;
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                leftButtonDown = false;
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

        updateTarget(player);
        if (leftButtonDown) {
            holdTicks++;
            if (holdTicks >= HOLD_TO_CONFIRM_TICKS) {
                active = false;
                leftButtonDown = false;
                holdTicks = 0;
                if (target == null) {
                    player.displayClientMessage(
                            Component.translatable("message.orbitalstrike.no_target"),
                            true
                    );
                } else if (minecraft.getConnection() != null) {
                    StrikeNetwork.CHANNEL.sendToServer(new RequestStrikePacket());
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

        drawRing(lines, poseStack, target.x, target.z, targetY + 0.08D, 0.1F, 0.98F, 1.0F, 1.0F);
        drawRing(lines, poseStack, target.x, target.z, bottomY + 0.02D, 0.1F, 0.85F, 1.0F, 0.9F);
        drawRing(lines, poseStack, target.x, target.z, topY - 0.02D, 0.1F, 0.85F, 1.0F, 0.9F);
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2.0D * i / 16.0D;
            double x = target.x + Math.cos(angle) * STRIKE_RADIUS;
            double z = target.z + Math.sin(angle) * STRIKE_RADIUS;
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

    @SubscribeEvent
    public static void onHudRender(RenderGuiOverlayEvent.Post event) {
        if (!active || event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        int centerX = width / 2;
        int y = height - 82;
        event.getGuiGraphics().fill(centerX - 115, y - 6, centerX + 115, y + 43, 0xCC081522);
        event.getGuiGraphics().drawCenteredString(
                minecraft.font,
                Component.translatable("gui.orbitalstrike.targeting.title"),
                centerX,
                y,
                0xFF8EEBFF
        );
        event.getGuiGraphics().drawCenteredString(
                minecraft.font,
                target == null
                        ? Component.translatable("gui.orbitalstrike.targeting.no_target")
                        : Component.translatable(
                                "gui.orbitalstrike.targeting.target",
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
                Component.translatable("gui.orbitalstrike.targeting.hold"),
                centerX,
                y + 25,
                0xFFFFD27A
        );

        int barLeft = centerX - 80;
        int barTop = y + 39;
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

        drawRadiusPreview(event, width - 62, height / 2 - 30);
    }

    private static void drawRadiusPreview(RenderGuiOverlayEvent.Post event, int centerX, int centerY) {
        int radius = 34;
        int panelLeft = centerX - 49;
        int panelTop = centerY - 58;
        event.getGuiGraphics().fill(
                panelLeft,
                panelTop,
                panelLeft + 98,
                panelTop + 120,
                0xD9081522
        );
        event.getGuiGraphics().drawCenteredString(
                Minecraft.getInstance().font,
                Component.translatable("gui.orbitalstrike.targeting.radius"),
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
                Component.translatable("gui.orbitalstrike.targeting.radius_value"),
                centerX,
                centerY + radius + 12,
                0xFFFFFFFF
        );
        event.getGuiGraphics().drawCenteredString(
                Minecraft.getInstance().font,
                Component.translatable("gui.orbitalstrike.targeting.height"),
                centerX,
                centerY + radius + 23,
                0xFFB7C9D8
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

    private static void updateTarget(Player player) {
        HitResult hit = player.pick(OrbitalRequestorItem.TARGETING_RANGE, 1.0F, false);
        target = hit instanceof BlockHitResult blockHit
                && hit.getType() == HitResult.Type.BLOCK
                ? blockHit.getLocation()
                : null;
    }

    private static boolean hasRequestor(Player player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND)
                .is(OrbitalStrikeMod.ORBITAL_REQUESTOR.get())
                || player.getItemInHand(InteractionHand.OFF_HAND)
                .is(OrbitalStrikeMod.ORBITAL_REQUESTOR.get());
    }

    private static void cancel() {
        active = false;
        leftButtonDown = false;
        holdTicks = 0;
        target = null;
    }

    private static void drawRing(
            VertexConsumer lines,
            PoseStack poseStack,
            double centerX,
            double centerZ,
            double y,
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
                    centerX + Math.cos(a0) * STRIKE_RADIUS,
                    y,
                    centerZ + Math.sin(a0) * STRIKE_RADIUS,
                    centerX + Math.cos(a1) * STRIKE_RADIUS,
                    y,
                    centerZ + Math.sin(a1) * STRIKE_RADIUS,
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
