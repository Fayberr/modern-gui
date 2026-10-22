package net.fayber.moderngui.font;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Reads the one number from a TrueType/OpenType file that vertical alignment needs: the cap
 * height. Minecraft pins every font's baseline 7 GUI pixels below the draw position, which is
 * right for the bundled Inter and wrong by a fraction of a pixel (or a whole one) for a face with
 * different proportions. {@link #baselineFor} turns the cap height into the baseline that centres
 * capitals in the line box exactly where Inter's are, so switching families does not make text
 * jump.
 *
 * <p>Pure byte parsing, no Minecraft types. Anything unreadable yields {@link Float#NaN} and the
 * caller falls back to Inter's baseline.
 */
public final class FontMetrics {
    /** Cap height of the bundled Inter, as a fraction of the em (1490 of 2048 units). */
    public static final float REFERENCE_CAP_HEIGHT = 1490.0f / 2048.0f;

    /** Baseline, in pixels below the top of the 9px line box, that the reference font (Inter) uses. */
    public static final float REFERENCE_BASELINE = 7.0f;

    private static final float BASE_SIZE = 9.0f;

    private FontMetrics() {
    }

    /**
     * Cap height as a fraction of the em, from the OS/2 table (version 2 or later), or NaN when the
     * file is not a readable sfnt or lacks the field.
     */
    public static float capHeightEm(byte[] ttf) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(ttf).order(ByteOrder.BIG_ENDIAN);
            int tables = buf.getShort(4) & 0xFFFF;
            int unitsPerEm = 0;
            int os2Offset = -1;
            int os2Length = 0;
            for (int i = 0; i < tables; i++) {
                int rec = 12 + i * 16;
                String tag = new String(ttf, rec, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
                int offset = buf.getInt(rec + 8);
                int length = buf.getInt(rec + 12);
                if (tag.equals("head")) {
                    unitsPerEm = buf.getShort(offset + 18) & 0xFFFF;
                } else if (tag.equals("OS/2")) {
                    os2Offset = offset;
                    os2Length = length;
                }
            }
            if (unitsPerEm <= 0 || os2Offset < 0 || os2Length < 90) {
                return Float.NaN;
            }
            int version = buf.getShort(os2Offset) & 0xFFFF;
            if (version < 2) {
                return Float.NaN;
            }
            int cap = buf.getShort(os2Offset + 88);
            return cap > 0 ? cap / (float) unitsPerEm : Float.NaN;
        } catch (RuntimeException e) {
            // Truncated or corrupt file: indexes and buffer reads fail with runtime exceptions.
            return Float.NaN;
        }
    }

    /**
     * The baseline, in pixels below the top of a 9px line box, that centres this cap height where
     * the reference font's capitals sit. The capital's centre is {@code baseline - cap * 9 / 2}
     * above the baseline, so holding it equal to Inter's gives
     * {@code 7 + 4.5 * (cap - REFERENCE_CAP_HEIGHT)}. NaN input yields the reference baseline.
     */
    public static float baselineFor(float capHeightEm) {
        if (Float.isNaN(capHeightEm)) {
            return REFERENCE_BASELINE;
        }
        return REFERENCE_BASELINE + (BASE_SIZE / 2.0f) * (capHeightEm - REFERENCE_CAP_HEIGHT);
    }
}
