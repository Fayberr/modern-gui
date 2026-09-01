package net.fayber.moderngui.list;

import net.fayber.moderngui.render.Theme;
import net.fayber.moderngui.render.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;

/**
 * The reusable scroll-list behind Modern GUI screens: one rounded card per row, a slim rounded
 * scrollbar, and no vanilla list chrome (background, separators and the sprite scrollbar are all
 * replaced).
 *
 * <p>Rows are {@link ContainerObjectSelectionList.Entry}s whose real child widgets are
 * hit-tested/focused through the entry's {@code children()} dispatch, exactly like the vanilla
 * KeyBindsList pattern: {@code extractContent} repositions children from the row's content coords
 * (scroll/resize-safe) and then calls their final {@code extractRenderState}.
 *
 * <p>Mouse-wheel scrolling has momentum (vanilla's is instant): the wheel adds velocity, and the
 * scroll position coasts with an exponential decay, the way a website eases a wheel step to rest.
 * One notch travels {@code scrollRate()} pixels in total, but spread over a fraction of a second
 * of deceleration instead of snapping there; fast spins accumulate velocity (capped). The decay
 * is time-normalised, so the feel is identical at any frame rate. Authoritative scroll changes
 * that are not wheel-driven (scrollbar drag, keyboard, code) cancel the glide and stay instant.
 *
 * <p>Vanilla positions rows at {@code firstEntryY - (int) scrollAmount} and the scrollbar thumb
 * from an integer-division formula: both drop the fractional part of the scroll amount, which
 * makes any fractional scroll (this glide, or a trackpad) move content in whole-GUI-pixel
 * stair-steps. Rows are therefore drawn through {@link #extractItem} with the pose shifted back
 * by the dropped fraction (true sub-pixel motion; hit-testing keeps the int positions, the
 * difference is under one GUI pixel), and the thumb is drawn from the continuous formula.
 *
 * <p>Subclasses provide the concrete row types; {@link Row} offers the shared card drawing.
 */
public abstract class CardList extends ContainerObjectSelectionList<CardList.Row> {
    /** Card height; the row pitch adds the gap on top of this. */
    public static final int CARD_HEIGHT = 34;
    /** Vertical gap between cards. */
    public static final int ROW_GAP = 4;
    public static final int ROW_HEIGHT = CARD_HEIGHT + ROW_GAP;
    /** Horizontal padding inside a card. */
    public static final int CARD_PADDING = 12;
    private static final float CARD_RADIUS = 6.0f;
    /** Exponential velocity decay of the wheel glide (per second); a notch coasts ~0.4s. */
    private static final double SCROLL_FRICTION = 10.0;
    /** Glide speed below which the coast has visibly ended and stops. */
    private static final double SCROLL_STOP = 6.0;
    /** Velocity cap so a fast spin does not launch the list off-screen. */
    private static final double SCROLL_MAX_SPEED = 4000.0;
    /** Frame gap cap so a stall never teleports the glide. */
    private static final double MAX_FRAME_SECONDS = 0.1;

    protected final int rowWidth;

    protected Theme theme = Theme.dark();

    private double glideVelocity;
    private long lastFrameMs = -1L;
    private boolean draggingScrollbar;

    protected CardList(Minecraft mc, int width, int height, int y0, int rowWidth) {
        this(mc, width, height, y0, rowWidth, ROW_HEIGHT);
    }

    /** @param itemHeight the row pitch; rows draw their own cards inside it (default {@link #ROW_HEIGHT}). */
    protected CardList(Minecraft mc, int width, int height, int y0, int rowWidth, int itemHeight) {
        super(mc, width, height, y0, itemHeight);
        this.rowWidth = rowWidth;
    }

    @Override
    public int getRowWidth() {
        return Math.min(this.rowWidth, this.getWidth() - 24);
    }

    public CardList theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    @Override
    public int addEntry(Row entry) {
        // 26.1 entries hold no back pointer to their list; set one so rows can read the theme.
        entry.owningList = this;
        return super.addEntry(entry);
    }

