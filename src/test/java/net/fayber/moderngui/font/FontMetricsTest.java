package net.fayber.moderngui.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class FontMetricsTest {
    private static byte[] bundled(String name) throws IOException {
        try (InputStream in = FontMetricsTest.class.getResourceAsStream("/assets/moderngui/font/" + name)) {
            return in.readAllBytes();
        }
    }

    @Test
    void readsInterCapHeightAndItIsTheReference() throws IOException {
        assertEquals(FontMetrics.REFERENCE_CAP_HEIGHT, FontMetrics.capHeightEm(bundled("inter_regular.ttf")), 1e-6f);
        assertEquals(FontMetrics.REFERENCE_CAP_HEIGHT, FontMetrics.capHeightEm(bundled("inter_semibold.ttf")), 1e-6f);
    }

    @Test
    void interDisplayMeasuresToARealBaselineNearInters() throws IOException {
        float cap = FontMetrics.capHeightEm(bundled("inter_display_regular.ttf"));
        assertTrue(cap > 0.6f && cap < 0.8f, "cap height " + cap);
        float baseline = FontMetrics.baselineFor(cap);
        assertEquals(7.0f, baseline, 0.3f);
    }

    @Test
    void baselineKeepsCapitalsCentredWhereInterPutsThem() {
        assertEquals(7.0f, FontMetrics.baselineFor(FontMetrics.REFERENCE_CAP_HEIGHT), 1e-6f);
        // Taller capitals need a lower baseline to keep the same centre, shorter ones a higher.
        float taller = FontMetrics.baselineFor(FontMetrics.REFERENCE_CAP_HEIGHT + 0.1f);
        float shorter = FontMetrics.baselineFor(FontMetrics.REFERENCE_CAP_HEIGHT - 0.1f);
        assertEquals(7.45f, taller, 1e-5f);
        assertEquals(6.55f, shorter, 1e-5f);
        // The capital's centre above the top of the box is the same for all three.
        float c0 = 7.0f - FontMetrics.REFERENCE_CAP_HEIGHT * 4.5f;
        assertEquals(c0, taller - (FontMetrics.REFERENCE_CAP_HEIGHT + 0.1f) * 4.5f, 1e-5f);
    }

    @Test
    void unreadableInputFallsBackToTheReferenceBaseline() {
        assertTrue(Float.isNaN(FontMetrics.capHeightEm(new byte[0])));
        assertTrue(Float.isNaN(FontMetrics.capHeightEm(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13})));
        assertEquals(7.0f, FontMetrics.baselineFor(Float.NaN));
    }
}
