package net.fayber.moderngui.widget;

import net.fayber.moderngui.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.fayber.moderngui.render.Ui;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Radio group: a set of options sharing one selection, each drawn as a 14px circle with a label
 * to its right. Exactly one option is selected at a time; clicking a row selects it.
 *
 * <p>This is one widget, not a container of child buttons: the rows are laid out and
 * hit-tested manually inside {@link #mouseClicked}, which keeps the whole group a single entry
 * in the screen's child list and a single narration target.
 *
 * <p>Binding pattern: live-read. The selected index is read through {@code selectedIndex}
 * every frame, so external changes (a config reload, another control selecting a related option)
 * show immediately, and clicks report through {@code onSelect} without owning the state.
 *
 * <p>Lays out vertically by default (rows 18 tall with a 4px gap, all rows as wide as the widest
 * label); {@link #horizontal()} lays the rows left to right with a 16px gap instead (each column
 * only as wide as its own label). The widget sizes itself to its options. Hovering a row
 * brightens its circle and label; the selected row shows the accent fill with an inner dot.
 */
public class RadioGroup extends AbstractButton {
    /** Diameter of the radio circle. */
    public static final int CIRCLE = 14;
    /** Row height. */
    public static final int ROW_HEIGHT = 18;
    /** Gap between the circle and its label. */
    private static final int LABEL_GAP = 6;
    /** Gap between rows (vertical) or row columns (horizontal). */
    private static final int VERTICAL_GAP = 4;
    private static final int HORIZONTAL_GAP = 16;
    /** Radius of the inner selection dot. */
    private static final float DOT_RADIUS = 4.0f;

    private final List<Component> options;
    private final int[] rowWidths;
    private final IntSupplier selectedIndex;
    private final IntConsumer onSelect;

    protected Theme theme = Theme.dark();
    private boolean horizontal = false;
    /** Optional accent override for the selected circle; -1 uses the theme. */
    private int selectedOverride = -1;
    private int selectedHoverOverride = -1;

    public RadioGroup(int x, int y, List<String> options, IntSupplier selectedIndex, IntConsumer onSelect) {
        super(x, y, 0, 0, Component.empty());
        this.options = options.stream().map(Ui::ui).toList();
        this.selectedIndex = selectedIndex;
        this.onSelect = onSelect;
        this.rowWidths = new int[this.options.size()];
        int widest = 0;
        for (int i = 0; i < this.options.size(); i++) {
            this.rowWidths[i] = CIRCLE + LABEL_GAP + Ui.font().width(this.options.get(i));
            widest = Math.max(widest, this.rowWidths[i]);
        }
        this.relayout(widest);
    }

    /** Sizes the widget for the current orientation; called from the ctor and from {@link #horizontal}. */
    private void relayout(int widestRow) {
        if (this.horizontal) {
            int total = 0;
            for (int i = 0; i < this.rowWidths.length; i++) {
                total += this.rowWidths[i] + (i > 0 ? HORIZONTAL_GAP : 0);
            }
            this.setWidth(total);
            this.setHeight(ROW_HEIGHT);
        } else {
            this.setWidth(widestRow);
            this.setHeight(this.rowWidths.length * ROW_HEIGHT
                    + (this.rowWidths.length - 1) * VERTICAL_GAP);
        }
    }

    /** Lays the options out left to right instead of the default vertical stack. */
    public RadioGroup horizontal() {
        this.horizontal = true;
        int widest = 0;
        for (int w : this.rowWidths) {
            widest = Math.max(widest, w);
        }
        this.relayout(widest);
        return this;
    }

    public RadioGroup theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    /** Overrides the selected circle fill for both states (defaults: theme accent / accentHover). */
    public RadioGroup selectedColor(int fill, int hover) {
        this.selectedOverride = fill;
        this.selectedHoverOverride = hover;
        return this;
    }

    /**
     * Writes the hit rectangle of row {@code index} into {@code out} as
     * {@code [x, y, width, height]} in screen coordinates.
     */
    private void rowRect(int index, int[] out) {
        if (this.horizontal) {
            int x = this.getX();
            for (int i = 0; i < index; i++) {
                x += this.rowWidths[i] + HORIZONTAL_GAP;
            }
            out[0] = x;
            out[1] = this.getY();
            out[2] = this.rowWidths[index];
        } else {
            out[0] = this.getX();
            out[1] = this.getY() + index * (ROW_HEIGHT + VERTICAL_GAP);
            out[2] = this.getWidth();
        }
        out[3] = ROW_HEIGHT;
    }

    private static boolean inRect(int mx, int my, int[] rect) {
        return mx >= rect[0] && mx < rect[0] + rect[2] && my >= rect[1] && my < rect[1] + rect[3];
    }

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
        int[] rect = new int[4];
        for (int i = 0; i < this.options.size(); i++) {
            this.rowRect(i, rect);
            if (inRect(mx, my, rect)) {
                this.onSelect.accept(i);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        int selected = this.selectedIndex.getAsInt();
        int[] rect = new int[4];
        for (int i = 0; i < this.options.size(); i++) {
            this.rowRect(i, rect);
            boolean hovered = inRect(mouseX, mouseY, rect);
            boolean isSelected = i == selected;

            float cx = rect[0] + CIRCLE / 2.0f;
            float cy = rect[1] + ROW_HEIGHT / 2.0f;
            if (isSelected) {
                int fill = hovered
                        ? (this.selectedHoverOverride >= 0 ? this.selectedHoverOverride : this.theme.accentHover)
                        : (this.selectedOverride >= 0 ? this.selectedOverride : this.theme.accent);
                Ui.circle(gfx, cx, cy, CIRCLE / 2.0f, fill);
                Ui.circle(gfx, cx, cy, DOT_RADIUS, this.theme.textOnAccent);
            } else {
                // Border circle with the fill inset by 1px, matching Ui.roundRectBorder's look.
                Ui.circle(gfx, cx, cy, CIRCLE / 2.0f, hovered ? this.theme.cardBorderHover : this.theme.cardBorder);
                Ui.circle(gfx, cx, cy, CIRCLE / 2.0f - 1.0f, hovered ? this.theme.cardHover : this.theme.card);
            }

            int textY = rect[1] + (ROW_HEIGHT - Ui.font().lineHeight) / 2 + 1;
            int textColor = isSelected || hovered ? this.theme.text : this.theme.textSecondary;
            Ui.text(gfx, this.options.get(i), rect[0] + CIRCLE + LABEL_GAP, textY, textColor);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