    /**
     * Wheel input adds glide velocity; the position coasts in {@link #advanceGlide}. Sign matches
     * vanilla's own wheel handling ({@code scrollAmount - yDelta * scrollRate()}), and one notch
     * travels {@code scrollRate()} pixels in total because v0 = distance * friction for an
     * exponential decay.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double xDelta, double yDelta) {
        if (!this.scrollable()) {
            return super.mouseScrolled(mouseX, mouseY, xDelta, yDelta);
        }
        this.glideVelocity = Math.clamp(
                this.glideVelocity - yDelta * this.scrollRate() * SCROLL_FRICTION,
                -SCROLL_MAX_SPEED, SCROLL_MAX_SPEED);
        return true;
    }

    /**
     * GUI pixels per wheel notch. Vanilla's own rate is {@code entryHeight / 2} (bytecode-verified:
     * the list constructor passes that to the scrollbar settings), half a row, which reads as
     * sluggish on a sparse screen. Two rows per notch feels like a modern app.
     */
    @Override
    protected double scrollRate() {
        return 2.0 * ROW_HEIGHT;
    }

    /**
     * Owns the scrollbar strip: the custom slim thumb is drawn there, and the strip has no row in
     * it, so 26.1's screen dispatch ({@code getChildAt} returns only the row entry under the
     * pointer) would otherwise drop the click before it ever reaches the list and vanilla's
     * scrollbar drag could never start. With this override the strip is the list itself, so the
     * screen routes clicks here and the drag below runs instead.
     */
    @Override
    public java.util.Optional<GuiEventListener> getChildAt(double x, double y) {
        if (this.scrollable() && this.overScrollbar(x, y)) {
            return java.util.Optional.of(this);
        }
        return super.getChildAt(x, y);
    }

