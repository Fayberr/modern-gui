package net.fayber.moderngui.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextSizingTest {
    @Test
    void snapsToTheNearestOfferedPercent() {
        int[] p = TextSizing.DEFAULT_PERCENTS;
        assertEquals(100, TextSizing.snapPercent(1.0f, p));
        assertEquals(75, TextSizing.snapPercent(0.5f, p));
        assertEquals(125, TextSizing.snapPercent(1.2f, p));
        assertEquals(150, TextSizing.snapPercent(1.6f, p));
        assertEquals(200, TextSizing.snapPercent(5.0f, p));
        // Nearer to 100 than to 125.
        assertEquals(100, TextSizing.snapPercent(1.12f, p));
        assertEquals(125, TextSizing.snapPercent(1.13f, p));
    }

    @Test
    void snapsWithinACustomList() {
        int[] p = {100, 300};
        assertEquals(300, TextSizing.snapPercent(2.4f, p));
        // Equidistant: the smaller size wins.
        assertEquals(100, TextSizing.snapPercent(2.0f, p));
        assertEquals(1, TextSizing.percentIndex(2.5f, p));
        assertEquals(0, TextSizing.percentIndex(1.4f, p));
    }

    @Test
    void guiScaleClampsToTheGeneratedRange() {
        assertEquals(1, TextSizing.scaleIndex(0.0f, 6));
        assertEquals(3, TextSizing.scaleIndex(3.0f, 6));
        assertEquals(6, TextSizing.scaleIndex(9.0f, 6));
        assertEquals(2, TextSizing.scaleIndex(9.0f, 2));
    }

    @Test
    void effectiveFactorIsTheRoundedPixelSize() {
        // 125% at GUI scale 1 is 11.25px, which FreeType rounds to 11.
        assertEquals(11.0f / 9.0f, TextSizing.effectiveFactor(125, 1), 1e-6f);
        // At scale 4 it is 45px exactly, so no rounding error.
        assertEquals(1.25f, TextSizing.effectiveFactor(125, 4), 1e-6f);
        assertEquals(1.0f, TextSizing.effectiveFactor(100, 3), 1e-6f);
        assertEquals(9.0f, TextSizing.lineHeight(100, 5), 1e-6f);
    }

    @Test
    void baselineShiftIsZeroForTheReferenceAtNormalSize() {
        for (int scale = 1; scale <= 6; scale++) {
            assertEquals(0.0f, TextSizing.baselineShift(100, scale, 7.0f), 1e-6f);
        }
    }

    @Test
    void baselineShiftTracksTheSizeAndSnapsToPhysicalPixels() {
        // 200%: the baseline is at 14, vanilla pins 7, so move down 7.
        assertEquals(7.0f, TextSizing.baselineShift(200, 1, 7.0f), 1e-6f);
        assertEquals(7.0f, TextSizing.baselineShift(200, 3, 7.0f), 1e-6f);
        // 75%: up by 1.75 GUI px, which at scale 3 is 5.25 physical px, snapped to 5 (1.6667).
        assertEquals(-5.0f / 3.0f, TextSizing.baselineShift(75, 3, 7.0f), 1e-5f);
        for (int scale = 1; scale <= 6; scale++) {
            for (int percent : TextSizing.DEFAULT_PERCENTS) {
                float device = TextSizing.baselineShift(percent, scale, 6.8f) * scale;
                assertEquals(Math.round(device), device, 1e-4f);
            }
        }
    }

    @Test
    void aFamilyBaselineMovesTheShift() {
        // A face whose baseline is half a pixel lower than Inter's shifts normal text by that, at
        // scale 2 (one whole physical pixel).
        assertEquals(0.5f, TextSizing.baselineShift(100, 2, 7.5f), 1e-6f);
    }
}
