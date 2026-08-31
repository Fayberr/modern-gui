package net.fayber.faybergui.widget;

import net.fayber.faybergui.render.Theme;
import net.fayber.faybergui.render.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Minimal multi-line text editor: a card holding a list of lines with a blinking caret, vertical
 * scrolling and an optional 1-based line-number gutter. Built from {@link Ui} primitives instead
 * of wrapping vanilla's {@code EditBox}, which is single-line only.
 *
 * <p>Supported editing: typing printable characters, Backspace/Delete, Enter (line break),
 * Home/End, arrow keys that walk the caret across line boundaries, and the clipboard shortcuts
 * (paste, copy, cut via {@link net.minecraft.client.KeyboardHandler}). Escape releases focus and
 * returns false so the screen still closes. There is no selection model: copy takes the current
 * line and cut copies then clears it, which covers the "paste it somewhere" use case.
 *
 * <p>Overflow scrolls by whole lines (an offset tracked against the caret, plus mouse wheel).
 * Lines wider than the field are clipped on the right with no horizontal scrolling yet. Drawing
 * is clipped by only emitting lines that fit the widget rect; a scissor would also clip the caret
 * near the edges, which this MVP avoids on purpose.
 *
 * <p>Focus works like {@link TextField}: a click inside takes focus (and places the caret from
 * the click position by measuring prefix widths), a click outside drops it.
 */
public class TextArea extends AbstractWidget {
    /** Default total character cap across all lines. */
    public static final int DEFAULT_MAX_LENGTH = 1000;

    private static final int PAD = 8;
    private static final int GUTTER_GAP = 8;
    /** Half the caret blink period in ms. */
    private static final long BLINK_MS = 500;

    protected final List<String> lines = new ArrayList<>();

    protected Theme theme = Theme.dark();
    protected float radius = 5.0f;
    private Consumer<String> onChanged;
    private boolean lineNumbers = false;
    private int maxLength = DEFAULT_MAX_LENGTH;

    private int caretLine;
    private int caretCol;
    private int scrollLine;
    private boolean focused;
    private String text = "";

    public TextArea(int x, int y, int w, int h) {
        super(x, y, w, h, Component.empty());
        this.lines.add("");
    }

    // ------------------------------------------------------------- fluent config

    public TextArea theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    public TextArea radius(float radius) {
        this.radius = radius;
        return this;
    }

    /** Replaces the content programmatically (does not fire {@link #onChanged}). */
    public TextArea value(String value) {
        this.lines.clear();
        String[] parts = value.split("\n", -1);
        for (String part : parts) {
            this.lines.add(part);
        }
        if (this.lines.isEmpty()) {
            this.lines.add("");
        }
        this.caretLine = 0;
        this.caretCol = 0;
        this.scrollLine = 0;
        this.refreshText();
        return this;
    }

    /** Runs on every edit with the full text joined by newlines. */
    public TextArea onChanged(Consumer<String> onChanged) {
        this.onChanged = onChanged;
        return this;
    }

    /** Draws a muted gutter with 1-based line numbers left of the text. */
    public TextArea lineNumbers(boolean lineNumbers) {
        this.lineNumbers = lineNumbers;
        return this;
    }

    /** Total character cap across all lines (typing and paste are truncated at it). */
    public TextArea maxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    // ------------------------------------------------------------------- reading

    /** The full content, lines joined by newlines. */
    public String getText() {
        return this.text;
    }

    private void refreshText() {
        this.text = String.join("\n", this.lines);
        if (this.onChanged != null) {
            this.onChanged.accept(this.text);
        }
    }

    private int totalChars() {
        int total = this.lines.size() - 1; // the newlines
        for (String line : this.lines) {
            total += line.length();
        }
        return total;
    }

    // ------------------------------------------------------------------- focus

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public boolean isFocused() {
        return this.focused;
    }

    // No children() override: in 26.1 children() lives on ContainerEventHandler, which
    // AbstractWidget does not implement. There is no child widget anyway.

    // ----------------------------------------------------------------- helpers

    private int lineHeight() {
        return Ui.font().lineHeight;
    }

    /** Text line capacity of the widget rect. */
    private int visibleLines() {
        return Math.max(1, (this.getHeight() - 2 * PAD) / this.lineHeight());
    }

    /** Width of the line-number gutter, including its gap to the text. */
    private int gutterWidth() {
        if (!this.lineNumbers) {
            return 0;
        }
        String widest = String.valueOf(this.lines.size());
        return Ui.font().width(widest) + GUTTER_GAP;
    }

