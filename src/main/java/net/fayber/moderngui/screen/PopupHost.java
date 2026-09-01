package net.fayber.moderngui.screen;

import net.fayber.moderngui.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The top layer of a Modern GUI screen: it is added after every other widget, so its
 * extraction (and therefore drawing) happens last and everything it holds floats above the
 * content: dropdown menus, modal dialogs, toasts and rich tooltips.
 *
 * <p>It also fronts input for those layers through {@link #mouseClicked} / {@link #keyPressed}:
 * a {@link ModernGuiScreen} routes mouse and keyboard here first, so an open menu swallows clicks
 * outside it (standard dropdown dismissal) and an open modal blocks the widgets beneath entirely.
 *
 * <p>The host is created by {@link ModernGuiScreen}; widgets reach it through the screen or a
 * direct reference. It is not a control of its own: narration and focus are no-ops, and its
 * "hover" state is never set.
 */
public class PopupHost extends AbstractWidget {
    protected Theme theme = Theme.dark();

    /** Open menu popups, stacked (a submenu would push a second entry); index 0 = bottom. */
    private final List<ListPopup> menus = new ArrayList<>();
    /** Open modal layer, or null. At most one; opening a new one replaces it. */
    private ModalLayer modal;
    /** Toast queue, drawn top right; the host retires finished toasts. */
    private final List<Toast> toasts = new ArrayList<>();
    /** Rich tooltip registrations (widget -> data), checked while extracting. */
    private final List<RichTooltip> tooltips = new ArrayList<>();

    /** Widget the rich tooltip is currently attached to, and when hovering started. */
    private AbstractWidget tooltipWidget;
    private long tooltipSince = -1L;
    private static final long TOOLTIP_DELAY_MS = 500;

    public PopupHost(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    public PopupHost theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    /** Opens a menu popup (a dropdown list) above everything else. */
    public void openMenu(ListPopup menu) {
        this.menus.add(menu);
    }

    public boolean menusOpen() {
        return !this.menus.isEmpty();
    }

    /** Closes the topmost menu popup (the click-outside path uses the whole stack). */
    public void closeTopMenu() {
        if (!this.menus.isEmpty()) {
            this.menus.remove(this.menus.size() - 1);
        }
    }

    public void closeAllMenus() {
        this.menus.clear();
    }

    /** Opens (or replaces) the modal layer: a plain dialog or an interactive one. */
    public void showModal(ModalLayer modal) {
        this.modal = modal;
        this.closeAllMenus();
    }

    public boolean modalOpen() {
        return this.modal != null;
    }

    public void closeModal() {
        this.modal = null;
    }

    /** Queues a toast notification. */
    public void showToast(Toast toast) {
        toast.setScreenBounds(this.getWidth(), this.getHeight());
        this.toasts.add(toast);
    }

    /**
     * Registers a rich tooltip for a widget: while the widget is hovered the host draws a
     * title + body card next to it after {@link #TOOLTIP_DELAY_MS}. Call once per widget.
     */
    public void tooltip(AbstractWidget widget, Component title, Component body) {
        this.tooltips.add(new RichTooltip(widget, title, body));
    }

    /**
     * Never a click target of its own: the host is a screen-sized layer, and 26.1's dispatch
     * ({@code getChildAt} picks the first child that reports a hit) would otherwise route every
     * click on empty space here, playing the vanilla press sound and stealing screen focus.
     * Popups are fronted through {@link #handleClick} instead, which the screen calls first.
     */
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return false;
    }

    /**
     * Front-of-queue mouse handling: returns true when a popup consumed the click (the screen
     * then must not dispatch it to the widgets below). Called by ModernGuiScreen before super.
     */
    public boolean handleClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (this.modal != null) {
            return this.modal.handleClick(this, event, doubleClick);
        }
        if (!this.menus.isEmpty()) {
            ListPopup top = this.menus.get(this.menus.size() - 1);
            if (top.handleClick(event.x(), event.y(), event.button())) {
                if (top.isDone()) {
                    this.closeTopMenu();
                }
            } else {
                // Click outside the menu: dismiss it and swallow the click.
                this.closeAllMenus();
            }
            return true;
        }
        return false;
    }

    /** Front-of-queue drag handling; only a modal layer can be a drag target. */
    public boolean handleDrag(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        return this.modal != null && this.modal.handleDrag(this, event, deltaX, deltaY);
    }

    /** Front-of-queue release handling; a modal layer uses it to end its drags. */
    public boolean handleRelease(net.minecraft.client.input.MouseButtonEvent event) {
        return this.modal != null && this.modal.handleRelease(this, event);
    }

    /** Front-of-queue keyboard handling; returns true when a popup consumed the key. */
    public boolean handleKey(net.minecraft.client.input.KeyEvent event) {
        if (this.modal != null) {
            return this.modal.handleKey(this, event);
        }
        if (!this.menus.isEmpty()) {
            ListPopup top = this.menus.get(this.menus.size() - 1);
            if (top.handleKey(event)) {
                if (top.isDone()) {
                    this.closeTopMenu();
                }
                return true;
            }
            // Any other key with a menu open still counts as outside activity; keep the menu.
            return false;
        }
        return false;
    }

    /** True while a popup can scroll (menus); the screen routes wheel events here first. */
    public boolean handleScroll(double mouseX, double mouseY, double yDelta) {
        if (!this.menus.isEmpty()) {
            ListPopup top = this.menus.get(this.menus.size() - 1);
            top.scroll(yDelta);
            return true;
        }
        return false;
    }

    /**
     * Front-of-queue character handling; returns true when the modal layer consumed the typed
     * character. The screen routes charTyped here first so text fields inside a modal (the
     * colour picker's hex field) receive typing even though they are not screen children.
     */
    public boolean handleChar(net.minecraft.client.input.CharacterEvent event) {
        return this.modal != null && this.modal.handleChar(this, event);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        // Draw order inside the host: toasts under menus under the modal, so a modal always
        // owns the screen while it is open.
        Toast.trim(this.toasts);
        for (int i = 0; i < this.toasts.size(); i++) {
            this.toasts.get(i).setSlot(i);
            this.toasts.get(i).extract(gfx, this.theme, partialTick);
        }
        this.toasts.removeIf(Toast::isRetired);

        for (ListPopup menu : this.menus) {
            menu.extract(gfx, this.theme, mouseX, mouseY, partialTick);
        }

        if (this.modal != null) {
            this.modal.extract(gfx, this.theme, mouseX, mouseY, partialTick);
        }

        this.extractRichTooltip(gfx, mouseX, mouseY);
    }

    /** Tracks hover across the registered widgets and draws the tooltip once the delay passes. */
    private void extractRichTooltip(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        AbstractWidget hovered = null;
        for (RichTooltip tip : this.tooltips) {
            if (tip.widget().isHoveredOrFocused() && tip.widget().isMouseOver(mouseX, mouseY)) {
                hovered = tip.widget(); // last registered wins, which is the natural draw order
            }
        }
        long now = net.minecraft.util.Util.getMillis();
        if (hovered != this.tooltipWidget) {
            this.tooltipWidget = hovered;
            this.tooltipSince = hovered == null ? -1L : now;
            return;
        }
        if (hovered == null || this.tooltipSince < 0) {
            return;
        }
        if (now - this.tooltipSince < TOOLTIP_DELAY_MS) {
            return;
        }
        RichTooltip tip = this.findTip(hovered);
        if (tip != null) {
            tip.extract(gfx, this.theme, mouseX, mouseY);
        }
    }

    private RichTooltip findTip(AbstractWidget widget) {
        for (RichTooltip tip : this.tooltips) {
            if (tip.widget() == widget) {
                return tip;
            }
        }
        return null;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // The host is not a control; it owns no narration.
    }

    /** A registered rich tooltip: the widget and what to show while it is hovered. */
    private record RichTooltip(AbstractWidget widget, Component title, Component body) {
        void extract(GuiGraphicsExtractor gfx, Theme theme, int mouseX, int mouseY) {
            RichTooltipRenderer.draw(gfx, theme, this.title, this.body, mouseX, mouseY);
        }
    }
}
