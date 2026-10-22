package net.fayber.moderngui.render;

import net.fayber.moderngui.ModernGui;
import net.fayber.moderngui.font.FontFamily;
import net.fayber.moderngui.font.FontWeight;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Crisp text at any size: 0.75x, 1x, 1.25x, 1.5x and 2x the normal 9px UI text (or whatever sizes
 * the font family offers), rendered at the real physical pixel size.
 *
 * <p>The tempting way to get big text is {@code pose().scale(1.5f)} around a normal draw call.
 * That draws the glyph bitmap that was baked for the 1x font and magnifies it, so every stem is a
 * block of texels and the glyph atlas is sampled NEAREST: the result looks pixelated. Here the
 * size is part of the font instead: for every (GUI scale, size factor, weight) a {@link
 * FontFamily} generates a font whose pixel size is exactly {@code 9 * factor * guiScale}, so a
 * "1.5x" heading is rasterised by FreeType at 1.5x and lands on whole physical pixels (see {@link
 * TextSizing}).
 *
 * <p>A requested size snaps to the nearest size the family generates. Everything is measured in
 * GUI pixels like the rest of the toolkit, and every helper takes the same size and weight (and
 * optionally family), so width, wrap and ellipsize agree with what {@link #draw} puts on screen.
 * The overloads without a family use {@link ModernGui#defaultFont}; {@code bold} means
 * {@link FontWeight#BOLD}:
 *
 * <pre>{@code
 * Component title = Component.literal("Settings");
 * SizedText.draw(gfx, title, x, y, 1.5f, true, theme.text);
 * SizedText.draw(gfx, title, x, y, 1.5f, FontWeight.MEDIUM, fancyFamily, theme.text);
 * int w = SizedText.width(title, 1.5f, true);
 * }</pre>
 *
 * <p>The y passed to the draw helpers is the top of a line box {@link #lineHeight} tall, so a
 * sized line can be centred in a row the same way the 9px text is. The generated fonts already
 * place the baseline for their size and family, so no draw-time offset is involved. Nothing here
 * is scaled by the matrix stack, so text stays sharp inside a pose that only translates.
 */
public final class SizedText {
    private SizedText() {
    }

    // ---- component -------------------------------------------------------------------------

    /** Re-styles a component into the sized font of {@code family} at {@code weight}. */
    public static Component component(Component text, float size, FontWeight weight, FontFamily family) {
        return text.copy().setStyle(text.getStyle().withFont(Ui.style(family, weight, size).getFont()));
    }

    public static Component component(Component text, float size, boolean bold) {
        return component(text, size, weightOf(bold), ModernGui.defaultFont());
    }

    /** A literal string in the sized font of {@code family} at {@code weight}. */
    public static Component component(String text, float size, FontWeight weight, FontFamily family) {
        return Component.literal(text).setStyle(Ui.style(family, weight, size));
    }

    public static Component component(String text, float size, boolean bold) {
        return component(text, size, weightOf(bold), ModernGui.defaultFont());
    }

    // ---- metrics ---------------------------------------------------------------------------

    /** Height in GUI pixels of one line box of this size at the current GUI scale. */
    public static int lineHeight(float size, FontFamily family) {
        int percent = family.snapPercent(size);
        return (int) Math.ceil(TextSizing.lineHeight(percent, family.scaleIndex(Ui.scale())));
    }

    public static int lineHeight(float size) {
        return lineHeight(size, ModernGui.defaultFont());
    }

    /** Width in GUI pixels the component takes at this size. */
    public static int width(Component text, float size, FontWeight weight, FontFamily family) {
        return Ui.font().width(component(text, size, weight, family));
    }

    public static int width(Component text, float size, boolean bold) {
        return width(text, size, weightOf(bold), ModernGui.defaultFont());
    }

    // ---- drawing ---------------------------------------------------------------------------

    /** Draws shadowless sized text with its line box's top-left at {@code (x, y)}. */
    public static void draw(GuiGraphicsExtractor gfx, Component text, int x, int y,
                            float size, FontWeight weight, FontFamily family, int color) {
        gfx.text(Ui.font(), component(text, size, weight, family), x, y, color, false);
    }

    public static void draw(GuiGraphicsExtractor gfx, Component text, int x, int y,
                            float size, boolean bold, int color) {
        draw(gfx, text, x, y, size, weightOf(bold), ModernGui.defaultFont(), color);
    }

    /** Sized text centred horizontally on {@code cx}. */
    public static void drawCentered(GuiGraphicsExtractor gfx, Component text, int cx, int y,
                                    float size, FontWeight weight, FontFamily family, int color) {
        Component sized = component(text, size, weight, family);
        gfx.text(Ui.font(), sized, cx - Ui.font().width(sized) / 2, y, color, false);
    }

    public static void drawCentered(GuiGraphicsExtractor gfx, Component text, int cx, int y,
                                    float size, boolean bold, int color) {
        drawCentered(gfx, text, cx, y, size, weightOf(bold), ModernGui.defaultFont(), color);
    }

    /** Sized text whose right edge sits at {@code right}. */
    public static void drawRight(GuiGraphicsExtractor gfx, Component text, int right, int y,
                                 float size, FontWeight weight, FontFamily family, int color) {
        Component sized = component(text, size, weight, family);
        gfx.text(Ui.font(), sized, right - Ui.font().width(sized), y, color, false);
    }

    public static void drawRight(GuiGraphicsExtractor gfx, Component text, int right, int y,
                                 float size, boolean bold, int color) {
        drawRight(gfx, text, right, y, size, weightOf(bold), ModernGui.defaultFont(), color);
    }

    // ---- fitting ---------------------------------------------------------------------------

    /** {@link Ui#ellipsize} measured in the sized font. */
    public static Component ellipsize(Component text, float size, FontWeight weight, FontFamily family, int maxWidth) {
        return Ui.ellipsize(component(text, size, weight, family), maxWidth);
    }

    public static Component ellipsize(Component text, float size, boolean bold, int maxWidth) {
        return ellipsize(text, size, weightOf(bold), ModernGui.defaultFont(), maxWidth);
    }

    /** {@link Ui#wrap} measured in the sized font; every line keeps the sized style. */
    public static List<Component> wrap(Component text, float size, FontWeight weight, FontFamily family, int maxWidth) {
        return Ui.wrap(component(text, size, weight, family), maxWidth);
    }

    public static List<Component> wrap(Component text, float size, boolean bold, int maxWidth) {
        return wrap(text, size, weightOf(bold), ModernGui.defaultFont(), maxWidth);
    }

    private static FontWeight weightOf(boolean bold) {
        return bold ? FontWeight.BOLD : FontWeight.REGULAR;
    }
}
