package net.fayber.moderngui.widget;

import net.fayber.moderngui.render.Theme;
import net.fayber.moderngui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A header row that expands and collapses a content area, with an animated height. The header is
 * a full-width 28px row: a chevron (up when closed, down when open), a bold title, a hover fill
 * and a hairline border. Clicking the header toggles the section; the content children ease in
 * and out with a time-normalised exponential and are clipped to the visible fraction, so they
 * reveal top-down instead of popping.
 *
 * <p>Children are positioned in content coordinates by {@link #pack()} (a column layout with a
 * 4px gap, run automatically as children are added). Their {@code getX()}/{@code getY()} are
 * rewritten every frame to their shifted screen positions, so content coordinates live in the
 * widget: after moving or resizing children, call {@link #pack()} again.
 *
 * <p>The widget's reported height always includes the full content height ({@code getHeight()} =
 * header + content), even while collapsed: the screen is expected to place sections in a column
 * and let expanded ones overlap what sits below (or scroll), exactly like a web accordion on a
 * page with fixed flow. An {@link #onChange} hook fires on every toggle, including programmatic
 * ones through {@link #open(boolean)}.
 */
public class CollapsibleSection extends AbstractWidget {
    /** Header height in px. */
    public static final int HEADER_H = 28;
    /** Vertical gap between content children (see {@link #pack()}). */
    public static final int CONTENT_GAP = 4;
    private static final float HEADER_RADIUS = 6.0f;
    /** Icon size and left inset inside the header. */
    private static final float ICON_SIZE = 12.0f;
    private static final int ICON_INSET = 10;
    /** Title x offset from the widget's left edge. */
    private static final int TITLE_X = 30;
    /** Content ease speed (per second, time-normalised). */
    private static final float EXPAND_SPEED = 14.0f;

    protected Theme theme = Theme.dark();

    private final String title;
    private boolean open;
    /** Expanded fraction in [0, 1]; eases toward {@link #open}. */
    private float fraction;
    private long lastFrameMs = -1L;
    /** Fraction below which the children are not drawn at all. */
    private static final float DRAW_THRESHOLD = 0.02f;
    /** Fraction above which clicks reach the children. */
    private static final float INTERACT_THRESHOLD = 0.9f;

    private final List<AbstractWidget> content = new ArrayList<>();
    // Content-space x/y per child, parallel to content.
    private final List<int[]> contentPos = new ArrayList<>();
    private int contentHeight;

    private Runnable onChange;
    private AbstractWidget pressedChild;

    public CollapsibleSection(int x, int y, int width, String title, boolean initiallyOpen) {
        super(x, y, width, HEADER_H, Component.empty());
        this.title = title;
        this.open = initiallyOpen;
        this.fraction = initiallyOpen ? 1.0f : 0.0f;
        this.updateHeight();
    }

    /**
     * Appends a child to the content area. Its current position is captured as content
     * coordinates, then the column layout is re-run (4px gap), so order of addition is the visual
     * order.
     */
    public CollapsibleSection add(AbstractWidget child) {
        this.content.add(child);
        this.pack();
        return this;
    }

    /**
     * Lays the content children in a column with a 4px gap and re-captures their content
     * coordinates and the content height. Call it again after moving or resizing children.
     */
    public void pack() {
        this.contentPos.clear();
        int y = 0;
        for (AbstractWidget child : this.content) {
            child.setX(0);
            child.setY(y);
            this.contentPos.add(new int[]{child.getX(), child.getY()});
            y += child.getHeight() + CONTENT_GAP;
        }
        this.contentHeight = Math.max(0, y - CONTENT_GAP);
        this.updateHeight();
    }

    /** Height of the content area from the last {@link #pack()}. */
    public int contentHeight() {
        return this.contentHeight;
    }

    private void updateHeight() {
        // The reported height always includes the collapsed content; see the class javadoc.
        this.setHeight(HEADER_H + this.contentHeight);
    }

    public CollapsibleSection theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    /** Runs after the section toggles (read {@link #isOpen()} for the new state). */
    public CollapsibleSection onChange(Runnable onChange) {
        this.onChange = onChange;
        return this;
    }

    /** Sets the open state programmatically; animates like a click on the header. */
    public CollapsibleSection open(boolean open) {
        this.setOpen(open);
        return this;
    }

    /** Whether the section is (targeting) open; true while the ease is still catching up. */
    public boolean isOpen() {
        return this.open;
    }

    private void setOpen(boolean open) {
        if (this.open != open) {
            this.open = open;
            if (this.onChange != null) {
                this.onChange.run();
            }
        }
    }

    /** Clicks on the header toggle the section; clicks in the open content reach the children. */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.isMouseOver(event.x(), event.y())) {
            return false;
        }
        if (event.y() < this.getY() + HEADER_H) {
            this.setOpen(!this.open);
            return true;
        }
        // Mid-animation the content is still moving; swallow instead of forwarding to widgets
        // whose positions are about to shift under the pointer.
        if (this.fraction <= INTERACT_THRESHOLD) {
            return true;
        }
        this.pressedChild = this.childAt(event.x(), event.y());
        if (this.pressedChild != null) {
            return this.pressedChild.mouseClicked(event, doubleClick);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.pressedChild != null) {
            AbstractWidget child = this.pressedChild;
            this.pressedChild = null;
            return child.mouseReleased(event);
        }
        if (this.fraction > INTERACT_THRESHOLD) {
            AbstractWidget child = this.childAt(event.x(), event.y());
            return child != null && child.mouseReleased(event);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.pressedChild != null) {
            return this.pressedChild.mouseDragged(event, deltaX, deltaY);
        }
        if (this.fraction > INTERACT_THRESHOLD) {
            AbstractWidget child = this.childAt(event.x(), event.y());
            return child != null && child.mouseDragged(event, deltaX, deltaY);
        }
        return false;
    }

    private AbstractWidget childAt(double mx, double my) {
        int shift = (int) ((1.0f - this.fraction) * this.contentHeight);
        for (int i = 0; i < this.content.size(); i++) {
            AbstractWidget child = this.content.get(i);
            int[] pos = this.contentPos.get(i);
            int cx = this.getX() + pos[0];
            int cy = this.getY() + HEADER_H + pos[1] - shift;
            if (mx >= cx && mx < cx + child.getWidth()
                    && my >= cy && my < cy + child.getHeight()) {
                return child;
            }
        }
        return null;
    }

    public List<? extends GuiEventListener> children() {
        return Collections.unmodifiableList(this.content);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float dt = this.lastFrameMs < 0
                ? 0.0f
                : Math.min((now - this.lastFrameMs) / 1000.0f, 0.1f);
        this.lastFrameMs = now;

        // Time-normalised exponential ease toward the open state; the first frame is exact
        // (the ctor seeds the fraction, and a dt of 0 keeps it there).
        float target = this.open ? 1.0f : 0.0f;
        this.fraction += (target - this.fraction) * (1.0f - (float) Math.exp(-dt * EXPAND_SPEED));

        this.extractHeader(gfx, mouseX, mouseY);
        if (this.fraction > DRAW_THRESHOLD) {
            this.extractContent(gfx, mouseX, mouseY, partialTick);
        }
    }

    private void extractHeader(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        boolean hovered = mouseX >= this.getX() && mouseX < this.getX() + this.getWidth()
                && mouseY >= this.getY() && mouseY < this.getY() + HEADER_H;
        Ui.roundRectBorder(gfx, this.getX(), this.getY(), this.getWidth(), HEADER_H, HEADER_RADIUS,
                hovered ? this.theme.cardHover : this.theme.card,
                hovered ? this.theme.cardBorderHover : this.theme.cardBorder, 1.0f);

        Icons.Glyph chevron = this.open ? Icons.CHEVRON_DOWN : Icons.CHEVRON_UP;
        chevron.draw(gfx, this.getX() + ICON_INSET + ICON_SIZE / 2.0f, this.getY() + HEADER_H / 2.0f,
                ICON_SIZE, this.theme.textSecondary);

        int textY = this.getY() + (HEADER_H - Ui.font().lineHeight) / 2 + 1;
        Ui.text(gfx, Ui.uiBold(this.title), this.getX() + TITLE_X, textY, this.theme.text);
    }

    private void extractContent(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        // Children ride up out of the header as the section closes: their screen position is the
        // content origin minus the hidden height. The fraction is drawn sub-pixel (the pose
        // carries what the int shift drops) so the ease does not stair-step.
        float shift = (1.0f - this.fraction) * this.contentHeight;
        int shiftPx = (int) shift;
        float frac = shift - shiftPx;

        int visibleBottom = this.getY() + HEADER_H + Math.round(this.contentHeight * this.fraction);
        gfx.enableScissor(this.getX(), this.getY() + HEADER_H,
                this.getX() + this.getWidth(), visibleBottom);
        gfx.pose().pushMatrix();
        gfx.pose().translate(0.0f, (float) -frac);
        for (int i = 0; i < this.content.size(); i++) {
            AbstractWidget child = this.content.get(i);
            int[] pos = this.contentPos.get(i);
            child.setPosition(this.getX() + pos[0], this.getY() + HEADER_H + pos[1] - shiftPx);
            child.extractRenderState(gfx, mouseX, mouseY, partialTick);
        }
        gfx.pose().popMatrix();
        gfx.disableScissor();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // The header title is narration enough; children narrate through children().
    }
}
