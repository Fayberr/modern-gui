package net.fayber.faybergui.widget;

import net.fayber.faybergui.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * A {@link TextField} dressed as a search box: a magnifier glyph at the left edge, a clear
 * ("x") button that appears once there is something to clear, and a debounced search hook
 * firing 250 ms after the last keystroke (or immediately on Enter).
 *
 * <p>The debounce exists because a search callback usually rebuilds a list below the field;
 * firing per keystroke rebuilds it per keystroke and makes fast typing flicker. The timer is
 * checked from {@link #extractContents}, so the callback lands on a frame boundary and the
 * pending flag makes a quiet field still fire after typing stops. The clear button's hit area
 * is checked before the field's own click handling, so clicking the "x" never steals focus
 * or moves the caret.
 */
public class SearchField extends TextField {
    /** Debounce delay between the last edit and the search callback. */
    private static final long SEARCH_DELAY_MS = 250;
    /** Left padding: room for the magnifier plus breathing space. */
    private static final int LEFT_PAD = 24;
    /** Right padding: room for the clear button. */
    private static final int RIGHT_PAD = 18;
    private static final int ICON_SIZE = 14;
    private static final int CLEAR_SIZE = 10;
    /** Half the side of the clear button's 16x16 hit square. */
    private static final int CLEAR_HIT = 8;

    private Consumer<String> onSearch;
    private long lastEditMs;
    /** True when edits happened since the last search fired. */
    private boolean pendingSearch;

    public SearchField(int x, int y, int w, int h) {
        super(x, y, w, h);
        this.leftPad = LEFT_PAD;
        this.rightPad = RIGHT_PAD;
        this.hint("Search");
    }

    public SearchField theme(Theme theme) {
        super.theme(theme);
        return this;
    }

    /** Fires at most every {@link #SEARCH_DELAY_MS} after the last edit, and on Enter. */
    public SearchField onSearch(Consumer<String> onSearch) {
        this.onSearch = onSearch;
        return this;
    }

    public SearchField hint(String hint) {
        super.hint(hint);
        return this;
    }

    public SearchField radius(float radius) {
        super.radius(radius);
        return this;
    }

    @Override
    protected void onEdited(String value) {
        this.lastEditMs = Util.getMillis();
        this.pendingSearch = true;
        super.onEdited(value);
    }

    /** Fires the search callback with the current text. */
    private void fireSearch() {
        this.pendingSearch = false;
        if (this.onSearch != null) {
            this.onSearch.accept(this.getValue());
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.isActive() && this.isFocused()
                && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            this.fireSearch(); // Enter skips the debounce
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // The clear button is checked first so clicking the "x" neither focuses nor edits.
        if (this.isActive() && !this.getValue().isEmpty() && this.isOverClear(event.x(), event.y())) {
            this.value("");
            this.fireSearch();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean isOverClear(double mouseX, double mouseY) {
        double cx = this.getX() + this.getWidth() - RIGHT_PAD / 2.0 - 1;
        double cy = this.getY() + this.getHeight() / 2.0;
        return Math.abs(mouseX - cx) <= CLEAR_HIT && Math.abs(mouseY - cy) <= CLEAR_HIT;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        // Fire the debounced search on a frame boundary once the quiet period elapsed.
        if (this.pendingSearch && Util.getMillis() - this.lastEditMs >= SEARCH_DELAY_MS) {
            this.fireSearch();
        }
        super.extractWidgetRenderState(gfx, mouseX, mouseY, partialTick);

        float cy = this.getY() + this.getHeight() / 2.0f;
        // The magnifier always sits at the left edge; it dims while the field is empty and
        // unfocused (the hint carries the invitation then).
        boolean emptyIdle = this.getValue().isEmpty() && !this.isFocused();
        Icons.SEARCH.draw(gfx, this.getX() + LEFT_PAD / 2.0f, cy, ICON_SIZE,
                emptyIdle ? this.theme.textMuted : this.theme.textSecondary);

        if (!this.getValue().isEmpty()) {
            Icons.X.draw(gfx, this.getX() + this.getWidth() - RIGHT_PAD / 2.0f - 1, cy,
                    CLEAR_SIZE, this.theme.textMuted);
        }
    }
}
