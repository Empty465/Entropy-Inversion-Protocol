package dev.entropyinversion.client;

import dev.entropyinversion.EntropyInversionMod;
import dev.entropyinversion.item.AttackMode;
import dev.entropyinversion.network.SelectAttackModePacket;
import dev.entropyinversion.network.StrikeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public final class AttackModeSelectionScreen extends Screen {
    private static final int CARD_GAP = 7;
    private static final int CARD_MIN_HEIGHT = 72;
    private final InteractionHand hand;
    private int scrollOffset;
    private int cardLeft;
    private int cardWidth;
    private int cardAreaTop;
    private int cardAreaBottom;
    private boolean draggingScrollBar;
    private int scrollDragOffset;
    private List<ModeCard> cards = List.of();

    public AttackModeSelectionScreen(InteractionHand hand) {
        super(Component.translatable("gui.entropyinversion.attack_modes.title"));
        this.hand = hand;
    }

    private record ModeCard(
            AttackMode mode,
            int offsetY,
            int height,
            List<DescriptionBlock> description
    ) {
    }

    private record DescriptionBlock(
            String section,
            List<List<net.minecraft.util.FormattedCharSequence>> paragraphs
    ) {
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.entropyinversion.attack_modes.done"),
                        button -> onClose()
                )
                .bounds((width - Math.min(180, width - 32)) / 2, height - 34,
                        Math.min(180, width - 32), 22)
                .build());
        layoutCards();
    }

    private void layoutCards() {
        cardWidth = Math.min(440, width - 32);
        cardLeft = (width - cardWidth) / 2;
        int descriptionWidth = Math.max(100, cardWidth - 58);
        List<ModeCard> newCards = new ArrayList<>();
        int contentHeight = 0;
        for (AttackMode mode : AttackMode.values()) {
            List<DescriptionBlock> description = new ArrayList<>();
            int cardHeight = 31;
            for (String section : List.of("summary", "range", "special")) {
                String key = mode.getNameKey() + ".description." + section;
                List<List<net.minecraft.util.FormattedCharSequence>> paragraphs = new ArrayList<>();
                paragraphs.add(font.split(Component.translatable(key), descriptionWidth));
                cardHeight += 12 + paragraphs.get(0).size() * 9;
                String detailKey = key + ".detail";
                if (I18n.exists(detailKey)) {
                    List<net.minecraft.util.FormattedCharSequence> detailLines = font.split(
                            Component.translatable(detailKey),
                            descriptionWidth
                    );
                    paragraphs.add(detailLines);
                    cardHeight += 2 + detailLines.size() * 9;
                }
                description.add(new DescriptionBlock(section, List.copyOf(paragraphs)));
            }
            int cardHeightFinal = Math.max(CARD_MIN_HEIGHT, cardHeight);
            newCards.add(new ModeCard(mode, contentHeight, cardHeightFinal, List.copyOf(description)));
            contentHeight += cardHeightFinal + CARD_GAP;
        }
        cards = List.copyOf(newCards);
        if (!cards.isEmpty()) {
            contentHeight -= CARD_GAP;
        }

        int headerTop = Math.max(14, (height - contentHeight - 110) / 2);
        cardAreaTop = headerTop + 54;
        cardAreaBottom = height - 44;
        scrollOffset = Mth.clamp(scrollOffset, 0, Math.max(0, contentHeight - (cardAreaBottom - cardAreaTop)));
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
        layoutCards();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && isOverScrollBar(mouseX, mouseY)) {
            int thumbTop = getThumbTop();
            int thumbHeight = getThumbHeight();
            if (mouseY >= thumbTop && mouseY < thumbTop + thumbHeight) {
                draggingScrollBar = true;
                scrollDragOffset = (int) mouseY - thumbTop;
            } else {
                scrollDragOffset = thumbHeight / 2;
                updateScrollFromMouse(mouseY);
                draggingScrollBar = true;
            }
            return true;
        }
        if (button != 0 || mouseY < cardAreaTop || mouseY >= cardAreaBottom
                || mouseX < cardLeft || mouseX >= cardLeft + cardWidth) {
            return false;
        }
        int contentY = (int) mouseY - cardAreaTop + scrollOffset;
        for (ModeCard card : cards) {
            if (contentY >= card.offsetY && contentY < card.offsetY + card.height) {
                selectMode(card.mode);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseY >= cardAreaTop && mouseY < cardAreaBottom) {
            int maxScroll = getMaxScroll();
            int oldOffset = scrollOffset;
            scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY) * 20, 0, maxScroll);
            if (scrollOffset != oldOffset) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollBar && button == 0) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollBar) {
            draggingScrollBar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int getContentHeight() {
        return cards.isEmpty() ? 0
                : cards.get(cards.size() - 1).offsetY + cards.get(cards.size() - 1).height;
    }

    private int getMaxScroll() {
        return Math.max(0, getContentHeight() - (cardAreaBottom - cardAreaTop));
    }

    private int getScrollTrackTop() {
        return cardAreaTop + 2;
    }

    private int getScrollTrackBottom() {
        return cardAreaBottom - 14;
    }

    private int getScrollTrackX() {
        return cardLeft + cardWidth + 7;
    }

    private int getThumbHeight() {
        int trackHeight = getScrollTrackBottom() - getScrollTrackTop();
        return Math.max(22, trackHeight * (cardAreaBottom - cardAreaTop) / getContentHeight());
    }

    private int getThumbTop() {
        int trackHeight = getScrollTrackBottom() - getScrollTrackTop();
        int thumbTravel = trackHeight - getThumbHeight();
        return getScrollTrackTop() + (getMaxScroll() == 0 ? 0 : thumbTravel * scrollOffset / getMaxScroll());
    }

    private boolean isOverScrollBar(double mouseX, double mouseY) {
        return getMaxScroll() > 0
                && mouseX >= getScrollTrackX() - 5 && mouseX < getScrollTrackX() + 8
                && mouseY >= getScrollTrackTop() && mouseY < cardAreaBottom - 6;
    }

    private void updateScrollFromMouse(double mouseY) {
        int trackHeight = getScrollTrackBottom() - getScrollTrackTop();
        int thumbTravel = trackHeight - getThumbHeight();
        if (thumbTravel <= 0) {
            scrollOffset = 0;
            return;
        }
        int thumbTop = Mth.clamp(
                (int) mouseY - scrollDragOffset,
                getScrollTrackTop(),
                getScrollTrackTop() + thumbTravel
        );
        scrollOffset = (thumbTop - getScrollTrackTop()) * getMaxScroll() / thumbTravel;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0x44020A14);
        int panelLeft = cardLeft - 14;
        int panelRight = cardLeft + cardWidth + 14;
        graphics.fill(panelLeft, 8, panelRight, height - 8, 0xD906111D);
        graphics.fill(panelLeft, 8, panelRight, 9, 0xFF39D8F2);
        graphics.fill(panelLeft, height - 9, panelRight, height - 8, 0x8039D8F2);

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, title, width / 2, Math.max(14, cardAreaTop - 43), 0xFFFFFFFF);
        graphics.drawCenteredString(
                font,
                Component.translatable("gui.entropyinversion.attack_modes.hint"),
                width / 2,
                Math.max(27, cardAreaTop - 28),
                0xFF9EB8C9
        );

        graphics.enableScissor(cardLeft - 2, cardAreaTop, cardLeft + cardWidth + 2, cardAreaBottom);
        for (ModeCard card : cards) {
            int top = cardAreaTop + card.offsetY - scrollOffset;
            int bottom = top + card.height;
            if (bottom <= cardAreaTop || top >= cardAreaBottom) {
                continue;
            }
            drawModeCard(graphics, card, top, mouseX, mouseY);
        }
        graphics.disableScissor();
        drawScrollIndicator(graphics);
    }

    private void drawScrollIndicator(GuiGraphics graphics) {
        if (cards.isEmpty()) {
            return;
        }
        int contentHeight = getContentHeight();
        int viewportHeight = cardAreaBottom - cardAreaTop;
        if (contentHeight <= viewportHeight) {
            return;
        }

        int trackX = getScrollTrackX();
        int trackTop = getScrollTrackTop();
        int trackBottom = getScrollTrackBottom();
        int trackHeight = trackBottom - trackTop;
        int thumbHeight = getThumbHeight();
        int maxScroll = contentHeight - viewportHeight;
        int thumbTop = getThumbTop();

        graphics.fill(trackX, trackTop, trackX + 3, trackBottom, 0xAA263B48);
        graphics.fill(trackX - 2, thumbTop, trackX + 5, thumbTop + thumbHeight, 0xFF43DDF0);
        graphics.fill(trackX - 1, thumbTop + 3, trackX + 4, thumbTop + thumbHeight - 3, 0xFFB5F8FF);

        int arrowY = cardAreaBottom - 10;
        int arrowColor = scrollOffset < maxScroll ? 0xFFFFD27A : 0xFF617885;
        graphics.fill(trackX - 3, arrowY, trackX + 6, arrowY + 2, arrowColor);
        graphics.fill(trackX - 2, arrowY + 2, trackX + 5, arrowY + 4, arrowColor);
        graphics.fill(trackX - 1, arrowY + 4, trackX + 4, arrowY + 6, arrowColor);
    }

    private void drawModeCard(GuiGraphics graphics, ModeCard card, int top, int mouseX, int mouseY) {
        int bottom = top + card.height;
        boolean hovered = mouseX >= cardLeft && mouseX < cardLeft + cardWidth
                && mouseY >= Math.max(top, cardAreaTop) && mouseY < Math.min(bottom, cardAreaBottom);
        boolean selected = card.mode == getSelectedMode();
        int accent = accentColor(card.mode);
        int background = selected ? 0xE51A2C3A : hovered ? 0xD9142531 : 0xC90D1A26;
        graphics.fill(cardLeft, top, cardLeft + cardWidth, bottom, background);
        graphics.fill(cardLeft, top, cardLeft + cardWidth, top + 1, selected ? accent : 0x553A6070);
        graphics.fill(cardLeft, top, cardLeft + 3, bottom, selected ? accent : 0xFF223744);
        graphics.fill(cardLeft + cardWidth - 1, top, cardLeft + cardWidth, bottom, 0x663A6070);
        graphics.fill(cardLeft, bottom - 1, cardLeft + cardWidth, bottom, 0x663A6070);

        int iconLeft = cardLeft + 12;
        int iconTop = top + (card.height - 28) / 2;
        graphics.fill(iconLeft, iconTop, iconLeft + 28, iconTop + 28, 0xCC061019);
        graphics.fill(iconLeft, iconTop, iconLeft + 1, iconTop + 28, accent);
        drawModeIcon(graphics, card.mode, iconLeft + 6, iconTop + 6);

        int textLeft = cardLeft + 50;
        int titleColor = selected ? accent : 0xFFF1F6FA;
        graphics.drawString(font, Component.translatable(card.mode.getNameKey()), textLeft, top + 8,
                titleColor, false);
        int lineY = top + 24;
        for (DescriptionBlock block : card.description) {
            if (lineY + 8 >= bottom - 5) {
                break;
            }
            Component sectionLabel = Component.translatable(
                    "gui.entropyinversion.attack_modes.section." + block.section
            );
            graphics.drawString(font, sectionLabel, textLeft, lineY, accent, false);
            lineY += 10;
            for (List<net.minecraft.util.FormattedCharSequence> paragraph : block.paragraphs) {
                for (net.minecraft.util.FormattedCharSequence line : paragraph) {
                    if (lineY + 8 >= bottom - 5) {
                        break;
                    }
                    graphics.drawString(font, line, textLeft, lineY, 0xFFC0CED8, false);
                    lineY += 9;
                }
                lineY += 2;
            }
        }
        if (selected) {
            Component badge = Component.translatable("gui.entropyinversion.attack_modes.selected");
            graphics.drawString(font, badge, cardLeft + cardWidth - font.width(badge) - 10, top + 8,
                    accent, false);
        }
    }

    private AttackMode getSelectedMode() {
        Player player = minecraft == null ? null : minecraft.player;
        ItemStack stack = player == null ? ItemStack.EMPTY : player.getItemInHand(hand);
        return AttackMode.fromStack(stack);
    }

    private static int accentColor(AttackMode mode) {
        return switch (mode) {
            case ENTROPY_INVERSION -> 0xFF55E5F3;
            case ASTEROID_BOMBARDMENT -> 0xFFFFA34A;
            case ANTI_ORGANIC_MICROBOTS -> 0xFF58E8A5;
        };
    }

    private static void drawModeIcon(GuiGraphics graphics, AttackMode mode, int x, int y) {
        graphics.fill(x, y, x + 16, y + 16, 0xB408111B);
        switch (mode) {
            case ENTROPY_INVERSION -> {
                int cyan = 0xFF5DEBFF;
                graphics.fill(x + 6, y + 2, x + 10, y + 4, cyan);
                graphics.fill(x + 3, y + 4, x + 5, y + 6, cyan);
                graphics.fill(x + 11, y + 4, x + 13, y + 6, cyan);
                graphics.fill(x + 2, y + 7, x + 4, y + 10, cyan);
                graphics.fill(x + 12, y + 7, x + 14, y + 10, cyan);
                graphics.fill(x + 4, y + 11, x + 6, y + 13, cyan);
                graphics.fill(x + 10, y + 11, x + 12, y + 13, cyan);
                graphics.fill(x + 6, y + 13, x + 10, y + 15, cyan);
                graphics.fill(x + 6, y + 6, x + 10, y + 11, 0xFFFF647E);
                graphics.fill(x + 7, y + 7, x + 9, y + 10, 0xFFFFE4D0);
            }
            case ASTEROID_BOMBARDMENT -> {
                graphics.fill(x + 7, y + 0, x + 9, y + 3, 0xFFFF6C2D);
                graphics.fill(x + 5, y + 2, x + 8, y + 5, 0xFFFFC14E);
                graphics.fill(x + 8, y + 3, x + 10, y + 6, 0xFFFF852D);
                graphics.fill(x + 4, y + 6, x + 12, y + 11, 0xFF82634C);
                graphics.fill(x + 5, y + 7, x + 9, y + 12, 0xFF57473D);
                graphics.fill(x + 7, y + 8, x + 9, y + 10, 0xFFD0A16A);
                graphics.fill(x + 6, y + 11, x + 10, y + 13, 0xFF82634C);
            }
            case ANTI_ORGANIC_MICROBOTS -> {
                int green = 0xFF51F0AF;
                graphics.fill(x + 6, y + 2, x + 10, y + 6, 0xFFB6FFD5);
                graphics.fill(x + 6, y + 6, x + 10, y + 11, green);
                graphics.fill(x + 4, y + 8, x + 6, y + 10, green);
                graphics.fill(x + 10, y + 8, x + 12, y + 10, green);
                graphics.fill(x + 7, y + 11, x + 9, y + 13, green);
                graphics.fill(x + 2, y + 3, x + 4, y + 5, 0xFF9BFFD1);
                graphics.fill(x + 12, y + 3, x + 14, y + 5, 0xFF9BFFD1);
                graphics.fill(x + 2, y + 12, x + 4, y + 14, 0xFF9BFFD1);
                graphics.fill(x + 12, y + 12, x + 14, y + 14, 0xFF9BFFD1);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
