package net.fayber.moderngui.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScrollStateTest {
    private static ScrollState state(ScrollState.Mode mode) {
        return new ScrollState().mode(mode).wheelStep(40.0f).setSizes(1000.0f, 200.0f);
    }

    /** Runs the state at 60 fps for the given seconds. */
    private static void run(ScrollState s, double seconds) {
        long t = 1000;
        s.update(t);
        for (int i = 0; i < seconds * 60; i++) {
            t += 16;
            s.update(t);
        }
    }

    @Test
    void rangeAndScrollability() {
        ScrollState s = state(ScrollState.Mode.EASED);
        assertEquals(800.0f, s.maxScroll());
        assertTrue(s.isScrollable());
        s.setSizes(100.0f, 200.0f);
        assertEquals(0.0f, s.maxScroll());
        assertFalse(s.isScrollable());
    }

    @Test
    void momentumNotchTravelsRoughlyOneStepInTotal() {
        ScrollState s = state(ScrollState.Mode.MOMENTUM);
        s.update(1000);
        s.wheel(-1.0);
        run(s, 3.0);
        // v0 = step * friction decays exponentially to rest; the distance is ~step (a little less
        // because the glide stops below the stop speed).
        assertEquals(40.0f, s.offset(), 4.0f);
        assertFalse(s.isAnimating());
    }

    @Test
    void wheelUpScrollsBackTowardsTheStart() {
        ScrollState s = state(ScrollState.Mode.MOMENTUM);
        s.jumpTo(300);
        s.wheel(1.0);
        run(s, 3.0);
        assertTrue(s.offset() < 300.0f);
    }

    @Test
    void momentumStopsDeadAtTheEnds() {
        ScrollState s = state(ScrollState.Mode.MOMENTUM);
        s.jumpTo(790);
        for (int i = 0; i < 20; i++) {
            s.wheel(-1.0);
        }
        run(s, 2.0);
        assertEquals(800.0f, s.offset());
        assertFalse(s.isAnimating());
    }

    @Test
    void easedWheelMovesTheTargetAndTheOffsetFollows() {
        ScrollState s = state(ScrollState.Mode.EASED);
        s.update(1000);
        s.wheel(-2.0);
        assertEquals(80.0f, s.target());
        assertEquals(0.0f, s.offset());
        run(s, 3.0);
        assertEquals(80.0f, s.offset(), 0.01f);
    }

    @Test
    void jumpToIsInstantAndClamped() {
        ScrollState s = state(ScrollState.Mode.MOMENTUM);
        s.jumpTo(5000);
        assertEquals(800.0f, s.offset());
        assertEquals(800.0f, s.target());
        s.jumpTo(-50);
        assertEquals(0.0f, s.offset());
        assertFalse(s.isAnimating());
    }

    @Test
    void scrollToGlidesWithTheEase() {
        ScrollState s = state(ScrollState.Mode.MOMENTUM);
        s.update(1000);
        s.scrollTo(400);
        assertTrue(s.isAnimating());
        run(s, 3.0);
        assertEquals(400.0f, s.offset(), 0.01f);
    }

    @Test
    void resizingReclamps() {
        ScrollState s = state(ScrollState.Mode.EASED);
        s.jumpTo(800);
        s.setSizes(500.0f, 200.0f);
        assertEquals(300.0f, s.offset());
    }

    @Test
    void longStallDoesNotTeleportTheGlide() {
        ScrollState s = state(ScrollState.Mode.MOMENTUM);
        s.update(1000);
        s.wheel(-1.0);
        s.update(1000 + 60_000);
        // A 60 s gap counts as at most 0.1 s of motion.
        assertTrue(s.offset() > 0.0f && s.offset() <= 40.5f);
    }

    @Test
    void snappedOffsetLandsOnWholePhysicalPixels() {
        ScrollState s = state(ScrollState.Mode.EASED);
        s.jumpTo(10.4);
        assertEquals(10.333333f, s.snappedOffset(3.0f), 1e-4f);
        assertEquals(10.0f, s.snappedOffset(1.0f), 1e-6f);
    }

    @Test
    void scrollbarGeometryMapsOffsetAndPointerBothWays() {
        ScrollState s = state(ScrollState.Mode.EASED);
        float track = 196.0f;
        float thumb = s.thumbLength(track, 18.0f);
        // Viewport is a fifth of the content.
        assertEquals(track / 5.0f, thumb, 1e-3f);
        assertEquals(0.0f, s.thumbOffset(track, thumb));
        s.jumpTo(800);
        assertEquals(track - thumb, s.thumbOffset(track, thumb), 1e-3f);
        // Centring the thumb under a pointer at the very bottom gives the end of the content.
        assertEquals(800.0f, s.offsetForPointer(track, track, thumb), 1e-3f);
        assertEquals(0.0f, s.offsetForPointer(0.0f, track, thumb), 1e-3f);
        assertEquals(400.0f, s.offsetForPointer(track / 2.0f, track, thumb), 1e-3f);
    }

    @Test
    void thumbNeverShrinksBelowTheMinimum() {
        ScrollState s = new ScrollState().setSizes(100000.0f, 200.0f);
        assertEquals(18.0f, s.thumbLength(196.0f, 18.0f));
    }
}
