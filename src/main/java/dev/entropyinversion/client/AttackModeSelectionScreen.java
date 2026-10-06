package dev.entropyinversion.client;

import dev.entropyinversion.EntropyInversionMod;
import dev.entropyinversion.item.AttackMode;
import dev.entropyinversion.network.SelectAttackModePacket;
import dev.entropyinversion.network.StrikeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;

public final class AttackModeSelectionScreen extends Screen {
    private final InteractionHand hand;

    public AttackModeSelectionScreen(InteractionHand hand) {
        super(Component.translatable("gui.entropyinversion.attack_modes.title"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        Player player = minecraft == null ? null : minecraft.player;
        ItemStack stack = player == null ? ItemStack.EMPTY : player.getItemInHand(hand);
        AttackMode selected = AttackMode.fromStack(stack);
        int buttonWidth = 240;
        int buttonHeight = 24;
        int left = (width - buttonWidth) / 2;
        int top = height / 2 - 48;

        for (AttackMode mode : AttackMode.values()) {
            Component label = Component.translatable(mode.getNameKey());
            if (mode == selected) {
                label = Component.literal("* ").append(label);
            }
            addRenderableWidget(Button.builder(label, button -> selectMode(mode))
                    .bounds(left, top, buttonWidth, buttonHeight)
                    .tooltip(Tooltip.create(Component.translatable(mode.getDescriptionKey())))
                    .build());
            top += buttonHeight + 8;
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entropyinversion.attack_modes.done"),
                        button -> onClose()
                )
                .bounds(left, top + 4, buttonWidth, buttonHeight)
                .build());
    }

    private void selectMode(AttackMode mode) {
        if (minecraft == null || minecraft.player == null
                || !minecraft.player.getItemInHand(hand).is(EntropyInversionMod.ENTROPY_INVERSION_REQUESTOR.get())) {
            onClose();
            return;
        }

        ItemStack stack = minecraft.player.getItemInHand(hand);
        AttackMode.writeToStack(stack, mode);
        StrikeNetwork.CHANNEL.sendToServer(new SelectAttackModePacket(hand, mode.ordinal()));
        clearWidgets();
        init();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 82, 0xFFFFFFFF);
        graphics.drawCenteredString(
                font,
                Component.translatable("gui.entropyinversion.attack_modes.hint"),
                width / 2,
                height / 2 - 66,
                0xFFB8C8D8
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
