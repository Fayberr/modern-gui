package net.fayber.moderngui.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EasedProgressTest {
    private static EasedProgress linear(long duration) {
        return new EasedProgress().duration(duration).easing(Easing.LINEAR);
    }

    @Test
    void glidesLinearlyOverTheDuration() {
        EasedProgress p = linear(100);
        p.animateTo(1.0f, 0);
        assertEquals(0.0f, p.update(0), 1e-6f);
        assertEquals(0.5f, p.update(50), 1e-6f);
        assertTrue(p.isAnimating());
        assertEquals(1.0f, p.update(100), 1e-6f);
        assertFalse(p.isAnimating());
    }

    @Test
    void snapNeverAnimates() {
        EasedProgress p = linear(100);
        p.snap(1.0f);
        assertEquals(1.0f, p.value());
        assertFalse(p.isAnimating());
        p.animateTo(1.0f, 0);
        assertFalse(p.isAnimating());
    }

    @Test
    void zeroDurationJumps() {
        EasedProgress p = linear(0);
        p.animateTo(1.0f, 0);
        assertEquals(1.0f, p.value());
        assertFalse(p.isAnimating());
    }

    @Test
    void retargetContinuesFromTheShownValue() {
        EasedProgress p = linear(100);
        p.animateTo(1.0f, 0);
        p.update(40);
        p.animateTo(0.0f, 40);
        // No jump: still 0.4 at the instant of the reversal.
        assertEquals(0.4f, p.update(40), 1e-6f);
        // And the way back takes time in proportion to the 0.4 distance, not the full duration.
        assertEquals(0.0f, p.update(80), 1e-6f);
        assertFalse(p.isAnimating());
    }

    @Test
    void timeBeforeStartAndFarAfterAreClamped() {
        EasedProgress p = linear(100);
        p.animateTo(1.0f, 1000);
        assertEquals(0.0f, p.update(900), 1e-6f);
        assertEquals(1.0f, p.update(99999), 1e-6f);
    }
}
