package net.fayber.faybergui.widget;

import net.fayber.faybergui.render.Theme;
import net.fayber.faybergui.render.Ui;
import net.fayber.faybergui.screen.ListPopup;
import net.fayber.faybergui.screen.PopupHost;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Dropdown / combobox: a flat card showing the current value with a chevron; clicking opens a
 * floating menu of options (a {@link ListPopup} on the screen's {@link PopupHost}) layered above
 * every other widget. Selection writes through immediately.
 *
 * <p>The menu needs a {@link PopupHost} for correct layering: pass one with {@link #host}
 * (on a {@link net.fayber.faybergui.screen.FayberGuiScreen} use {@code screen.popupHost()}). Without
 * a host the options expand inline below the widget instead, which works but can be drawn over by
 * widgets extracted later.
 */
public class Dropdown extends AbstractButton {
    /** Right-edge room for the chevron. */
    private static final int CHEVRON_ROOM = 18;

    private final IntSupplier selectedIndex;
    private final IntConsumer onSelect;
    private final List<Component> optionLabels;
    @Nullable
    private PopupHost host;

    protected Theme theme = Theme.dark();
    private boolean open;
    /** Inline fallback state (no host): which row is under the mouse while open. */
    private int inlineHover = -1;
    private Runnable onChange;

    public Dropdown(int x, int y, int w, int h, List<String> options, IntSupplier selectedIndex,
                    IntConsumer onSelect) {
        super(x, y, w, h, Component.empty());
        this.optionLabels = new ArrayList<>();
        for (String option : options) {
            this.optionLabels.add(Ui.ui(option));
        }
        this.selectedIndex = selectedIndex;
        this.onSelect = onSelect;
    }

    public Dropdown host(@Nullable PopupHost host) {
        this.host = host;
        return this;
    }

    public Dropdown theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    /** Runs after a value is picked from the menu (the select callback already ran). */
    public Dropdown onChange(Runnable onChange) {
        this.onChange = onChange;
        return this;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (this.host != null) {
            // The host handles the next click (inside or outside the menu).
            this.host.openMenu(this.buildPopup());
        } else {
            this.open = !this.open;
            this.inlineHover = -1;
        }
    }

    private ListPopup buildPopup() {
        List<ListPopup.Option> options = new ArrayList<>();
        int current = this.selectedIndex.getAsInt();
        for (int i = 0; i < this.optionLabels.size(); i++) {
            int index = i;
            options.add(new ListPopup.Option(this.optionLabels.get(i), i == current, () -> {
                this.onSelect.accept(index);
                if (this.onChange != null) {
                    this.onChange.run();
                }
            }));
        }
        return new ListPopup(this.getX(), this.getY(), this.getHeight(), this.getWidth(), options);
    }

    /**
     * Inline mode (no host): the expanded menu extends the hit area, so a click on a row picks
     * it and a click back on the widget closes the menu. The super path (press on the widget
     * while closed) is left intact.
     */
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (this.host == null && this.open && this.active && this.visible) {
            int row = this.inlineRowAt(event.y());
            if (row >= 0) {
                this.onSelect.accept(row);
                if (this.onChange != null) {
                    this.onChange.run();
                }
                this.open = false;
                return true;
            }
            if (super.isMouseOver(event.x(), event.y())) {
                this.open = false; // second click on the widget closes the menu
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (super.isMouseOver(mouseX, mouseY)) {
            return true;
        }
        if (this.host == null && this.open) {
            // Inline menu extends the hit area so rows are clickable.
            int count = Math.min(this.optionLabels.size(), ListPopup.MAX_VISIBLE);
            int top = this.inlineMenuTop();
            return mouseX >= this.getX() && mouseX < this.getX() + this.getWidth()
                    && mouseY >= top && mouseY < top + count * ListPopup.ROW_HEIGHT;
        }
        return false;
    }

    /**
     * The inline menu's top edge: right below the widget, flipped above it when the window has
     * no room below (the same rule the hosted {@link ListPopup} applies).
     */
    private int inlineMenuTop() {
        int count = Math.min(this.optionLabels.size(), ListPopup.MAX_VISIBLE);
        int height = count * ListPopup.ROW_HEIGHT + 4;
        int below = this.getY() + this.getHeight() + 2;
        int screenH = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (below + height > screenH - 2 && this.getY() - 2 - height >= 0) {
            return this.getY() - 2 - height;
        }
        return below;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHoveredOrFocused();
        Ui.roundRectBorder(gfx, this.getX(), this.getY(), this.getWidth(), this.getHeight(), FlatButton.RADIUS,
                hovered ? this.theme.cardHover : this.theme.card,
                hovered ? this.theme.cardBorderHover : this.theme.cardBorder, 1.0f);

        int current = this.selectedIndex.getAsInt();
        Component label = current >= 0 && current < this.optionLabels.size()
                ? this.optionLabels.get(current) : Component.empty();
        int textY = this.getY() + (this.getHeight() - Ui.font().lineHeight) / 2 + 1;
        Ui.text(gfx, label, this.getX() + 10, textY, hovered ? this.theme.text : this.theme.textSecondary);

        float chevronCx = this.getX() + this.getWidth() - CHEVRON_ROOM / 2.0f - 4;
        float chevronCy = this.getY() + this.getHeight() / 2.0f;
        Icons.CHEVRON_DOWN.draw(gfx, chevronCx, chevronCy, 12,
                hovered ? this.theme.textSecondary : this.theme.textMuted);

        if (this.host == null && this.open) {
            this.inlineHover = this.inlineRowAt(mouseY);
            this.extractInlineMenu(gfx, this.theme);
        }
    }

    /** Inline menu (no host): a simple list right below the widget (above it near the floor). */
    private void extractInlineMenu(GuiGraphicsExtractor gfx, Theme theme) {
        int count = Math.min(this.optionLabels.size(), ListPopup.MAX_VISIBLE);
        int top = this.inlineMenuTop();
        Ui.shadow(gfx, this.getX(), top, this.getWidth(), count * ListPopup.ROW_HEIGHT, 6.0f, 4.0f, 3);
        Ui.roundRectBorder(gfx, this.getX(), top, this.getWidth(), count * ListPopup.ROW_HEIGHT, 6.0f,
                theme.card, theme.cardBorderHover, 1.0f);
        for (int i = 0; i < count; i++) {
            int rowY = top + i * ListPopup.ROW_HEIGHT;
            boolean rowHovered = this.inlineHover == i;
            if (rowHovered) {
                Ui.roundRect(gfx, this.getX() + 4, rowY, this.getWidth() - 8, ListPopup.ROW_HEIGHT, 4.0f,
                        theme.cardHover);
            }
            Ui.text(gfx, this.optionLabels.get(i), this.getX() + 12,
                    rowY + (ListPopup.ROW_HEIGHT - Ui.font().lineHeight) / 2 + 1,
                    rowHovered ? theme.text : theme.textSecondary);
        }
    }

    /** Row under the mouse in the inline menu, or -1. */
    private int inlineRowAt(double mouseY) {
        int top = this.inlineMenuTop();
        int row = (int) Math.floor((mouseY - top) / ListPopup.ROW_HEIGHT);
        return row >= 0 && row < Math.min(this.optionLabels.size(), ListPopup.MAX_VISIBLE) ? row : -1;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
