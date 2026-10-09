package dev.entropyinversion.client;

import dev.entropyinversion.item.AttackMode;
import net.minecraft.world.InteractionHand;

public final class ClientModHooks {
    private ClientModHooks() {
    }

    public static void openModeSelection(InteractionHand hand) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new AttackModeSelectionScreen(hand));
    }

    public static void startTargeting(InteractionHand hand) {
        TargetingMode.start(hand);
    }

    public static void beginCutscene(
            double x,
            double y,
            double z,
            int duration,
            int radius,
            AttackMode mode
    ) {
        CutsceneOverlay.begin(x, y, z, duration, radius, mode);
    }
}
