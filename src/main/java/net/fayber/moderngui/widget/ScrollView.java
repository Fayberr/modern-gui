package net.fayber.moderngui.widget;

import net.fayber.moderngui.anim.ScrollState;
import net.fayber.moderngui.render.Theme;
import net.fayber.moderngui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A clipped viewport onto custom-drawn content that scrolls smoothly: wheel with a glide, a slim
 * draggable scrollbar, and content translated by a whole-physical-pixel offset so text stays
 * crisp while it moves. It is the ready-made widget around {@link ScrollState}; use the state
 * directly (via {@link #state}) for finer control, or when the content is not a widget at all.
 *
 * <p>The {@link Content} painter draws the content at rest with its top-left at the given
 * origin; the view translates the matrix stack underneath it. Pointer coordinates are handed
 * over already shifted into content space, so hover tests inside the painter just work.
 * Child widgets inside the content are not hit-tested by the view (it is for drawn content such as
 * lists of cards and text); use {@link net.fayber.moderngui.list.CardList} for real widgets.
 */
public class ScrollView extends AbstractWidget {
    /** Draws the scrolled content at rest from {@code (x, y)}, {@code width} wide. */
    @FunctionalInterface
    public interface Content {
        void paint(GuiGraphicsExtractor gfx, int x, int y, int width, int mouseX, int mouseY);
    }

    private static final float BAR_WIDTH = 4.0f;
    private static final float MIN_THUMB = 18.0f;

    private final ScrollState state = new ScrollState();
    private final Content content;
    private int contentHeight;
    private boolean draggingBar;

    protected Theme theme = Theme.dark();

    public ScrollView(int x, int y, int width, int height, int contentHeight, Content content) {
        super(x, y, width, height, Component.empty());
        this.contentHeight = contentHeight;
        this.content = content;
    }

    public ScrollView theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    /** The scroll state: configure mode, wheel step and friction, or jump/scroll from code. */
    public ScrollState state() {
        return this.state;
    }

    /** Updates the content height when it changes (rows added, text re-wrapped). */
    public ScrollView contentHeight(int contentHeight) {
        this.contentHeight = contentHeight;
        return this;
    }

    private int contentWidth() {
        return this.getWidth() - (this.state.isScrollable() ? 10 : 0);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        this.state.setSizes(this.contentHeight, this.getHeight());
        this.state.update();
        float offset = this.state.snappedOffset(Ui.scale());

        gfx.enableScissor(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight());
        gfx.pose().pushMatrix();
        gfx.pose().translate(0.0f, -offset);
        this.content.paint(gfx, this.getX(), this.getY(), this.contentWidth(),
                mouseX, Math.round(mouseY + offset));
        gfx.pose().popMatrix();
        gfx.disableScissor();

        if (this.state.isScrollable()) {
            this.drawBar(gfx, mouseX, mouseY);
        }
    }

    private float barX() {
        return this.getX() + this.getWidth() - BAR_WIDTH - 1.0f;
    }

    private float trackTop() {
        return this.getY() + 2.0f;
    }

    private float trackLength() {
        return this.getHeight() - 4.0f;
    }

    private void drawBar(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        float track = this.trackLength();
        float thumb = this.state.thumbLength(track, MIN_THUMB);
        boolean hovered = this.draggingBar || (mouseX >= this.barX() - 3.0f && mouseX <= this.barX() + BAR_WIDTH + 3.0f
                && mouseY >= this.getY() && mouseY <= this.getY() + this.getHeight());
        Ui.pill(gfx, this.barX(), this.trackTop(), BAR_WIDTH, track, this.theme.card);
        Ui.pill(gfx, this.barX(), this.trackTop() + this.state.thumbOffset(track, thumb), BAR_WIDTH, thumb,
                hovered ? this.theme.scrollbarHover : this.theme.scrollbar);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double xDelta, double yDelta) {
        if (!this.isMouseOver(mouseX, mouseY) || !this.state.isScrollable()) {
            return false;
        }
        this.state.wheel(yDelta);
        return true;
    }

    private boolean overBar(double x, double y) {
        return this.state.isScrollable() && x >= this.barX() - 4.0 && x < this.getX() + this.getWidth()
                && y >= this.getY() && y < this.getY() + this.getHeight();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.overBar(event.x(), event.y())) {
            this.draggingBar = true;
            this.dragTo(event.y());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingBar) {
            this.dragTo(event.y());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean was = this.draggingBar;
        this.draggingBar = false;
        return was;
    }

    /** Grab-and-centre: the thumb jumps under the pointer and follows it, with no glide. */
    private void dragTo(double pointerY) {
        float track = this.trackLength();
        float thumb = this.state.thumbLength(track, MIN_THUMB);
        this.state.jumpTo(this.state.offsetForPointer((float) (pointerY - this.trackTop()), track, thumb));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Pure display; nothing to narrate.
    }
}
