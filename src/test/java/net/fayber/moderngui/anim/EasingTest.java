package net.fayber.moderngui.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EasingTest {
    private static final Easing[] ALL = {Easing.LINEAR, Easing.EASE_IN_CUBIC, Easing.EASE_OUT_CUBIC,
            Easing.EASE_IN_OUT_CUBIC, Easing.EASE_OUT_QUART, Easing.EASE_OUT_BACK};

    @Test
    void everyCurveStartsAtZeroAndEndsAtOne() {
        for (Easing easing : ALL) {
            assertEquals(0.0f, easing.apply(0.0f), 1e-6f);
            assertEquals(1.0f, easing.apply(1.0f), 1e-6f);
        }
    }

    @Test
    void outCurvesLeadAndInCurvesLag() {
        assertTrue(Easing.EASE_OUT_CUBIC.apply(0.5f) > 0.5f);
        assertTrue(Easing.EASE_OUT_QUART.apply(0.5f) > Easing.EASE_OUT_CUBIC.apply(0.5f));
        assertTrue(Easing.EASE_IN_CUBIC.apply(0.5f) < 0.5f);
        assertEquals(0.5f, Easing.EASE_IN_OUT_CUBIC.apply(0.5f), 1e-6f);
    }

    @Test
    void backOvershootsBeforeSettling() {
        assertTrue(Easing.EASE_OUT_BACK.apply(0.7f) > 1.0f);
    }

    @Test
    void curvesAreMonotonicExceptBack() {
        for (Easing easing : new Easing[] {Easing.LINEAR, Easing.EASE_IN_CUBIC, Easing.EASE_OUT_CUBIC,
                Easing.EASE_IN_OUT_CUBIC, Easing.EASE_OUT_QUART}) {
            float last = -1.0f;
            for (int i = 0; i <= 100; i++) {
                float v = easing.apply(i / 100.0f);
                assertTrue(v >= last);
                last = v;
            }
        }
    }
}
