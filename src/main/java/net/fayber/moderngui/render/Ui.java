package net.fayber.moderngui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fayber.moderngui.ModernGuiClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The drawing layer that makes Modern GUI look like a modern app instead of a Minecraft screen.
 *
 * <p>Two things cause the vanilla "blocky" look, and this class fixes both:
 *
 * <ol>
 *   <li>The GUI-scale grid: everything vanilla draws is snapped to GUI pixels, so at GUI
 *       scale 3 a one-pixel border is three screen pixels thick and a rounded corner is a visible
 *       staircase. Every primitive here takes float coordinates in GUI space but draws with the
 *       matrix stack scaled to physical screen pixels, so shapes get the full resolution
 *       of the monitor. Layout and mouse hit-testing stay in GUI space, so widgets are unaffected.
 *   <li>Hard-edged corners: corners go through {@link GuiPipelines#roundCorner()}, whose
 *       shader computes circular coverage and anti-aliases it per pixel.
 * </ol>
 *
 * <p>Text uses the bundled Inter font via {@link #ui}/{@link #uiBold} rather than the vanilla
 * bitmap font, at a rasterisation that matches the monitor exactly (see {@link #style}).
 */
public final class Ui {
    /**
     * The font is bundled once per GUI scale. Minecraft rasterises a TTF glyph at
     * {@code size * oversample} texels and then draws it {@code size} GUI pixels tall, so on screen
     * one texel covers {@code oversample / guiScale} pixels, and the glyph atlas is sampled
     * NEAREST ({@code FontTexture} uses {@code SamplerCache.getRepeat(FilterMode.NEAREST)}).
     *
     * <p>Any ratio other than 1 therefore resamples the glyph with no filtering, which is what makes
     * TTF text in Minecraft look subtly jagged: a single fixed {@code oversample} is only ever right
     * at one GUI scale. Picking the variant whose oversample equals the current GUI scale makes it
     * exactly one texel per physical pixel, and it also makes every glyph advance and bearing
     * (which Minecraft stores as {@code raster / oversample}) land on a whole physical pixel.
     */
    private static final int MAX_SCALE = 6;

    private static final Style[] STYLES = new Style[MAX_SCALE + 1];
    private static final Style[] STYLES_BOLD = new Style[MAX_SCALE + 1];

    static {
        for (int i = 1; i <= MAX_SCALE; i++) {
            // 26.1 selects fonts through FontDescription, not a bare Identifier.
            STYLES[i] = Style.EMPTY.withFont(new FontDescription.Resource(
                    Identifier.fromNamespaceAndPath(ModernGuiClient.MOD_ID, "ui_x" + i)));
            STYLES_BOLD[i] = Style.EMPTY.withFont(new FontDescription.Resource(
                    Identifier.fromNamespaceAndPath(ModernGuiClient.MOD_ID, "ui_bold_x" + i)));
        }
    }

    private Ui() {
    }

    /** The Inter style whose rasterisation matches the current GUI scale one texel per pixel. */
    private static Style style(boolean bold) {
        int i = Math.clamp(Math.round(scale()), 1, MAX_SCALE);
        return bold ? STYLES_BOLD[i] : STYLES[i];
    }

    /** Wraps text in the Inter UI font. */
    public static Component ui(String text) {
        return Component.literal(text).setStyle(style(false));
    }

    /** Wraps text in the Inter UI font, semibold. */
    public static Component uiBold(String text) {
        return Component.literal(text).setStyle(style(true));
    }

    /** Re-styles an arbitrary component (and its children) into the Inter UI font. */
    public static Component ui(Component text) {
        return text.copy().setStyle(text.getStyle().withFont(style(false).getFont()));
    }

    /** Re-styles an arbitrary component into the semibold Inter UI font. */
    public static Component uiBold(Component text) {
        return text.copy().setStyle(text.getStyle().withFont(style(true).getFont()));
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    /**
     * Draws text without the vanilla drop shadow. That shadow is the other half of why MC screens
     * look dated: it is a hard one-GUI-pixel offset copy, so at GUI scale 3 every label sits on a
     * 3px black smear.
     */
    public static void text(GuiGraphicsExtractor gfx, Component text, int x, int y, int color) {
        gfx.text(font(), text, x, y, color, false);
    }

    /** Shadowless text centred on {@code cx}. */
    public static void textCentered(GuiGraphicsExtractor gfx, Component text, int cx, int y, int color) {
        gfx.text(font(), text, cx - font().width(text) / 2, y, color, false);
    }

    /** Shadowless text whose right edge sits at {@code right}. */
    public static void textRight(GuiGraphicsExtractor gfx, Component text, int right, int y, int color) {
        gfx.text(font(), text, right - font().width(text), y, color, false);
    }

    /**
     * Truncates a component to {@code maxWidth} GUI pixels with an ellipsis. Useful for labels in
     * fixed-width rows; returns the input unchanged when it already fits.
     */
    public static Component ellipsize(Component text, int maxWidth) {
        if (font().width(text) <= maxWidth) {
            return text;
        }
        Component ellipsis = Component.literal("...").setStyle(text.getStyle());
        int budget = maxWidth - font().width(ellipsis);
        String raw = text.getString();
        int end = raw.length();
        while (end > 0 && font().width(Component.literal(raw.substring(0, end)).setStyle(text.getStyle())) > budget) {
            end--;
        }
        return Component.literal(raw.substring(0, end)).setStyle(text.getStyle())
                .copy().append(ellipsis);
    }

    /**
     * Greedy word wrap into lines that each fit {@code maxWidth} GUI pixels. Splits on spaces;
     * words longer than the line are hard-split. Keeps the input's font style on every line.
     */
    public static List<Component> wrap(Component text, int maxWidth) {
        List<Component> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.getString().split(" ", -1)) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font().width(Component.literal(candidate).setStyle(text.getStyle())) > maxWidth) {
                lines.add(Component.literal(line.toString()).setStyle(text.getStyle()));
                line.setLength(0);
                // Hard-split a word wider than a whole line.
                while (font().width(Component.literal(word).setStyle(text.getStyle())) > maxWidth && word.length() > 1) {
                    int cut = word.length() - 1;
                    while (cut > 1 && font().width(Component.literal(word.substring(0, cut)).setStyle(text.getStyle())) > maxWidth) {
                        cut--;
                    }
                    lines.add(Component.literal(word.substring(0, cut)).setStyle(text.getStyle()));
                    word = word.substring(cut);
                }
            }
            if (line.isEmpty()) {
                line.append(word);
            } else {
                line.append(' ').append(word);
            }
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(Component.literal(line.toString()).setStyle(text.getStyle()));
        }
        return lines;
    }

    /** Physical pixels per GUI pixel. */
    public static float scale() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getWindow() == null ? 1.0f : (float) mc.getWindow().getGuiScale();
    }

    /** Plain rectangle, drawn at physical-pixel precision (so it can sit on half pixels). */
    public static void rect(GuiGraphicsExtractor gfx, float x, float y, float w, float h, int color) {
        float s = scale();
        gfx.pose().pushMatrix();
        gfx.pose().scale(1.0f / s, 1.0f / s);
        int x0 = Math.round(x * s);
        int y0 = Math.round(y * s);
        gfx.fill(x0, y0, x0 + Math.round(w * s), y0 + Math.round(h * s), color);
        gfx.pose().popMatrix();
    }

    /** Filled rounded rectangle with anti-aliased corners. Coordinates are GUI-space floats. */
    public static void roundRect(GuiGraphicsExtractor gfx, float x, float y, float w, float h, float radius, int color) {
        float s = scale();
        gfx.pose().pushMatrix();
        gfx.pose().scale(1.0f / s, 1.0f / s);
        roundRectDevice(gfx, Math.round(x * s), Math.round(y * s), Math.round(w * s), Math.round(h * s),
                Math.round(radius * s), color);
        gfx.pose().popMatrix();
    }

    /**
     * Rounded rectangle with a border: the border is drawn as the outer shape and the fill is
     * inset into it, which keeps both edges anti-aliased.
     */
    public static void roundRectBorder(GuiGraphicsExtractor gfx, float x, float y, float w, float h,
                                       float radius, int fill, int border, float thickness) {
        float s = scale();
        int t = Math.max(1, Math.round(thickness * s));
        int x0 = Math.round(x * s);
        int y0 = Math.round(y * s);
        int w0 = Math.round(w * s);
        int h0 = Math.round(h * s);
        int r0 = Math.round(radius * s);
        if ((fill >>> 24) == 0) {
            // A fully transparent fill cannot be painted over the outer shape to "cut" the
            // interior out (a zero-alpha fill is a blend no-op, so the border would cover the
            // whole rect). Paint the ring directly instead: four edge strips plus the corner
            // bands. The corners must be BANDS, not the full quarter discs the opaque path
            // uses: a disc is solid, so on a hairline ring it renders as a fat blob over the
            // corner (the interior here belongs to whatever the caller drew).
            gfx.pose().pushMatrix();
            gfx.pose().scale(1.0f / s, 1.0f / s);
            gfx.fill(x0 + r0, y0, x0 + w0 - r0, y0 + t, border);
            gfx.fill(x0 + r0, y0 + h0 - t, x0 + w0 - r0, y0 + h0, border);
            gfx.fill(x0, y0 + r0, x0 + t, y0 + h0 - r0, border);
            gfx.fill(x0 + w0 - t, y0 + r0, x0 + w0, y0 + h0 - r0, border);
            cornerRing(gfx, x0, y0, r0, t, 0, 0, border);
            cornerRing(gfx, x0 + w0 - r0, y0, r0, t, 1, 0, border);
            cornerRing(gfx, x0, y0 + h0 - r0, r0, t, 0, 1, border);
            cornerRing(gfx, x0 + w0 - r0, y0 + h0 - r0, r0, t, 1, 1, border);
            gfx.pose().popMatrix();
            return;
        }
        gfx.pose().pushMatrix();
        gfx.pose().scale(1.0f / s, 1.0f / s);
        roundRectDevice(gfx, x0, y0, w0, h0, r0, border);
        roundRectDevice(gfx, x0 + t, y0 + t, w0 - 2 * t, h0 - 2 * t, Math.max(0, r0 - t), fill);
        gfx.pose().popMatrix();
    }

    /** Fully rounded capsule (pill), used for toggles and slider knobs. */
    public static void pill(GuiGraphicsExtractor gfx, float x, float y, float w, float h, int color) {
        roundRect(gfx, x, y, w, h, Math.min(w, h) / 2.0f, color);
    }

    /**
     * Anti-aliased filled circle.
     *
     * <p>The device box is derived from the already-rounded radius instead of going through
     * {@link #roundRect}, which rounds the box and the radius independently: a fractional radius
     * (3.5 GUI at scale 3) there produces an odd 21px box whose radius clamps down a pixel, and
     * the misfit leaves a 1px slab between the corner quads. On the picker's cursor that slab
     * read as a hairline tick through the disc, appearing and vanishing with the cursor's
     * sub-pixel position. With the box cut as exactly 2r the four corner quads tile it edge to
     * edge with no slabs and no seam, so the disc renders identically everywhere.
     */
    public static void circle(GuiGraphicsExtractor gfx, float cx, float cy, float radius, int color) {
        float s = scale();
        int r0 = Math.max(0, Math.round(radius * s));
        if (r0 == 0) {
            return;
        }
        int x0 = Math.round(cx * s);
        int y0 = Math.round(cy * s);
        gfx.pose().pushMatrix();
        gfx.pose().scale(1.0f / s, 1.0f / s);
        roundRectDevice(gfx, x0 - r0, y0 - r0, r0 * 2, r0 * 2, r0, color);
        gfx.pose().popMatrix();
    }

    /**
     * Soft drop shadow: concentric rounded rectangles with rising alpha. Cheap (a handful of
     * quads) and enough to lift a panel off the world behind it.
     */
    public static void shadow(GuiGraphicsExtractor gfx, float x, float y, float w, float h, float radius,
                              float spread, int steps) {
        for (int i = steps; i >= 1; i--) {
            float grow = spread * i / steps;
            int alpha = (int) (10.0f * (1.0f - (float) i / (steps + 1)));
            if (alpha <= 0) {
                continue;
            }
            roundRect(gfx, x - grow, y - grow, w + grow * 2.0f, h + grow * 2.0f, radius + grow, alpha << 24);
        }
    }

    /** Rounded rectangle in physical pixels; assumes the caller already scaled the matrix. */
    private static void roundRectDevice(GuiGraphicsExtractor gfx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            gfx.fill(x, y, x + w, y + h, color);
            return;
        }

        // Body: a full-height centre column plus the two side slabs between the corner arcs.
        gfx.fill(x + r, y, x + w - r, y + h, color);
        gfx.fill(x, y + r, x + r, y + h - r, color);
        gfx.fill(x + w - r, y + r, x + w, y + h - r, color);

        // Corners: each quad maps one quadrant of the unit circle (see round_corner.fsh), picked so
        // the circle centre lands on the corner's inner side.
        corner(gfx, x, y, r, 0, 0, color);
        corner(gfx, x + w - r, y, r, 1, 0, color);
        corner(gfx, x, y + h - r, r, 0, 1, color);
        corner(gfx, x + w - r, y + h - r, r, 1, 1, color);
    }

    private static void corner(GuiGraphicsExtractor gfx, int x, int y, int r, int uHalf, int vHalf, int color) {
        RenderPipeline pipeline = GuiPipelines.roundCorner();
        if (pipeline != null) {
            gfx.blit(pipeline, GuiPipelines.WHITE, x, y, uHalf * r, vHalf * r, r, r, 2 * r, 2 * r, color);
            return;
        }
        cornerFallback(gfx, x, y, r, uHalf, vHalf, color);
    }

    /**
     * Corner drawn as one fill per physical-pixel row when the shader pipeline is unavailable.
     * Not anti-aliased, but still a real arc at monitor resolution rather than a GUI-pixel staircase.
     */
    private static void cornerFallback(GuiGraphicsExtractor gfx, int x, int y, int r, int uHalf, int vHalf, int color) {
        for (int row = 0; row < r; row++) {
            // Distance from the arc's centre, which sits on the corner's inner side.
            float dy = (vHalf == 0 ? (r - row - 0.5f) : (row + 0.5f));
            float halfSpan = (float) Math.sqrt(Math.max(0.0f, r * r - dy * dy));
            int span = Math.round(halfSpan);
            if (span <= 0) {
                continue;
            }
            int x0 = uHalf == 0 ? x + r - span : x;
            gfx.fill(x0, y + row, x0 + span, y + row + 1, color);
        }
    }

    /**
     * One corner of a hairline ring: the band between the outer arc (radius r) and the inner
     * arc (radius r-t), one fill per physical-pixel row. Same fallback geometry as
     * {@link #cornerFallback}, narrowed to the band, since a full quarter disc would read as a
     * solid blob on anything thinner than the radius.
     */
    private static void cornerRing(GuiGraphicsExtractor gfx, int x, int y, int r, int t,
                                   int uHalf, int vHalf, int color) {
        int inner = Math.max(0, r - t);
        for (int row = 0; row < r; row++) {
            float dy = (vHalf == 0 ? (r - row - 0.5f) : (row + 0.5f));
            if (dy >= r) {
                continue;
            }
            // Band extent measured inward from the corner tip edge: the outer arc start and,
            // when this row reaches the inner circle, where that circle ends the band.
            int from = r - Math.round((float) Math.sqrt(r * r - dy * dy));
            int to = r;
            if (dy < inner) {
                to = r - Math.round((float) Math.sqrt(inner * (float) inner - dy * dy));
            }
            if (to <= from) {
                continue;
            }
            int x0 = uHalf == 0 ? x + from : x + r - to;
            gfx.fill(x0, y + row, x0 + to - from, y + row + 1, color);
        }
    }

    /**
     * Squares off the corners of a rect the caller drew edge to edge: paints the part of each
     * corner square that lies OUTSIDE the quarter-circle arc, so a gradient (or any full-bleed
     * fill) reads as rounded under a hairline {@link #roundRectBorder} ring drawn on top. The
     * colour must match the surface behind the rect; there is no erasing in this pipeline.
     */
    public static void cornerCut(GuiGraphicsExtractor gfx, float x, float y, float w, float h,
                                 float radius, int color) {
        float s = scale();
        int r = Math.round(radius * s);
        int x0 = Math.round(x * s);
        int y0 = Math.round(y * s);
        int w0 = Math.round(w * s);
        int h0 = Math.round(h * s);
        r = Math.max(0, Math.min(r, Math.min(w0, h0) / 2));
        if (r == 0) {
            return;
        }
        gfx.pose().pushMatrix();
        gfx.pose().scale(1.0f / s, 1.0f / s);
        cutCorner(gfx, x0, y0, r, 0, 0, color);
        cutCorner(gfx, x0 + w0 - r, y0, r, 1, 0, color);
        cutCorner(gfx, x0, y0 + h0 - r, r, 0, 1, color);
        cutCorner(gfx, x0 + w0 - r, y0 + h0 - r, r, 1, 1, color);
        gfx.pose().popMatrix();
    }

    /** One corner of {@link #cornerCut}: the wedge between the corner tip edge and the arc. */
    private static void cutCorner(GuiGraphicsExtractor gfx, int x, int y, int r, int uHalf, int vHalf, int color) {
        for (int row = 0; row < r; row++) {
            float dy = (vHalf == 0 ? (r - row - 0.5f) : (row + 0.5f));
            int to = r - Math.round((float) Math.sqrt(Math.max(0.0f, r * r - dy * dy)));
            if (to <= 0) {
                continue;
            }
            int x0 = uHalf == 0 ? x : x + r - to;
            gfx.fill(x0, y + row, x0 + to, y + row + 1, color);
        }
    }
}
