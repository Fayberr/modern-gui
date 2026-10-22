package net.fayber.moderngui.anim;

import net.fayber.moderngui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Animates swapping one block of content for another inside a fixed viewport: the old content
 * slides out one way while the new content slides in from the other, clipped to the viewport, so
 * a page or category change moves only the content while the rest of the screen stands still.
 *
 * <p>The transition draws nothing itself. The caller supplies a {@link Painter} for each block;
 * a painter draws its block "at rest" into {@code (x, y, width, height)} and the transition
 * translates the matrix stack underneath it, so ordinary widget and text drawing code works
 * unchanged, including child widgets rendered through their normal path. The offset is snapped to
 * whole physical pixels so text stays crisp while it moves. While the transition is running,
 * pointer hit-testing still sees the blocks at rest: treat the content as inert until
 * {@link #isRunning} turns false (or ignore clicks during the ~quarter second it takes).
 *
 * <p>When idle {@link #draw} paints only the incoming block with no clipping, so a settled screen
 * is exactly what it was without the transition.
 *
 * <pre>{@code
 * SlideTransition slide = new SlideTransition().duration(260).easing(Easing.EASE_OUT_QUART);
 * // on category change:
 * slide.start(newIndex > oldIndex ? Direction.LEFT : Direction.RIGHT);
 * // every frame:
 * slide.draw(gfx, x, y, w, h, (g, px, py, pw, ph) -> drawPage(g, oldPage, px, py, pw, ph),
 *                             (g, px, py, pw, ph) -> drawPage(g, newPage, px, py, pw, ph));
 * }</pre>
 */
public final class SlideTransition {
    /** The way the content travels: {@link #LEFT} pushes the old block out left, new one in from the right. */
    public enum Direction {
        LEFT(-1, true),
        RIGHT(1, true),
        UP(-1, false),
        DOWN(1, false);

        private final int sign;
        private final boolean horizontal;

        Direction(int sign, boolean horizontal) {
            this.sign = sign;
            this.horizontal = horizontal;
        }

        public boolean horizontal() {
            return this.horizontal;
        }
    }

    /** Draws one block of content at rest into the given rectangle. */
    @FunctionalInterface
    public interface Painter {
        void paint(GuiGraphicsExtractor gfx, int x, int y, int width, int height);
    }

    private final EasedProgress progress = new EasedProgress().duration(240L).easing(Easing.EASE_OUT_CUBIC);
    private Direction direction = Direction.LEFT;

    /** Full length of the slide in milliseconds. */
    public SlideTransition duration(long millis) {
        this.progress.duration(millis);
        return this;
    }

    public SlideTransition easing(Easing easing) {
        this.progress.easing(easing);
        return this;
    }

    /** Direction used by the next {@link #start()}. */
    public SlideTransition direction(Direction direction) {
        this.direction = direction;
        return this;
    }

    public void start() {
        this.start(this.direction);
    }

    /** Begins a slide in {@code direction}, from the current state of the outgoing block. */
    public void start(Direction direction) {
        this.start(direction, EasedProgress.nowMs());
    }

    public void start(Direction direction, long nowMs) {
        this.direction = direction;
        this.progress.snap(0.0f);
        this.progress.animateTo(1.0f, nowMs);
    }

    /** Ends the slide now; the incoming block is at rest. */
    public void finish() {
        this.progress.snap(1.0f);
    }

    public boolean isRunning() {
        return this.progress.isAnimating();
    }

    /** Eased progress, 0 (old block at rest) to 1 (new block at rest). */
    public float progress() {
        return this.progress.value();
    }

    public float update(long nowMs) {
        return this.progress.update(nowMs);
    }

    /** Offset along the slide axis of the outgoing block, in pixels, for a viewport {@code distance} long. */
    public float outgoingOffset(float distance) {
        return this.direction.sign * this.progress.value() * distance;
    }

    /** Offset along the slide axis of the incoming block, in pixels. */
    public float incomingOffset(float distance) {
        return -this.direction.sign * (1.0f - this.progress.value()) * distance;
    }

    /** True when the slide runs along x. */
    public boolean isHorizontal() {
        return this.direction.horizontal;
    }

    /**
     * Draws the transition into the viewport: both blocks while running (clipped to the viewport),
     * otherwise just {@code incoming}. Advances the animation itself.
     */
    public void draw(GuiGraphicsExtractor gfx, int x, int y, int width, int height,
                     Painter outgoing, Painter incoming) {
        this.progress.update();
        if (!this.isRunning()) {
            incoming.paint(gfx, x, y, width, height);
            return;
        }
        float distance = this.isHorizontal() ? width : height;
        gfx.enableScissor(x, y, x + width, y + height);
        this.paintOffset(gfx, outgoing, this.outgoingOffset(distance), x, y, width, height);
        this.paintOffset(gfx, incoming, this.incomingOffset(distance), x, y, width, height);
        gfx.disableScissor();
    }

    private void paintOffset(GuiGraphicsExtractor gfx, Painter painter, float offset,
                             int x, int y, int width, int height) {
        float scale = Ui.scale();
        float snapped = Math.round(offset * scale) / scale;
        gfx.pose().pushMatrix();
        if (this.isHorizontal()) {
            gfx.pose().translate(snapped, 0.0f);
        } else {
            gfx.pose().translate(0.0f, snapped);
        }
        painter.paint(gfx, x, y, width, height);
        gfx.pose().popMatrix();
    }
}
