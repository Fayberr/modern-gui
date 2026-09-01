package net.fayber.faybergui.screen;

import net.fayber.faybergui.render.Theme;
import net.fayber.faybergui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Base screen for mods building UIs out of the Fayber GUI toolkit. It wires the
 * {@link PopupHost}: dropdown menus, modals, toasts and rich tooltips all layer above the
 * widgets the subclass adds, and popups block the input underneath them.
 *
 * <p>Subclasses implement {@link #initScreen()} and add their widgets there (plain
 * {@code addRenderableWidget}); the host is appended after, which is what puts it on top.
 * Extras on top of vanilla Screen behaviour:
 *
 * <ul>
 *   <li>{@link #tooltip(widget, title, body)} registers a rich tooltip for a widget.</li>
 *   <li>{@link #showToast} / {@link #showModal} / {@link #openMenu} drive the popups.</li>
 *   <li>{@link #popupHost()} for widgets that open menus themselves (dropdowns).</li>
 *   <li>ESC still closes the screen when no popup is open; with a modal open it cancels it.</li>
 * </ul>
 */
public abstract class FayberGuiScreen extends Screen {
    /** Room to each side of the content column, for reference layouts. */
    public static final int CONTENT_WIDTH = 380;

    protected Theme theme = Theme.dark();
    private PopupHost popupHost;

    protected FayberGuiScreen(Component title) {
        // Kept raw: the Inter variant depends on the GUI scale, so it is applied at draw time.
        super(title);
    }

    /** The theme this screen's widgets are drawn with. Set before {@link #initScreen()} runs. */
    public FayberGuiScreen theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    public Theme theme() {
        return this.theme;
    }

    /** The popup host; null only before init. Dropdowns and friends take it as their anchor. */
    public @Nullable PopupHost popupHost() {
        return this.popupHost;
    }

    /**
     * The screen content. Runs after the popup host exists (so widgets can capture it) but
     * before it is added to the screen, which is what makes the host draw on top.
     */
    protected abstract void initScreen();

    @Override
    protected void init() {
        // The host is created first so initScreen() can hand it to widgets while they build
        // (dropdowns capture screen.popupHost() at construction time; it used to be null there,
        // which silently pushed every dropdown into the inline fallback). It is still added
        // last, below, which is what puts its popups on top during extraction.
        this.popupHost = new PopupHost(0, 0, this.width, this.height);
        this.popupHost.theme(this.theme);
        this.initScreen();
        this.addRenderableWidget(this.popupHost);
    }

    // ------------------------------------------------------------------ popup API

    /** Opens a dropdown menu popup anchored to the given rectangle. */
    public void openMenu(ListPopup menu) {
        if (this.popupHost != null) {
            this.popupHost.openMenu(menu);
        }
    }

    /** Opens (or replaces) a modal dialog. */
    public void showModal(Modal modal) {
        if (this.popupHost != null) {
            this.popupHost.showModal(modal);
        }
    }

    /** Queues a toast notification. */
    public void showToast(Toast toast) {
        if (this.popupHost != null) {
            this.popupHost.showToast(toast);
        }
    }

    /** Convenience: registers a rich tooltip (title + body, hover delay) for a widget. */
    public void tooltip(AbstractWidget widget, String title, String body) {
        this.tooltip(widget, Component.literal(title), Component.literal(body));
    }

    public void tooltip(AbstractWidget widget, Component title, Component body) {
        if (this.popupHost != null) {
            this.popupHost.tooltip(widget, Ui.uiBold(title), Ui.ui(body));
        }
    }

    // ------------------------------------------------------------------ input routing

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        // Popups go first: an open menu or modal swallows clicks that would fall through.
        if (this.popupHost != null && this.popupHost.handleClick(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (this.popupHost != null && this.popupHost.handleKey(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double xDelta, double yDelta) {
        if (this.popupHost != null && this.popupHost.handleScroll(mouseX, mouseY, yDelta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, xDelta, yDelta);
    }

    @Override
    public void onClose() {
        // Clean up any open popups before leaving (the host dies with the screen anyway).
        if (this.popupHost != null) {
            this.popupHost.closeAllMenus();
            this.popupHost.closeModal();
        }
        super.onClose();
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Extracts the dim backdrop over the vanilla background, then the title, then the widgets
     * (which the host tops up). Override {@link #drawTitle} or call-and-extend to customise.
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        // Backdrop and title must be extracted BEFORE super (which extracts the widgets),
        // because within one stratum extraction order is draw order.
        gfx.fill(0, 0, this.width, this.height, this.theme.scrim);
        this.drawTitle(gfx);
        super.extractRenderState(gfx, mouseX, mouseY, partialTick);
    }

    /** Draws the screen title; default is a bold Inter label at the top left of the content. */
    protected void drawTitle(GuiGraphicsExtractor gfx) {
        Ui.text(gfx, Ui.uiBold(this.title), this.contentX(), 18, this.theme.text);
    }

    /** Left edge of the centred content column ({@link #CONTENT_WIDTH} clamped to the window). */
    protected int contentX() {
        int w = Math.min(CONTENT_WIDTH, Math.max(220, this.width - 32));
        return (this.width - w) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        // Fayber screens feel like overlays; do not pause singleplayer.
        return false;
    }
}
