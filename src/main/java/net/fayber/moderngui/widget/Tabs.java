package net.fayber.moderngui.widget;

import net.fayber.moderngui.render.Theme;
import net.fayber.moderngui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A horizontal tab bar: a row of text labels with a 2px accent underline under the active one.
 * The active index is read through a supplier every frame (live-read, like {@link PillToggle}),
 * so external state changes show immediately; the underline eases to whichever tab is active,
 * which is what turns a stack of labels into a navigation control.
 *
 * <p>The bar sizes itself to its labels: each tab is the text width plus padding, the widget
 * width is the row total. Hit-testing is per tab. Keyboard navigation is deliberately not
 * implemented; the bar is pointer-first and the owning screen can move focus between the bar and
 * its content panels.
 */
public class Tabs extends AbstractWidget {
    /** Widget height: labels are vertically centred, the underline sits on the bottom edge. */
    public static final int HEIGHT = 30;
    /** Horizontal padding inside a tab, on each side of the label. */
    private static final int TAB_PADDING = 12;
    /** Underline thickness and ease speed (per second, time-normalised). */
    private static final float UNDERLINE_H = 2.0f;
    private static final float UNDERLINE_SPEED = 18.0f;

    protected Theme theme = Theme.dark();

    private final List<Component> labels;
    private final IntSupplier activeIndex;
    private final IntConsumer onSelect;
    private final List<Integer> tabWidths = new ArrayList<>();

    /** Optional hook fired after the selection changes. */
    private Runnable onChange;

    /** Current underline left edge; -1 means "not initialised yet" (first frame snaps). */
    private float underlineX = -1.0f;
    /** Current underline width; -1 means "not initialised yet". */
    private float underlineW = -1.0f;
    /** Underline ease target (the active tab's text rect), updated every frame. */
    private float underlineTargetX;
    private float underlineTargetW;
    /** Timestamp of the last drawn frame, for the time-normalised ease. */
    private long lastFrameMs = -1L;

    /**
     * @param labels      tab labels, in left-to-right order; translatables resolve at draw time
     * @param activeIndex supplies the active tab index every frame (live-read)
     * @param onSelect    called with the clicked tab index
     */
    public Tabs(int x, int y, List<Component> labels, IntSupplier activeIndex, IntConsumer onSelect) {
        super(x, y, 0, HEIGHT, Component.empty());
        this.labels = List.copyOf(labels);
        this.activeIndex = activeIndex;
        this.onSelect = onSelect;
        // Tab widths are measured once here, so the strip is sized to the labels as of
        // construction even though a translatable can change its width later.
        int total = 0;
        for (Component label : this.labels) {
            int w = Ui.font().width(Ui.ui(label)) + TAB_PADDING * 2;
            this.tabWidths.add(w);
            total += w;
        }
        this.setWidth(total);
    }

    public Tabs theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    /** Runs after a click changes the selection (read the supplier for the new index). */
    public Tabs onChange(Runnable onChange) {
        this.onChange = onChange;
        return this;
    }

    /** Clicks inside the bar select the tab under the pointer; the bar swallows the rest. */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.isMouseOver(event.x(), event.y())) {
            return false;
        }
        int index = this.tabAt(event.x());
        if (index >= 0 && index != this.activeIndex.getAsInt()) {
            this.onSelect.accept(index);
            if (this.onChange != null) {
                this.onChange.run();
            }
        }
        return true;
    }

    /** The tab index containing the x coordinate, or -1. */
    private int tabAt(double mx) {
        int cursor = this.getX();
        for (int i = 0; i < this.tabWidths.size(); i++) {
            int w = this.tabWidths.get(i);
            if (mx >= cursor && mx < cursor + w) {
                return i;
            }
            cursor += w;
        }
        return -1;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float dt = this.lastFrameMs < 0
                ? 0.0f
                : Math.min((now - this.lastFrameMs) / 1000.0f, 0.1f);
        this.lastFrameMs = now;

        int active = this.activeIndex.getAsInt();
        int cursor = this.getX();
        for (int i = 0; i < this.tabWidths.size(); i++) {
            int w = this.tabWidths.get(i);
            boolean isActive = i == active;
            boolean hovered = i != active
                    && mouseX >= cursor && mouseX < cursor + w
                    && mouseY >= this.getY() && mouseY < this.getY() + HEIGHT;
            int color = isActive
                    ? this.theme.text
                    : hovered ? this.theme.text : this.theme.textSecondary;
            // Active tabs render in the bold weight; width metrics below use the drawn label so
            // the underline matches the text exactly.
            Component label = isActive
                    ? Ui.uiBold(this.labels.get(i))
                    : Ui.ui(this.labels.get(i));
            int textY = this.getY() + (int) ((HEIGHT - UNDERLINE_H - Ui.font().lineHeight) / 2.0f) + 1;
            Ui.text(gfx, label, cursor + TAB_PADDING, textY, color);
            if (isActive) {
                this.targetUnderline(cursor + TAB_PADDING, Ui.font().width(label));
            }
            cursor += w;
        }

        // Ease the underline to its target; the first frame snaps (nothing drawn at -1 yet).
        if (this.underlineW >= 0.0f && dt > 0.0f) {
            float t = 1.0f - (float) Math.exp(-dt * UNDERLINE_SPEED);
            this.underlineX += (this.underlineTargetX - this.underlineX) * t;
            this.underlineW += (this.underlineTargetW - this.underlineW) * t;
        }
        if (this.underlineW >= 0.0f) {
            Ui.rect(gfx, this.underlineX, this.getY() + HEIGHT - UNDERLINE_H,
                    this.underlineW, UNDERLINE_H, this.theme.accent);
        }
    }

    private void targetUnderline(int x, int w) {
        if (this.underlineW < 0.0f) {
            this.underlineX = x;
            this.underlineW = w;
        }
        this.underlineTargetX = x;
        this.underlineTargetW = w;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Narration of the active tab is left to the owning screen; the bar is pointer-first.
    }
}