    /** The scrollbar strip: the gutter band the slim thumb is drawn in, full list height. */
    private boolean overScrollbar(double x, double y) {
        return x >= this.scrollBarX() && x < this.getX() + this.getWidth()
                && y >= this.getY() && y < this.getBottom();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (this.scrollable() && event.button() == 0 && this.overScrollbar(event.x(), event.y())) {
            this.draggingScrollbar = true;
            // Grab-and-centre: the thumb jumps to the pointer, like a website scrollbar.
            this.setScrollAmount(this.scrollForPointerY(event.y()));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingScrollbar) {
            this.setScrollAmount(this.scrollForPointerY(event.y()));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        this.draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    /** Maps a pointer y to the scroll amount that centres the thumb under it. */
    private double scrollForPointerY(double pointerY) {
        float trackTop = this.getY() + 2.0f;
        float trackH = this.getHeight() - 4.0f;
        float span = trackH - this.scrollerHeight();
        if (span <= 0.0f) {
            return 0.0;
        }
        double target = (pointerY - trackTop - this.scrollerHeight() / 2.0) / span;
        return Math.clamp(target, 0.0, 1.0) * this.maxScrollAmount();
    }

    /**
     * Every scroll change that is not ours (scrollbar drag, keyboard, scrollToEntry) is
     * authoritative: cancel the glide and apply instantly, so the thumb keeps up with the pointer.
     */
    @Override
    public void setScrollAmount(double amount) {
        this.glideVelocity = 0.0;
        super.setScrollAmount(amount);
    }

    /**
     * Jumps to a scroll offset, cancelling any glide. Kept for the preview workbench, which pins
     * the list at exact offsets for screenshots.
     */
    public void smoothScrollTo(double target) {
        this.setScrollAmount(target);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        // Coast before super so the rows, scrollbar and separators all extract from the freshly
        // advanced amount in the same frame.
        this.advanceGlide();
        super.extractWidgetRenderState(gfx, mouseX, mouseY, partialTick);
    }

    private void advanceGlide() {
        // Track the frame gap every frame so it's fresh when a wheel event arrives after a long
        // idle period; a stale gap would teleport the glide on the first frame.
        long now = Util.getMillis();
        double dt = this.lastFrameMs < 0
                ? 0.0
                : Math.min((now - this.lastFrameMs) / 1000.0, MAX_FRAME_SECONDS);
        this.lastFrameMs = now;
        if (this.glideVelocity == 0.0 || !this.scrollable()) {
            return;
        }
        double scrolled = this.scrollAmount() + this.glideVelocity * dt;
        this.glideVelocity *= Math.exp(-dt * SCROLL_FRICTION);
        if (Math.abs(this.glideVelocity) < SCROLL_STOP) {
            this.glideVelocity = 0.0;
        }
        double clamped = Math.clamp(scrolled, 0.0, this.maxScrollAmount());
        if (clamped != scrolled) {
            // Reached an end of the list: stop dead instead of pressing against the edge.
            this.glideVelocity = 0.0;
        }
        // The super setter, not the override: the glide must not cancel itself.
        super.setScrollAmount(clamped);
    }

    /**
     * Draws each row shifted by the scroll fraction vanilla drops: rows sit at
     * {@code firstEntryY - (int) scrollAmount}, so the true (fractional) position is
     * {@code rowY - frac}. Without this the glide moves content in whole-GUI-pixel stair-steps
     * (3 screen px per GUI px at scale 3) instead of coasting smoothly.
     */
    @Override
    protected void extractItem(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick, Row entry) {
        double frac = this.scrollAmount() - Math.floor(this.scrollAmount());
        gfx.pose().pushMatrix();
        gfx.pose().translate(0.0f, (float) -frac);
        super.extractItem(gfx, mouseX, mouseY, partialTick, entry);
        gfx.pose().popMatrix();
    }

    @Override
    protected void extractListBackground(GuiGraphicsExtractor gfx) {
        // The screen draws its own rounded panel.
    }

    @Override
    protected void extractListSeparators(GuiGraphicsExtractor gfx) {
        // No vanilla row separators.
    }

    @Override
    protected void extractScrollbar(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        if (!this.scrollable()) {
            return;
        }
        // Slim rounded scrollbar instead of the vanilla sprite one. The thumb is drawn from the
        // continuous position instead of vanilla's scrollBarY(), which truncates the scroll
        // amount to an int and integer-divides, so it stair-steps during the animation.
        float w = 4.0f;
        float x = this.scrollBarX() + (this.scrollbarWidth() - w) / 2.0f;
        boolean hovered = mouseX >= x - 3.0f && mouseX <= x + w + 3.0f
                && mouseY >= this.getY() && mouseY <= this.getY() + this.getHeight();
        float trackTop = this.getY() + 2.0f;
        float trackH = this.getHeight() - 4.0f;
        Ui.pill(gfx, x, trackTop, w, trackH, this.theme.card);
        float span = trackH - this.scrollerHeight();
        float thumbY = trackTop + (float) (this.scrollAmount() * span / this.maxScrollAmount());
        Ui.pill(gfx, x, thumbY, w, this.scrollerHeight(),
                hovered ? this.theme.scrollbarHover : this.theme.scrollbar);
    }

    /** Base row: draws its own rounded card; interactive children live in {@link #children()}. */
    public abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
        /** Set by {@link CardList#addEntry}; the only way back to the list (and its theme). */
        private CardList owningList;

        public abstract List<? extends GuiEventListener> children();

        @Override
        @SuppressWarnings("unchecked")
        public List<? extends NarratableEntry> narratables() {
            // Row children are always AbstractWidgets, which are both GuiEventListener and
            // NarratableEntry, so the same list serves both dispatch paths.
            return (List<? extends NarratableEntry>) (List<?>) this.children();
        }

        /** Card top edge; the row pitch includes the gap, the card does not. */
        protected int cardY() {
            return this.getY();
        }

        protected Theme theme() {
            return this.owningList != null ? this.owningList.theme : Theme.dark();
        }

        protected void drawRowCard(GuiGraphicsExtractor gfx, boolean hovered) {
            Ui.roundRectBorder(gfx, this.getX(), this.cardY(), this.getWidth(), CARD_HEIGHT, CARD_RADIUS,
                    hovered ? this.theme().cardHover : this.theme().card,
                    hovered ? this.theme().cardBorderHover : this.theme().cardBorder, 1.0f);
        }

        /** Baseline for a single line of text vertically centred in the card. */
        protected int textY() {
            return this.cardY() + (CARD_HEIGHT - Ui.font().lineHeight) / 2 + 1;
        }
    }
}
