package net.fayber.moderngui.widget;

import net.fayber.moderngui.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A flow layout as a widget: children are placed left to right and wrap to the next line when
 * they would pass the widget's width, like CSS {@code flex-wrap}. Use it for badge rows, chip
 * clouds and button groups that must survive resizes without hand-placing every child.
 *
 * <p>The wrap runs eagerly: every {@link #add} re-flows all children and grows the widget's
 * height to the total, so a screen that reads {@code getHeight()} right after building the flow
 * already sees the final size. Children are positioned in flow coordinates by the reflow and are
 * repositioned every frame from the stored coordinates plus the widget origin, so moving or
 * resizing a child just needs another {@link #relayout()}.
 *
 * <p>Mouse input is forwarded to the child under the pointer; keyboard input is left to the
 * owning screen, which dispatches focus through {@link #children()}.
 */
public class HFlow extends AbstractWidget {
    /** Horizontal distance between children on one line. */
    public static final int GAP_X = 6;
    /** Vertical distance between lines. */
    public static final int GAP_Y = 6;

    protected Theme theme = Theme.dark();

    private final List<AbstractWidget> children = new ArrayList<>();
    // Flow-space x/y per child, parallel to children.
    private final List<int[]> flowPos = new ArrayList<>();

    private AbstractWidget pressedChild;

    /** {@code width} is the wrap width; children past it start a new line. */
    public HFlow(int x, int y, int width) {
        super(x, y, width, 0, Component.empty());
    }

    /**
     * Appends a child and re-flows. The child's size must already be final (set its width and
     * height before adding); the flow never resizes its children.
     */
    public HFlow add(AbstractWidget child) {
        this.children.add(child);
        this.relayout();
        return this;
    }

    /**
     * Re-runs the flow layout: children wrap at the widget's width and the widget's height is set
     * to the total flow height. Call it after moving or resizing children (or after changing the
     * width with {@code setWidth}).
     */
    public void relayout() {
        this.flowPos.clear();
        int x = 0;
        int y = 0;
        int lineH = 0;
        for (AbstractWidget child : this.children) {
            int w = child.getWidth();
            if (!this.flowPos.isEmpty() && x + w > this.getWidth()) {
                x = 0;
                y += lineH + GAP_Y;
                lineH = 0;
            }
            this.flowPos.add(new int[]{x, y});
            lineH = Math.max(lineH, child.getHeight());
            x += w + GAP_X;
        }
        this.setHeight(this.flowPos.isEmpty() ? 0 : y + lineH);
    }

    /** Clicks are forwarded to the child under the pointer; the flow swallows the rest. */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.isMouseOver(event.x(), event.y())) {
            return false;
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
        AbstractWidget child = this.childAt(event.x(), event.y());
        return child != null && child.mouseReleased(event);
    }

    // Drags go to the captured child, so a slider inside the flow keeps its pointer.
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.pressedChild != null) {
            return this.pressedChild.mouseDragged(event, deltaX, deltaY);
        }
        AbstractWidget child = this.childAt(event.x(), event.y());
        return child != null && child.mouseDragged(event, deltaX, deltaY);
    }

    private AbstractWidget childAt(double mx, double my) {
        for (int i = 0; i < this.children.size(); i++) {
            AbstractWidget child = this.children.get(i);
            int[] pos = this.flowPos.get(i);
            int cx = this.getX() + pos[0];
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
        // Reposition from the flow coordinates every frame (origin/resize-safe), then extract
        // through each child's final extractRenderState.
        for (int i = 0; i < this.children.size(); i++) {
            AbstractWidget child = this.children.get(i);
            int[] pos = this.flowPos.get(i);
            child.setPosition(this.getX() + pos[0], this.getY() + pos[1]);
            child.extractRenderState(gfx, mouseX, mouseY, partialTick);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // The flow is a pure container; children narrate through children().
    }
}