    private int textLeft() {
        return this.getX() + PAD + this.gutterWidth();
    }

    private int firstLineY() {
        return this.getY() + PAD;
    }

    /** Keeps the caret inside the scrolled viewport. */
    private void scrollToCaret() {
        int visible = this.visibleLines();
        if (this.caretLine < this.scrollLine) {
            this.scrollLine = this.caretLine;
        } else if (this.caretLine >= this.scrollLine + visible) {
            this.scrollLine = this.caretLine - visible + 1;
        }
    }

    /** Clamps the caret onto the lines that exist after an edit. */
    private void clampCaret() {
        this.caretLine = Math.clamp(this.caretLine, 0, this.lines.size() - 1);
        this.caretCol = Math.clamp(this.caretCol, 0, this.lines.get(this.caretLine).length());
    }

    // ----------------------------------------------------------------- editing

    /** Inserts text at the caret, turning newlines into line breaks, respecting the char cap. */
    private void insert(String inserted) {
        inserted = inserted.replace("\r", "");
        int room = this.maxLength - this.totalChars();
        if (room <= 0) {
            return;
        }
        if (inserted.length() > room) {
            inserted = inserted.substring(0, room);
        }
        String[] parts = inserted.split("\n", -1);
        String line = this.lines.get(this.caretLine);
        String head = line.substring(0, this.caretCol);
        String tail = line.substring(this.caretCol);
        if (parts.length == 1) {
            this.lines.set(this.caretLine, head + parts[0] + tail);
            this.caretCol += parts[0].length();
        } else {
            this.lines.set(this.caretLine, head + parts[0]);
            for (int i = 1; i < parts.length - 1; i++) {
                this.lines.add(this.caretLine + i, parts[i]);
            }
            this.lines.add(this.caretLine + parts.length - 1,
                    parts[parts.length - 1] + tail);
            this.caretLine += parts.length - 1;
            this.caretCol = parts[parts.length - 1].length();
        }
        this.scrollToCaret();
        this.refreshText();
    }

    private void backspace() {
        if (this.caretCol > 0) {
            String line = this.lines.get(this.caretLine);
            this.lines.set(this.caretLine, line.substring(0, this.caretCol - 1) + line.substring(this.caretCol));
            this.caretCol--;
        } else if (this.caretLine > 0) {
            // Join with the previous line.
            String previous = this.lines.get(this.caretLine - 1);
            this.lines.remove(this.caretLine);
            this.caretLine--;
            this.lines.set(this.caretLine, previous + this.lines.get(this.caretLine));
            this.caretCol = previous.length();
        }
        this.scrollToCaret();
        this.refreshText();
    }

    private void delete() {
        String line = this.lines.get(this.caretLine);
        if (this.caretCol < line.length()) {
            this.lines.set(this.caretLine, line.substring(0, this.caretCol) + line.substring(this.caretCol + 1));
        } else if (this.caretLine < this.lines.size() - 1) {
            // Join with the next line.
            String next = this.lines.get(this.caretLine + 1);
            this.lines.remove(this.caretLine + 1);
            this.lines.set(this.caretLine, line + next);
        }
        this.clampCaret();
        this.refreshText();
    }

    private void breakLine() {
        if (this.totalChars() >= this.maxLength) {
            return;
        }
        String line = this.lines.get(this.caretLine);
        this.lines.set(this.caretLine, line.substring(0, this.caretCol));
        this.lines.add(this.caretLine + 1, line.substring(this.caretCol));
        this.caretLine++;
        this.caretCol = 0;
        this.scrollToCaret();
        this.refreshText();
    }

