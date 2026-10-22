package net.fayber.moderngui.render;

/**
 * The pure arithmetic behind sized text, kept free of Minecraft classes so it can be unit tested.
 *
 * <p>Minecraft rasterises a TTF glyph at {@code round(size * oversample)} pixels per em and then
 * draws it {@code that / oversample} GUI pixels tall. A font can only be declared in a resource
 * json, so a text size is a generated font: one per (GUI scale, size factor, weight) of a
 * {@link net.fayber.moderngui.font.FontFamily}, each with {@code size = 9 * factor} and
 * {@code oversample = guiScale}. That makes the glyph bitmap exactly as many texels as it covers
 * physical pixels, at every size, instead of magnifying a bitmap that was baked for a different
 * size (which is what {@code pose().scale(1.5f)} around ordinary text does, and why such text
 * looks soft and pixelated).
 *
 * <p>The size factors a family offers are a list of percents ({@link #DEFAULT_PERCENTS} unless the
 * family says otherwise); a requested size snaps to the nearest one.
 */
public final class TextSizing {
    /** Highest GUI scale a family generates fonts for by default; larger scales reuse it (and sample slightly soft). */
    public static final int DEFAULT_MAX_SCALE = 6;

    /** Size factors in percent of the 9 GUI-pixel base size that a family offers by default. Ascending. */
    public static final int[] DEFAULT_PERCENTS = {75, 100, 125, 150, 200};

    /** Base em size in GUI pixels (the {@code size} of every 100% font). */
    public static final float BASE_SIZE = 9.0f;

    /** Baseline offset below the top of a 9px line box, as vanilla's glyph layout fixes it. */
    public static final float BASELINE = 7.0f;

    private TextSizing() {
    }

    /** The percent in {@code percents} nearest to {@code factor} (1.0 = 100); ties go to the smaller. */
    public static int snapPercent(float factor, int[] percents) {
        int target = Math.round(factor * 100.0f);
        int best = percents[0];
        for (int p : percents) {
            if (Math.abs(p - target) < Math.abs(best - target)) {
                best = p;
            }
        }
        return best;
    }

    /** Index into {@code percents} of the size nearest to {@code factor}. */
    public static int percentIndex(float factor, int[] percents) {
        int percent = snapPercent(factor, percents);
        for (int i = 0; i < percents.length; i++) {
            if (percents[i] == percent) {
                return i;
            }
        }
        return 0;
    }

    /** The generated GUI scale variant used for a window GUI scale: 1 to {@code maxScale}. */
    public static int scaleIndex(float guiScale, int maxScale) {
        return Math.clamp(Math.round(guiScale), 1, maxScale);
    }

    /**
     * The em size, in multiples of 9 GUI pixels, that FreeType really rasterises: the pixel size is
     * rounded to a whole number of physical pixels, so at GUI scale 1 a 125% font is 11px, not
     * 11.25px. Layout and the baseline shift use this, not the nominal factor.
     */
    public static float effectiveFactor(int percent, int guiScale) {
        float size = BASE_SIZE * percent / 100.0f;
        return Math.round(size * guiScale) / (BASE_SIZE * guiScale);
    }

    /** Height in GUI pixels of the line box of a sized font (9 for the 100% font). */
    public static float lineHeight(int percent, int guiScale) {
        return BASE_SIZE * effectiveFactor(percent, guiScale);
    }

    /**
     * How far, in GUI pixels, glyphs must be moved down so that a sized font's baseline lands at
     * {@code baseline * effectiveFactor} below the top of its line box, given the family's baseline
     * for a 9px box ({@link net.fayber.moderngui.font.FontMetrics#baselineFor}). Vanilla pins the
     * baseline 7 GUI pixels below the draw position whatever the font, so without this a 200% line
     * would poke 7px above its box, and a face with different proportions would sit high or low.
     * Snapped to a whole physical pixel so the glyphs stay on the device grid; the generated font
     * applies it as the TTF provider's {@code shift}, so every way of drawing the text gets it.
     */
    public static float baselineShift(int percent, int guiScale, float baseline) {
        float shift = baseline * effectiveFactor(percent, guiScale) - BASELINE;
        return Math.round(shift * guiScale) / (float) guiScale;
    }
}
