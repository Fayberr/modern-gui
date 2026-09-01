package net.fayber.moderngui.screen;

import net.fayber.moderngui.render.Theme;
import net.fayber.moderngui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Draws a rich tooltip: a floating dark card next to the cursor with a bold title and wrapped
 * body lines. Purely a renderer; the {@link PopupHost} owns the hover-delay logic that calls it.
 */
final class RichTooltipRenderer {
    private static final int PADDING = 10;
    private static final int GAP = 14;
    private static final float RADIUS = 6.0f;
    private static final int MAX_WIDTH = 240;

    private RichTooltipRenderer() {
    }

    static void draw(GuiGraphicsExtractor gfx, Theme theme, Component title, Component body, int mouseX, int mouseY) {
        List<Component> bodyLines = Ui.wrap(Ui.ui(body), MAX_WIDTH - PADDING * 2);

        int textW = Ui.font().width(Ui.uiBold(title));
        for (Component line : bodyLines) {
            textW = Math.max(textW, Ui.font().width(line));
        }
        int width = Math.min(textW + PADDING * 2, MAX_WIDTH);
        int height = PADDING * 2 - 2 + Ui.font().lineHeight + 2 + bodyLines.size() * (Ui.font().lineHeight + 2);

        var mc = net.minecraft.client.Minecraft.getInstance();
        int x = mouseX + GAP;
        int y = mouseY - height - GAP;
        if (x + width > mc.getWindow().getGuiScaledWidth() - 2) {
            x = mouseX - width - GAP;
        }
        if (y < 2) {
            y = mouseY + GAP;
        }
        x = Math.clamp(x, 2, mc.getWindow().getGuiScaledWidth() - width - 2);
        y = Math.clamp(y, 2, mc.getWindow().getGuiScaledHeight() - height - 2);

        Ui.shadow(gfx, x, y, width, height, RADIUS, 4.0f, 3);
        Ui.roundRect(gfx, x, y, width, height, RADIUS, 0xF0141414);
        Ui.roundRectBorder(gfx, x, y, width, height, RADIUS, 0xF0141414, theme.cardBorderHover, 1.0f);

        int lineY = y + PADDING - 1;
        Ui.text(gfx, Ui.uiBold(title), x + PADDING, lineY, theme.text);
        lineY += Ui.font().lineHeight + 2;
        for (Component line : bodyLines) {
            Ui.text(gfx, line, x + PADDING, lineY, theme.textSecondary);
            lineY += Ui.font().lineHeight + 2;
        }
    }
}