    // ------------------------------------------------------------------- input

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!this.focused || !this.isActive()) {
            return false;
        }
        int cp = event.codepoint();
        // Control characters arrive as key events; only printable codepoints belong here.
        if (cp < 32 || cp == 127) {
            return false;
        }
        this.insert(new String(Character.toChars(cp)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.focused || !this.isActive()) {
            return false;
        }
        if (event.isEscape()) {
            this.focused = false;
            // Let the screen see it so a still-usable escape closes the screen.
            return false;
        }
        if (event.isLeft()) {
            if (this.caretCol > 0) {
                this.caretCol--;
            } else if (this.caretLine > 0) {
                this.caretLine--;
                this.caretCol = this.lines.get(this.caretLine).length();
            }
            this.scrollToCaret();
            return true;
        }
        if (event.isRight()) {
            if (this.caretCol < this.lines.get(this.caretLine).length()) {
                this.caretCol++;
            } else if (this.caretLine < this.lines.size() - 1) {
                this.caretLine++;
                this.caretCol = 0;
            }
            this.scrollToCaret();
            return true;
        }
        if (event.isUp()) {
            this.caretLine = Math.max(0, this.caretLine - 1);
            this.clampCaret();
            this.scrollToCaret();
            return true;
        }
        if (event.isDown()) {
            this.caretLine = Math.min(this.lines.size() - 1, this.caretLine + 1);
            this.clampCaret();
            this.scrollToCaret();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            this.backspace();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DELETE) {
            this.delete();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            this.breakLine();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_HOME) {
            this.caretCol = 0;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_END) {
            this.caretCol = this.lines.get(this.caretLine).length();
            return true;
        }
        if (event.isPaste()) {
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clipboard != null && !clipboard.isEmpty()) {
                this.insert(clipboard);
            }
            return true;
        }
        if (event.isCopy()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(this.lines.get(this.caretLine));
            return true;
        }
        if (event.isCut()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(this.lines.get(this.caretLine));
            this.lines.set(this.caretLine, "");
            this.clampCaret();
            this.refreshText();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double xAmount, double yAmount) {
        if (!this.isMouseOver(mouseX, mouseY) || !this.isActive()) {
            return super.mouseScrolled(mouseX, mouseY, xAmount, yAmount);
        }
        int maxScroll = Math.max(0, this.lines.size() - this.visibleLines());
        this.scrollLine = Math.clamp(this.scrollLine - (int) Math.signum(yAmount), 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.isActive()) {
            return super.mouseClicked(event, doubleClick);
        }
        if (!this.isMouseOver(event.x(), event.y())) {
            this.focused = false;
            return super.mouseClicked(event, doubleClick);
        }
        this.focused = true;
        this.placeCaretFromClick(event.x(), event.y());
        return true;
    }

    /** Picks the clicked line, then the column by measuring prefix widths. */
    private void placeCaretFromClick(double mouseX, double mouseY) {
        int relative = (int) (mouseY - this.firstLineY());
        int line = this.scrollLine + Math.floorDiv(relative, this.lineHeight());
        this.caretLine = Math.clamp(line, this.scrollLine,
                Math.min(this.lines.size() - 1, this.scrollLine + this.visibleLines() - 1));
        String target = this.lines.get(this.caretLine);
        int x = (int) (mouseX - this.textLeft());
        int col = 0;
        while (col < target.length()
                && Ui.font().width(Component.literal(target.substring(0, col + 1)).setStyle(Ui.ui("").getStyle())) <= x) {
            col++;
        }
        this.caretCol = col;
    }

    // ---------------------------------------------------------------- rendering

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        int border = this.focused ? this.theme.cardBorderHover : this.theme.cardBorder;
        Ui.roundRectBorder(gfx, this.getX(), this.getY(), this.getWidth(), this.getHeight(),
                this.radius, this.theme.card, border, 1.0f);

        int lineHeight = this.lineHeight();
        int textLeft = this.textLeft();
        int firstLineY = this.firstLineY();
        int lastLineBottom = this.getY() + this.getHeight() - PAD;
        int maxCol = this.getWidth() - PAD - textLeft;

        for (int row = 0; row < this.visibleLines(); row++) {
            int index = this.scrollLine + row;
            if (index >= this.lines.size()) {
                break;
            }
            int y = firstLineY + row * lineHeight;
            if (y + lineHeight > lastLineBottom) {
                break;
            }
            if (this.lineNumbers) {
                String number = String.valueOf(index + 1);
                Ui.text(gfx, Ui.ui(number), this.getX() + PAD, y, this.theme.textMuted);
            }
            String line = this.lines.get(index);
            if (!line.isEmpty()) {
                // Hard-clip wide lines to the card; no horizontal scroll in this MVP.
                Ui.text(gfx, Ui.ellipsize(Ui.ui(line), maxCol), textLeft, y, this.theme.text);
            }
            if (this.focused && index == this.caretLine
                    && Util.getMillis() / BLINK_MS % 2 == 0) {
                int caretX = textLeft + (this.caretCol > 0
                        ? Ui.font().width(Component.literal(line.substring(0, this.caretCol))
                                .setStyle(Ui.ui("").getStyle()))
                        : 0);
                Ui.rect(gfx, caretX, y, 1.0f, lineHeight, this.theme.text);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Ui.ui("Text area"));
    }
}
