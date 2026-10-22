package net.fayber.moderngui.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SlideTransitionTest {
    private static SlideTransition slide() {
        return new SlideTransition().duration(100).easing(Easing.LINEAR);
    }

    @Test
    void leftPushesTheOldBlockOutLeftAndBringsTheNewOneInFromTheRight() {
        SlideTransition t = slide();
        t.start(SlideTransition.Direction.LEFT, 0);
        t.update(0);
        assertEquals(0.0f, t.outgoingOffset(200), 1e-4f);
        assertEquals(200.0f, t.incomingOffset(200), 1e-4f);
        t.update(50);
        assertEquals(-100.0f, t.outgoingOffset(200), 1e-4f);
        assertEquals(100.0f, t.incomingOffset(200), 1e-4f);
        assertTrue(t.isHorizontal());
    }

    @Test
    void rightAndVerticalDirectionsMirror() {
        SlideTransition t = slide();
        t.start(SlideTransition.Direction.RIGHT, 0);
        t.update(50);
        assertEquals(100.0f, t.outgoingOffset(200), 1e-4f);
        assertEquals(-100.0f, t.incomingOffset(200), 1e-4f);
        t.start(SlideTransition.Direction.UP, 0);
        t.update(50);
        assertFalse(t.isHorizontal());
        assertEquals(-50.0f, t.outgoingOffset(100), 1e-4f);
        assertEquals(50.0f, t.incomingOffset(100), 1e-4f);
        t.start(SlideTransition.Direction.DOWN, 0);
        t.update(50);
        assertEquals(50.0f, t.outgoingOffset(100), 1e-4f);
    }

    @Test
    void endsAtRestAndReportsRunning() {
        SlideTransition t = slide();
        assertFalse(t.isRunning());
        t.start(SlideTransition.Direction.LEFT, 0);
        assertTrue(t.isRunning());
        t.update(100);
        assertFalse(t.isRunning());
        assertEquals(0.0f, t.incomingOffset(200), 1e-4f);
        assertEquals(1.0f, t.progress());
    }

    @Test
    void finishLandsTheIncomingBlockAtOnce() {
        SlideTransition t = slide();
        t.start(SlideTransition.Direction.LEFT, 0);
        t.finish();
        assertFalse(t.isRunning());
        assertEquals(0.0f, t.incomingOffset(200), 1e-4f);
    }
}
