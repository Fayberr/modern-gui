package net.fayber.moderngui.anim;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Remembers which page is shown and which one it replaced, and runs a {@link SlideTransition}
 * between them: the bookkeeping every "click a category, slide the content" screen needs.
 *
 * <pre>{@code
 * SlideSwitcher<Page> pages = new SlideSwitcher<>(Page.GENERAL, new SlideTransition());
 * pages.show(Page.VISUALS, Direction.LEFT);                       // on tab click
 * pages.draw(gfx, x, y, w, h, (g, page, px, py, pw, ph) -> page.draw(g, px, py, pw, ph));
 * }</pre>
 *
 * @param <T> the page type; only compared with {@link Object#equals}
 */
public final class SlideSwitcher<T> {
    /** Draws one page at rest into a rectangle. */
    @FunctionalInterface
    public interface PagePainter<T> {
        void paint(GuiGraphicsExtractor gfx, T page, int x, int y, int width, int height);
    }

    private final SlideTransition slide;
    private T current;
    private T previous;

    public SlideSwitcher(T initial, SlideTransition slide) {
        this.current = initial;
        this.previous = initial;
        this.slide = slide;
    }

    public T current() {
        return this.current;
    }

    public SlideTransition transition() {
        return this.slide;
    }

    /** Switches to {@code next}, sliding in {@code direction}; a no-op when it is already shown. */
    public void show(T next, SlideTransition.Direction direction) {
        if (next == null || next.equals(this.current)) {
            return;
        }
        // A switch in mid-slide lands the pending one first instead of stacking three blocks.
        this.slide.finish();
        this.previous = this.current;
        this.current = next;
        this.slide.start(direction);
    }

    /** Switches without animating. */
    public void jump(T next) {
        this.slide.finish();
        this.previous = next;
        this.current = next;
    }

    /** True while the old page is still sliding out; content is best treated as inert. */
    public boolean isTransitioning() {
        return this.slide.isRunning();
    }

    public void draw(GuiGraphicsExtractor gfx, int x, int y, int width, int height,
                     PagePainter<T> painter) {
        T outgoing = this.previous;
        T incoming = this.current;
        this.slide.draw(gfx, x, y, width, height,
                (g, px, py, pw, ph) -> painter.paint(g, outgoing, px, py, pw, ph),
                (g, px, py, pw, ph) -> painter.paint(g, incoming, px, py, pw, ph));
    }
}
