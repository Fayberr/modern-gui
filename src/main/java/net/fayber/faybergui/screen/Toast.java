package net.fayber.faybergui.screen;

import net.fayber.faybergui.render.Theme;
import net.fayber.faybergui.render.Ui;
import net.fayber.faybergui.widget.Icons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;

/**
 * A toast notification: a small card that slides in from the top right corner, sits for its
 * duration, then slides back out. Queued by the {@link PopupHost}; up to a handful stack down
 * the right edge. Toasts are passive (no click handling) and purely informational.
 *
 * <p>Factories pick an icon: {@link #info} / {@link #success} / {@link #warning} / a custom one.
 */
public class Toast {
    private static final int WIDTH = 240;
    private static final int PADDING = 12;
    private static final float RADIUS = 8.0f;
    /** Slide-in/out animation time in ms. */
    private static final long SLIDE_MS = 260;
    /** Max toasts kept on screen; the host retires the oldest first. */
    private static final int MAX_ON_SCREEN = 4;

    private final Component title;
    private final List<Component> bodyLines;
    private final Icons.Glyph icon;
    private final int iconColor;
    private final long durationMs;

    private long bornAt = -1L;
    private int slot = -1;
    private int screenW;
    private int screenH;
    private int height;
    private List<Component> titleAndBody;

    private Toast(Component title, Component body, Icons.Glyph icon, int iconColor, long durationMs) {
        this.title = title;
        this.bodyLines = Ui.wrap(Ui.ui(body), WIDTH - PADDING * 2 - 26);
        this.icon = icon;
        this.iconColor = iconColor;
        this.durationMs = durationMs;
    }

    public static Toast info(String title, String body) {
        return new Toast(Component.literal(title), Component.literal(body),
                Icons.INFO, 0xFF9EC1F7, 4000);
    }

    public static Toast success(String title, String body) {
        return new Toast(Component.literal(title), Component.literal(body),
                Icons.CHECK_CIRCLE, 0xFF9FDCA8, 4000);
    }

    public static Toast warning(String title, String body) {
        return new Toast(Component.literal(title), Component.literal(body),
                Icons.ALERT, 0xFFF7D79E, 6000);
    }

    /** A toast with a custom icon and tint (icon drawn from the given glyph). */
    public static Toast custom(String title, String body, Icons.Glyph icon, int tint, long durationMs) {
        return new Toast(Component.literal(title), Component.literal(body), icon, tint, durationMs);
    }

    // ------------------------------------------------------------------ lifecycle

    void setScreenBounds(int width, int height) {
        if (this.bornAt < 0) {
            this.bornAt = Util.getMillis();
        }
        this.screenW = width;
        this.screenH = height;
    }

    boolean isRetired() {
        return this.bornAt >= 0 && Util.getMillis() - this.bornAt > this.durationMs + SLIDE_MS;
    }

    void setSlot(int slot) {
        this.slot = slot;
    }

    // ------------------------------------------------------------------ rendering

    void extract(GuiGraphicsExtractor gfx, Theme theme, float partialTick) {
        if (this.bornAt < 0) {
            return;
        }
        long age = Util.getMillis() - this.bornAt;
        // 0..1 slide progress: 1 = fully on screen.
        float slide = age < SLIDE_MS
                ? (float) age / SLIDE_MS
                : (age > this.durationMs ? Math.clamp(1.0f - (float) (age - this.durationMs) / SLIDE_MS, 0.0f, 1.0f)
                : 1.0f);
        // Ease-out cubic for the entrance, ease-in for the exit; one curve is fine for both.
        slide = 1.0f - (1.0f - slide) * (1.0f - slide);

        if (this.titleAndBody == null) {
            this.titleAndBody = new java.util.ArrayList<>();
            this.titleAndBody.add(Ui.uiBold(this.title));
            this.titleAndBody.addAll(this.bodyLines);
            int bodyH = this.titleAndBody.size() * (Ui.font().lineHeight + 2);
            this.height = Math.max(PADDING * 2 + bodyH, 44);
        }

        int x = this.screenW - WIDTH - 12 + Math.round((1.0f - slide) * (WIDTH + 12));
        int y = 12 + this.slot * (this.height + 8);
        if (y + this.height > this.screenH - 12) {
            // Overflow below the window: drop it off-screen (host retires old toasts first).
            return;
        }

        Ui.shadow(gfx, x, y, WIDTH, this.height, RADIUS, 5.0f, 3);
        Ui.roundRect(gfx, x, y, WIDTH, this.height, RADIUS, theme.card);
        Ui.roundRectBorder(gfx, x, y, WIDTH, this.height, RADIUS, theme.card, theme.cardBorder, 1.0f);

        float iconCx = x + PADDING + 8;
        float iconCy = y + this.height / 2.0f;
        this.icon.draw(gfx, iconCx, iconCy, 16, this.iconColor);

        int textX = x + PADDING + 24;
        int textY = y + (this.height - this.titleAndBody.size() * (Ui.font().lineHeight + 2)) / 2 + 1;
        for (int i = 0; i < this.titleAndBody.size(); i++) {
            Component line = this.titleAndBody.get(i);
            Ui.text(gfx, line, textX, textY, i == 0 ? theme.text : theme.textSecondary);
            textY += Ui.font().lineHeight + 2;
        }
    }

    /** Retires the oldest toasts past the cap; called by the host before drawing. */
    static void trim(List<Toast> toasts) {
        while (toasts.size() > MAX_ON_SCREEN) {
            toasts.removeFirst();
        }
    }
}
