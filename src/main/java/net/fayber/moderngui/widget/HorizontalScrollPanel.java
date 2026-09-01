package net.fayber.moderngui.widget;

import net.fayber.moderngui.render.Theme;
import net.fayber.moderngui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A panel whose children scroll horizontally, for timelines, galleries and toolbars that are
 * wider than the screen. Children live in content coordinates and are repositioned every frame
 * from the current scroll offset, clipped to the panel with a scissor.
 *
 * <p>Scrolling has the same wheel-driven momentum as {@link net.fayber.moderngui.list.CardList}:
 * the wheel adds velocity and the offset coasts to rest with a time-normalised decay.
 * Authoritative changes ({@link #setScrollAmount}) cancel the glide and apply instantly.
 *
 * <p>Keyboard input is deliberately not forwarded; the panel is a pointer-first container, and
 * children that need keys should be focussed by the screen through {@link #children()}.
 */
public class HorizontalScrollPanel extends AbstractWidget {
    private static final double SCROLL_FRICTION = 10.0;
    private static final double SCROLL_STOP = 6.0;
    // Velocity cap so a fast spin doesn't launch the content off-screen.
    private static final double SCROLL_MAX_SPEED = 4000.0;
    // Frame gap cap so a stall never teleports the glide.
    private static final double MAX_FRAME_SECONDS = 0.1;
    private static final double SCROLL_RATE = 60.0;
    private static final float BAR_HEIGHT = 4.0f;
    private static final float BAR_INSET = 2.0f;
    private static final float MIN_THUMB_WIDTH = 12.0f;

    protected Theme theme = Theme.dark();

    private int contentWidth;
    private double scroll;
    private double glideVelocity;
    private long lastFrameMs = -1L;

    private final List<AbstractWidget> children = new ArrayList<>();
    // Content-space x/y per child, parallel to children; re-captured by syncContentCoords().
    private final List<int[]> childPos = new ArrayList<>();
    private AbstractWidget pressedChild;
    private boolean draggingScrollbar;

    public HorizontalScrollPanel(int x, int y, int w, int h) {
        super(x, y, w, h, net.minecraft.network.chat.Component.empty());
    }

    /**
     * Appends a child. Its current position is captured as relative to the content: {@code (0, 0)}
     * is the content's top-left. Callers position the child before adding, or call
     * {@link #pack(int)} afterward.
     */
    public HorizontalScrollPanel add(AbstractWidget child) {
        this.children.add(child);
        this.childPos.add(new int[]{child.getX(), child.getY()});
        this.contentWidth = Math.max(this.contentWidth, child.getX() + child.getWidth());
        return this;
    }

    /** Re-captures the content coordinates of every child; call after moving or resizing them
     * through means other than {@link #pack(int)}. */
    public void syncContentCoords() {
        this.childPos.clear();
        this.contentWidth = 0;
        for (AbstractWidget child : this.children) {
            this.childPos.add(new int[]{child.getX(), child.getY()});
            this.contentWidth = Math.max(this.contentWidth, child.getX() + child.getWidth());
        }
    }

    /**
     * Lays the children in a single row with the given gap (preserving each child's height, all
     * aligned to y = 0 in content space) and sets {@link #contentWidth} to the occupied width.
     */
    public void pack(int gap) {
        Layouts.row(this.children, 0, 0, gap);
        this.syncContentCoords();
        this.contentWidth = Layouts.totalWidth(this.children, gap);
    }

    /** Sets the content width in px; the scroll range is {@code max(0, contentWidth - width)}. */
    public void setContentWidth(int contentWidth) {
        this.contentWidth = Math.max(0, contentWidth);
        this.scroll = Math.clamp(this.scroll, 0.0, this.maxScroll());
    }

    /**
     * Scrolls minimally so the child is fully inside the viewport; a no-op when it already is or
     * when there is nothing to scroll. Meant for selection-follows-content, e.g. a tab strip
     * calling this after the user picks a child that may sit past the clipped edge.
     */
    public boolean ensureVisible(AbstractWidget child) {
        int index = this.children.indexOf(child);
        if (index < 0 || this.maxScroll() <= 0.0) {
            return false;
        }
        int x = this.childPos.get(index)[0];
        if (x >= this.scroll && x + child.getWidth() <= this.scroll + this.getWidth()) {
            return false;
        }
        double target = x < this.scroll
                ? x
                : x + child.getWidth() - this.getWidth();
        this.setScrollAmount(target);
        return true;
    }

    public int getContentWidth() {
        return this.contentWidth;
    }

    /** Fractional while gliding. */
    public double getScrollAmount() {
        return this.scroll;
    }

    public double maxScroll() {
        return Math.max(0, this.contentWidth - this.getWidth());
    }

    /** Jumps to a scroll offset, cancelling any glide. */
    public void setScrollAmount(double amount) {
        this.glideVelocity = 0.0;
        this.scroll = Math.clamp(amount, 0.0, this.maxScroll());
    }

    // Both axes drive the horizontal offset, so a plain wheel and a trackpad sideways swipe
    // both work; the offset coasts in advanceGlide(). Outside the panel it falls through to the screen.
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double xDelta, double yDelta) {
        if (!this.isMouseOver(mouseX, mouseY) || this.maxScroll() <= 0.0) {
            return super.mouseScrolled(mouseX, mouseY, xDelta, yDelta);
        }
        double delta = xDelta != 0.0 ? xDelta : yDelta;
        this.glideVelocity = Math.clamp(
                this.glideVelocity - delta * SCROLL_RATE * SCROLL_FRICTION,
                -SCROLL_MAX_SPEED, SCROLL_MAX_SPEED);
        return true;
    }

    private void advanceGlide() {
        // Track the frame gap every frame so it's fresh when a wheel event arrives after a long
        // idle period; a stale gap would teleport the glide on the first frame.
        long now = Util.getMillis();
        double dt = this.lastFrameMs < 0
                ? 0.0
                : Math.min((now - this.lastFrameMs) / 1000.0, MAX_FRAME_SECONDS);
        this.lastFrameMs = now;
        if (this.glideVelocity == 0.0 || this.maxScroll() <= 0.0) {
            return;
        }
        double scrolled = this.scroll + this.glideVelocity * dt;
        this.glideVelocity *= Math.exp(-dt * SCROLL_FRICTION);
        if (Math.abs(this.glideVelocity) < SCROLL_STOP) {
            this.glideVelocity = 0.0;
        }
        double clamped = Math.clamp(scrolled, 0.0, this.maxScroll());
        if (clamped != scrolled) {
            // Reached an end of the content: stop dead instead of pressing against the edge.
            this.glideVelocity = 0.0;
        }
        this.scroll = clamped;
    }

    // The panel swallows clicks either way so clicks in the padding don't fall through to
    // widgets behind it. Grabbing the scrollbar strip starts a thumb drag instead of reaching a child.
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.isMouseOver(event.x(), event.y())) {
            return false;
        }
        if (this.maxScroll() > 0.0 && this.overScrollbar(event.x(), event.y())) {
            this.draggingScrollbar = true;
            this.setScrollAmount(this.scrollForPointerX(event.x()));
            return true;
        }
        this.pressedChild = this.childAt(event.x(), event.y());
        if (this.pressedChild != null) {
            return this.pressedChild.mouseClicked(event, doubleClick);
        }
        return true;
    }

    private boolean overScrollbar(double x, double y) {
        float trackY = this.getY() + this.getHeight() - BAR_HEIGHT - BAR_INSET;
        return y >= trackY - 3.0 && y < this.getY() + this.getHeight()
                && x >= this.getX() && x < this.getX() + this.getWidth();
    }

    // Maps a pointer x to the scroll amount that centres the thumb under it.
    private double scrollForPointerX(double pointerX) {
        float trackX = this.getX() + BAR_INSET;
        float trackW = this.getWidth() - BAR_INSET * 2.0f;
        float thumbW = Math.max(MIN_THUMB_WIDTH,
                trackW * (float) (this.getWidth() / (double) Math.max(1, this.contentWidth)));
        float span = trackW - thumbW;
        if (span <= 0.0f) {
            return 0.0;
        }
        double target = (pointerX - trackX - thumbW / 2.0) / span;
        return Math.clamp(target, 0.0, 1.0) * this.maxScroll();
    }

    /** Releases end a thumb drag first; otherwise they go to the captured child or any under the pointer. */
    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingScrollbar = false;
        if (this.pressedChild != null) {
            AbstractWidget child = this.pressedChild;
            this.pressedChild = null;
            return child.mouseReleased(event);
        }
        AbstractWidget child = this.childAt(event.x(), event.y());
        return child != null && child.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.draggingScrollbar) {
            this.setScrollAmount(this.scrollForPointerX(event.x()));
            return true;
        }
        if (this.pressedChild != null) {
            return this.pressedChild.mouseDragged(event, deltaX, deltaY);
        }
        AbstractWidget child = this.childAt(event.x(), event.y());
        return child != null && child.mouseDragged(event, deltaX, deltaY);
    }

    private AbstractWidget childAt(double mx, double my) {
        int offset = this.getX() - (int) this.scroll;
        for (int i = 0; i < this.children.size(); i++) {
            AbstractWidget child = this.children.get(i);
            int[] pos = this.childPos.get(i);
            int cx = pos[0] + offset;
            int cy = this.getY() + pos[1];
            if (mx >= cx && mx < cx + child.getWidth()
                    && my >= cy && my < cy + child.getHeight()) {
                return child;
            }
        }
        return null;
    }

    public List<? extends GuiEventListener> children() {
        return Collections.unmodifiableList(this.children);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        this.advanceGlide();

        double frac = this.scroll - Math.floor(this.scroll);
        int scrollPx = (int) this.scroll;

        // Scissor is enabled before the pose shift so the clip rectangle stays on the panel;
        // the sub-pixel translate moves only the children inside it.
        gfx.enableScissor(this.getX(), this.getY(),
                this.getX() + this.getWidth(), this.getY() + this.getHeight());
        gfx.pose().pushMatrix();
        gfx.pose().translate((float) -frac, 0.0f);
        for (int i = 0; i < this.children.size(); i++) {
            AbstractWidget child = this.children.get(i);
            int[] pos = this.childPos.get(i);
            child.setPosition(this.getX() + pos[0] - scrollPx, this.getY() + pos[1]);
            child.extractRenderState(gfx, mouseX, mouseY, partialTick);
        }
        gfx.pose().popMatrix();
        gfx.disableScissor();

        this.extractScrollbar(gfx, mouseX, mouseY);
    }

    // The thumb is drawn from the continuous scroll offset, so it glides with the content
    // instead of stair-stepping.
    private void extractScrollbar(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        if (this.maxScroll() <= 0.0) {
            return;
        }
        float trackX = this.getX() + BAR_INSET;
        float trackY = this.getY() + this.getHeight() - BAR_HEIGHT - BAR_INSET;
        float trackW = this.getWidth() - BAR_INSET * 2.0f;
        boolean hovered = mouseX >= trackX && mouseX <= trackX + trackW
                && mouseY >= trackY - 3.0f && mouseY <= trackY + BAR_HEIGHT + 3.0f;

        float fraction = (float) (this.getWidth() / (double) this.contentWidth);
        float thumbW = Math.max(MIN_THUMB_WIDTH, trackW * fraction);
        float span = trackW - thumbW;
        float thumbX = trackX + (float) (this.scroll * span / this.maxScroll());
        Ui.pill(gfx, trackX, trackY, trackW, BAR_HEIGHT, this.theme.card);
        Ui.pill(gfx, thumbX, trackY, thumbW, BAR_HEIGHT,
                hovered ? this.theme.scrollbarHover : this.theme.scrollbar);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // The panel itself has nothing to say; children are narrated through children().
    }
}
