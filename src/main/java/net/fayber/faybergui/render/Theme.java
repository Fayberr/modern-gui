package net.fayber.faybergui.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The Fayber GUI palette. Every widget and screen reads its colours through a Theme instance, so a
 * mod can re-skin the whole toolkit by handing its own Theme to the widgets it builds
 * ({@code widget.theme(myTheme)}), including an optional accent colour via {@link #withAccent}.
 *
 * <p>{@link #dark()} is the default: a genuinely neutral dark ramp, identical to the original
 * Fayber Config palette. The surface greys have equal R/G/B so nothing reads as tinted, and there
 * is deliberately no accent colour. Emphasis is carried by lightness alone: a near-white
 * fill marks the confirming button and the "on" state of a toggle, which keeps the screen quiet
 * and lets the mod's own content be the only real colour on it. (The {@code accent} fields hold
 * that neutral light fill by default; {@code withAccent} swaps them to a real colour.)
 *
 * <p>Themes are immutable: every fluent setter returns a new instance, so the shared default can
 * never be mutated by accident. Colours are ARGB ints.
 */
public final class Theme {
    private static final Theme DARK = new Theme(
            0xC6000000, // scrim
            0xFF1A1A1A, 0xFF222222, 0xFF262626, 0xFF3A3A3A, // card, cardHover, cardBorder, cardBorderHover
            0xFFF0F0F0, 0xFFA3A3A3, 0xFF6E6E6E, // text, textSecondary, textMuted
            0xFF121212, 0xFFE6E6E6, 0xFFFFFFFF, // textOnAccent, accent, accentHover
            0xFF3A3A3A, // offTrack
            0xFF2E2E2E, 0xFF3A3A3A, 0xFF7A7A7A, 0xFF9A9A9A, // slider track/fill + hovers
            0xFF3A3A3A, 0xFF4D4D4D // scrollbar, scrollbarHover
    );

    /** Dim laid over the world/menu behind the cards. */
    public final int scrim;

    public final int card;
    public final int cardHover;
    public final int cardBorder;
    public final int cardBorderHover;

    public final int text;
    public final int textSecondary;
    public final int textMuted;
    /** Dark label for text sitting on top of the light {@link #accent} fill. */
    public final int textOnAccent;

    /** Fill for the confirming button and the "on" state of a toggle (near-white by default). */
    public final int accent;
    public final int accentHover;

    /** Dark track of an off toggle. */
    public final int offTrack;

    public final int sliderTrack;
    public final int sliderTrackHover;
    /** Filled part of a slider track, mid grey so the white knob reads against it. */
    public final int sliderFill;
    public final int sliderFillHover;

    public final int scrollbar;
    public final int scrollbarHover;

    private Theme(int scrim, int card, int cardHover, int cardBorder, int cardBorderHover,
                  int text, int textSecondary, int textMuted, int textOnAccent,
                  int accent, int accentHover, int offTrack,
                  int sliderTrack, int sliderTrackHover, int sliderFill, int sliderFillHover,
                  int scrollbar, int scrollbarHover) {
        this.scrim = scrim;
        this.card = card;
        this.cardHover = cardHover;
        this.cardBorder = cardBorder;
        this.cardBorderHover = cardBorderHover;
        this.text = text;
        this.textSecondary = textSecondary;
        this.textMuted = textMuted;
        this.textOnAccent = textOnAccent;
        this.accent = accent;
        this.accentHover = accentHover;
        this.offTrack = offTrack;
        this.sliderTrack = sliderTrack;
        this.sliderTrackHover = sliderTrackHover;
        this.sliderFill = sliderFill;
        this.sliderFillHover = sliderFillHover;
        this.scrollbar = scrollbar;
        this.scrollbarHover = scrollbarHover;
    }

    /** The default neutral dark theme. Shared instance; immutable, so handing it out is safe. */
    public static Theme dark() {
        return DARK;
    }

    /**
     * Returns a theme with a real accent colour. The accent drives the confirming button and the
     * "on" state of a toggle; its hover variant is a lighter step of the same hue and the label on
     * top of it flips to a dark tone with matching luminance, so the defaults stay readable.
     */
    public Theme withAccent(int accentColor) {
        return new Theme(this.scrim,
                this.card, this.cardHover, this.cardBorder, this.cardBorderHover,
                this.text, this.textSecondary, this.textMuted, this.textOnAccent,
                accentColor, lighten(accentColor, 0.14f), this.offTrack,
                this.sliderTrack, this.sliderTrackHover, this.sliderFill, this.sliderFillHover,
                this.scrollbar, this.scrollbarHover);
    }

    /** Lightens an ARGB colour by mixing it with white; keeps the alpha channel. */
    public static int lighten(int color, float amount) {
        int a = color & 0xFF000000;
        float r = (color >> 16 & 0xFF) / 255.0f;
        float g = (color >> 8 & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        r += (1.0f - r) * amount;
        g += (1.0f - g) * amount;
        b += (1.0f - b) * amount;
        return a | ((int) (r * 255.0f) << 16) | ((int) (g * 255.0f) << 8) | (int) (b * 255.0f);
    }

    /** Darkens an ARGB colour by mixing it with black; keeps the alpha channel. */
    public static int darken(int color, float amount) {
        int a = color & 0xFF000000;
        int r = Math.round((color >> 16 & 0xFF) * (1.0f - amount));
        int g = Math.round((color >> 8 & 0xFF) * (1.0f - amount));
        int b = Math.round((color & 0xFF) * (1.0f - amount));
        return a | (r << 16) | (g << 8) | b;
    }

    public Theme scrim(int v) {
        return new Theme(v, this.card, this.cardHover, this.cardBorder, this.cardBorderHover,
                this.text, this.textSecondary, this.textMuted, this.textOnAccent,
                this.accent, this.accentHover, this.offTrack,
                this.sliderTrack, this.sliderTrackHover, this.sliderFill, this.sliderFillHover,
                this.scrollbar, this.scrollbarHover);
    }

    public Theme card(int v, int hover, int border, int borderHover) {
        return new Theme(this.scrim, v, hover, border, borderHover,
                this.text, this.textSecondary, this.textMuted, this.textOnAccent,
                this.accent, this.accentHover, this.offTrack,
                this.sliderTrack, this.sliderTrackHover, this.sliderFill, this.sliderFillHover,
                this.scrollbar, this.scrollbarHover);
    }

    public Theme text(int v, int secondary, int muted) {
        return new Theme(this.scrim, this.card, this.cardHover, this.cardBorder, this.cardBorderHover,
                v, secondary, muted, this.textOnAccent,
                this.accent, this.accentHover, this.offTrack,
                this.sliderTrack, this.sliderTrackHover, this.sliderFill, this.sliderFillHover,
                this.scrollbar, this.scrollbarHover);
    }

    /** Filled rounded rectangle. */
    public static void fillRound(GuiGraphicsExtractor gfx, float x, float y, float w, float h, float radius, int color) {
        Ui.roundRect(gfx, x, y, w, h, radius, color);
    }

    /** Rounded card with a hairline border. */
    public static void fillRoundCard(GuiGraphicsExtractor gfx, float x, float y, float w, float h, float radius,
                                     int borderColor, int fillColor) {
        Ui.roundRectBorder(gfx, x, y, w, h, radius, fillColor, borderColor, 1.0f);
    }
}
