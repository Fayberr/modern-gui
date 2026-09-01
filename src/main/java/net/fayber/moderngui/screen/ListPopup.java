package net.fayber.moderngui.screen;

import net.fayber.moderngui.render.Theme;
import net.fayber.moderngui.render.Ui;
import net.fayber.moderngui.widget.Icons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * A dropdown menu popup: a floating rounded panel of option rows above everything else on the
 * screen. Opened through the {@link PopupHost} (which dismisses it on outside clicks) by
 * {@link net.fayber.moderngui.widget.Dropdown}, or directly for custom menus.
 *
 * <p>Rendering: dark card, one row per option, the selected row carries a check mark, the hovered
 * row brightens one step. The panel is clamped to the window and flips above its anchor when
 * there is no room below. Clicking an option runs its callback and closes the popup.
 */
public class ListPopup {
    /** Row height inside the menu. */
    public static final int ROW_HEIGHT = 22;
    /** Padding around the option list. */
    private static final int PADDING = 6;
    private static final float RADIUS = 6.0f;
    /** Most rows shown before the list scrolls. */
    public static final int MAX_VISIBLE = 10;

    /** One option: display text and what happens when it is picked. */
    public record Option(Component label, boolean selected, Runnable onSelect) {
        public Option(String label, boolean selected, Runnable onSelect) {
            this(Component.literal(label), selected, onSelect);
        }
    }

    private final int anchorX;
    private final int anchorY;
    private final int anchorH;
    private final int width;
    private final List<Option> options;

    /** Set once the host has positioned the panel (depends on window bounds). */
    private int x;
    private int y;
    private int height;
    private boolean positioned;
    /** First visible row when the list scrolls. */
    private int scrollRow;
    /** True once an option was picked (or the menu is otherwise finished). */
    private boolean done;

    public ListPopup(int anchorX, int anchorY, int anchorH, int width, List<Option> options) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorH = anchorH;
        this.width = width;
        this.options = List.copyOf(options);
    }

    private void position(Theme theme) {
        if (this.positioned) {
            return;
        }
        this.positioned = true;
        int visible = Math.min(this.options.size(), MAX_VISIBLE);
        this.height = visible * ROW_HEIGHT + PADDING * 2;
        this.x = Math.clamp(this.anchorX, 2, mc().getWindow().getGuiScaledWidth() - this.width - 2);
        int below = this.anchorY + this.anchorH + 2;
        int screenH = mc().getWindow().getGuiScaledHeight();
        if (below + this.height > screenH - 2 && this.anchorY - 2 - this.height >= 0) {
            // Flip above when there is no room below.
            this.y = this.anchorY - 2 - this.height;
        } else {
            this.y = below;
        }
    }

    private net.minecraft.client.Minecraft mc() {
        return net.minecraft.client.Minecraft.getInstance();
    }

    /** @return true when the click was inside the panel (and handled). */
    public boolean handleClick(double mouseX, double mouseY, int button) {
        if (mouseX < this.x || mouseX >= this.x + this.width || mouseY < this.y || mouseY >= this.y + this.height) {
            return false;
        }
        int index = this.rowAt(mouseY);
        if (index >= 0) {
            Option option = this.options.get(index + this.scrollRow);
            this.done = true;
            option.onSelect().run();
        }
        return true;
    }

    /** Row index under the mouse inside the panel, or -1 (padding or outside). */
    private int rowAt(double mouseY) {
        int rel = (int) Math.floor(mouseY - this.y) - PADDING;
        int index = rel / ROW_HEIGHT;
        int visible = Math.min(this.options.size() - this.scrollRow, MAX_VISIBLE);
        if (index < 0 || index >= visible) {
            return -1;
        }
        return index;
    }

    /** ESC closes the menu; up/down move a scroll window when the list overflows. */
    public boolean handleKey(net.minecraft.client.input.KeyEvent event) {
        if (event.isEscape()) {
            this.done = true;
            return true;
        }
        return false;
    }

    public void scroll(double yDelta) {
        int maxRow = Math.max(0, this.options.size() - MAX_VISIBLE);
        this.scrollRow = Math.clamp(this.scrollRow + (int) Math.round(-yDelta), 0, maxRow);
    }

    public boolean isDone() {
        return this.done;
    }

    public void extract(GuiGraphicsExtractor gfx, Theme theme, int mouseX, int mouseY, float partialTick) {
        this.position(theme);
        net.fayber.moderngui.render.Ui.shadow(gfx, this.x, this.y, this.width, this.height, RADIUS, 6.0f, 4);
        net.fayber.moderngui.render.Ui.roundRectBorder(gfx, this.x, this.y, this.width, this.height, RADIUS,
                theme.card, theme.cardBorderHover, 1.0f);

        int rowX = this.x + PADDING;
        int rowW = this.width - PADDING * 2;
        int visible = Math.min(this.options.size() - this.scrollRow, MAX_VISIBLE);
        for (int i = 0; i < visible; i++) {
            Option option = this.options.get(this.scrollRow + i);
            int rowY = this.y + PADDING + i * ROW_HEIGHT;
            boolean hovered = mouseX >= rowX && mouseX < rowX + rowW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hovered) {
                net.fayber.moderngui.render.Ui.roundRect(gfx, rowX, rowY, rowW, ROW_HEIGHT, 4.0f, theme.cardHover);
            }
            int textY = rowY + (ROW_HEIGHT - Ui.font().lineHeight) / 2 + 1;
            int labelX = rowX + 8;
            if (option.selected()) {
                Icons.CHECK.draw(gfx, rowX + 14, rowY + ROW_HEIGHT / 2.0f, 10, theme.text);
                labelX = rowX + 24;
            }
            Ui.text(gfx, option.label(), labelX, textY, hovered ? theme.text : theme.textSecondary);
        }
    }
}
