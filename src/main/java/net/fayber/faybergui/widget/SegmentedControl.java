package net.fayber.faybergui.widget;

import net.fayber.faybergui.render.Theme;
import net.fayber.faybergui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Segmented control: a horizontal pill of N segments sharing one selection, in the style of an
 * iOS segmented control. The whole control is one rounded card; the selected segment gets a
 * rounded inset pill in the accent colour, so switching reads as a pill sliding between slots
 * rather than a row of independent buttons.
 *
 * <p>This is <b>one widget</b>, not a container of child buttons: the segments are laid out and
 * hit-tested manually inside {@link #mouseClicked}, which keeps the control a single entry in the
 * screen's child list and a single narration target.
 *
 * <p><b>Binding pattern: live-read.</b> The selected index is read through {@code selectedIndex}
 * every frame, so external changes show immediately, and clicks report through {@code onSelect}
 * without owning the state. The selection pill eases horizontally to the selected segment
 * (time-normalised, so the feel is identical at any frame rate).
 *
 * <p>Each segment is sized to its label plus padding; an optional {@link Icons.Glyph} per segment
 * is drawn left of the label and widens the segment. Configurable through {@link #theme},
 * {@link #icon} and a colour override for the selection pill.
 */
public class SegmentedControl extends AbstractButton {
    /** Height of the control. */
    public static final int HEIGHT = 26;
    /** Corner radius of the outer card. */
    private static final float RADIUS = 6.0f;
    /** Inset between the outer card and the selection pill on all sides. */
    private static final int INSET = 2;
    /** Horizontal room added to every segment around its label. */
    private static final int LABEL_PADDING = 20;
    /** Extra width per segment when it has an icon: icon size plus the gap to the label. */
    private static final int ICON_SIZE = 10;
    private static final int ICON_GAP = 5;
    /** Corner radius of the selection pill (outer radius minus the inset). */
    private static final float PILL_RADIUS = RADIUS - INSET;
    /** Time-normalised pill glide speed (per second). */
    private static final float PILL_SPEED = 22.0f;
    /** Frame gap cap so a stall never teleports the glide. */
    private static final float MAX_FRAME_SECONDS = 0.1f;

    private final List<Component> labels;
    private final List<Icons.Glyph> icons = new ArrayList<>();
    private final int[] segmentWidths;
    private final IntSupplier selectedIndex;
    private final IntConsumer onSelect;

    protected Theme theme = Theme.dark();
    /** Optional accent override for the selection pill; -1 uses the theme. */
    private int selectionOverride = -1;
    private int selectionHoverOverride = -1;

    /** Left edge of the selection pill actually drawn; eased toward the selected segment. */
    private float pillX;
    /** True once the pill position has been snapped for the first frame. */
    private boolean pillInitialised;
    /** Timestamp of the last drawn frame, for the time-normalised glide. */
    private long lastFrameMs = -1L;

    public SegmentedControl(int x, int y, List<String> options, IntSupplier selectedIndex, IntConsumer onSelect) {
        super(x, y, 0, HEIGHT, Component.empty());
        this.labels = options.stream().map(Ui::ui).toList();
        this.selectedIndex = selectedIndex;
        this.onSelect = onSelect;
        this.segmentWidths = new int[this.labels.size()];
        int total = 2 * INSET;
        for (int i = 0; i < this.labels.size(); i++) {
            this.segmentWidths[i] = Ui.font().width(this.labels.get(i)) + LABEL_PADDING;
            this.icons.add(null);
            total += this.segmentWidths[i];
        }
        this.setWidth(total);
    }

    public SegmentedControl theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    /** Sets the glyph drawn left of segment {@code index}'s label (pass {@code null} to clear). */
    public SegmentedControl icon(int index, Icons.Glyph glyph) {
        this.icons.set(index, glyph);
        int extra = glyph == null ? 0 : ICON_SIZE + ICON_GAP;
        this.segmentWidths[index] = Ui.font().width(this.labels.get(index)) + LABEL_PADDING + extra;
        int total = 2 * INSET;
        for (int w : this.segmentWidths) {
            total += w;
        }
        this.setWidth(total);
        return this;
    }

    /** Overrides the selection pill fill for both states (defaults: theme accent / accentHover). */
    public SegmentedControl selectionColor(int fill, int hover) {
        this.selectionOverride = fill;
        this.selectionHoverOverride = hover;
        return this;
    }

    // ------------------------------------------------------------------ geometry

    /** Left edge of segment {@code index} (the pill slides along these positions). */
    private float segmentX(int index) {
        float x = this.getX() + INSET;
        for (int i = 0; i < index; i++) {
            x += this.segmentWidths[i];
        }
        return x;
    }

    // ------------------------------------------------------------------ behaviour

    //
    // The abstract onPress hook is unused: clicks are dispatched through the overridden
    // mouseClicked below (the widget is a selector, not a push button).
    @Override
    public void onPress(net.minecraft.client.input.InputWithModifiers input) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x();
        int my = (int) event.y();
        if (mx >= this.getX() && mx < this.getX() + this.getWidth()
                && my >= this.getY() && my < this.getY() + this.getHeight()) {
            int index = 0;
            float x = this.getX() + INSET;
            while (index < this.segmentWidths.length - 1 && mx >= x + this.segmentWidths[index]) {
                x += this.segmentWidths[index];
                index++;
            }
            this.onSelect.accept(index);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        // Glide the pill toward the selected segment, time-normalised like StyledSlider's knob.
        int selected = this.selectedIndex.getAsInt();
        long now = Util.getMillis();
        float targetX = this.segmentX(selected);
        if (!this.pillInitialised) {
            this.pillX = targetX; // opening the screen should not animate the pill across
            this.pillInitialised = true;
        } else {
            float dt = Math.min((now - this.lastFrameMs) / 1000.0f, MAX_FRAME_SECONDS);
            this.pillX += (targetX - this.pillX) * (1.0f - (float) Math.exp(-dt * PILL_SPEED));
        }
        this.lastFrameMs = now;

        boolean hovered = this.isHoveredOrFocused();
        Ui.roundRectBorder(gfx, this.getX(), this.getY(), this.getWidth(), this.getHeight(), RADIUS,
                this.theme.card, hovered ? this.theme.cardBorderHover : this.theme.cardBorder, 1.0f);

        int pillFill;
        if (this.selectionOverride >= 0) {
            pillFill = hovered && this.selectionHoverOverride >= 0 ? this.selectionHoverOverride : this.selectionOverride;
        } else {
            pillFill = hovered ? this.theme.accentHover : this.theme.accent;
        }
        Ui.roundRect(gfx, this.pillX, this.getY() + INSET, this.segmentWidths[selected],
                this.getHeight() - 2 * INSET, PILL_RADIUS, pillFill);

        int textY = this.getY() + (this.getHeight() - Ui.font().lineHeight) / 2 + 1;
        float x = this.getX() + INSET;
        for (int i = 0; i < this.labels.size(); i++) {
            int segWidth = this.segmentWidths[i];
            Icons.Glyph icon = this.icons.get(i);
            int textWidth = Ui.font().width(this.labels.get(i));
            int contentWidth = textWidth + (icon != null ? ICON_SIZE + ICON_GAP : 0);
            int contentX = (int) (x + (segWidth - contentWidth) / 2.0f);
            if (icon != null) {
                int iconColor = i == selected ? this.theme.textOnAccent : this.theme.textSecondary;
                icon.draw(gfx, contentX + ICON_SIZE / 2.0f,
                        this.getY() + this.getHeight() / 2.0f, ICON_SIZE, iconColor);
            }
            int labelColor = i == selected
                    ? this.theme.textOnAccent
                    : (i == this.hoveredSegment(mouseX) ? this.theme.text : this.theme.textSecondary);
            Ui.text(gfx, this.labels.get(i), contentX + (icon != null ? ICON_SIZE + ICON_GAP : 0), textY, labelColor);
            x += segWidth;
        }
    }

    /** Index of the segment under {@code mx}, or -1 when the cursor is outside the control. */
    private int hoveredSegment(int mx) {
        if (mx < this.getX() || mx >= this.getX() + this.getWidth()) {
            return -1;
        }
        float x = this.getX() + INSET;
        for (int i = 0; i < this.segmentWidths.length; i++) {
            if (mx < x + this.segmentWidths[i]) {
                return i;
            }
            x += this.segmentWidths[i];
        }
        return -1;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
