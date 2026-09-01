package net.fayber.moderngui.widget;

import net.fayber.moderngui.render.Theme;
import net.fayber.moderngui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A list editor with one text field per item plus a remove button, and an add row at the bottom:
 * the structured alternative to a {@link TextArea}'s one-item-per-line blob. The editor is
 * string-based; the owner parses. Fires {@code onChanged} with the full raw line list on every
 * edit, add and remove, so the owner can apply all-or-nothing semantics (skip the write while
 * any line fails to parse); a per-line {@link #lineValidator} marks offending lines red without
 * blocking typing, matching {@link TextField}'s validation philosophy.
 *
 * <p>Height grows with the row count: report it through {@link #getHeight()} (and position the
 * following content from it) rather than fixing it at construction.
 *
 * <p>Not a {@link net.minecraft.client.gui.components.events.ContainerEventHandler}: rows are
 * forwarded by hand from the input methods below (the {@code children()} trap from the widget
 * notes), with focus mirrored onto the fields like {@link TextField} does for its EditBox.
 */
public class ListEditor extends AbstractWidget {
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 4;
    private static final int PAD = 6;
    private static final int REMOVE_SIZE = 14;
    private static final int REMOVE_GAP = 6;
    private static final int ADD_HEIGHT = 18;
    private static final int ADD_GAP = 6;
    private static final int MAX_LENGTH = 4000;

    private final List<TextField> fields = new ArrayList<>();
    private final List<IconButton> removes = new ArrayList<>();
    private final FlatButton addRow;
    private Theme theme = Theme.dark();
    private Predicate<String> lineValidator = v -> true;
    @Nullable
    private Consumer<List<String>> onChanged;
    private Component addLabel = Component.literal("+ Add");
    private Component removeLabel = Component.literal("Remove");
    /** Placeholder drawn in an empty row (the per-type hint, e.g. "80" for an int list). */
    @Nullable
    private String fieldHint;

    /** @param initial the starting items, one per row */
    public ListEditor(int x, int y, int w, List<String> initial) {
        super(x, y, w, PAD + ADD_HEIGHT + PAD, Component.empty());
        for (String line : initial) {
            this.makeField(line);
        }
        this.addRow = new FlatButton(0, 0, 100, ADD_HEIGHT, this.addLabel, this::addRow, FlatButton.Style.GHOST)
                .theme(this.theme);
        this.setHeight(this.computeHeight());
    }

    private void makeField(String initial) {
        // Order matters: the validator must be attached before value() so lastValid starts at
        // the initial text, and onChanged last so the programmatic value() does not fire it.
        TextField field = new TextField(0, 0, 10, ROW_HEIGHT).theme(this.theme).maxLength(MAX_LENGTH);
        field.validator(this.lineValidator);
        if (this.fieldHint != null) {
            field.hint(this.fieldHint);
        }
        field.value(initial);
        field.onChanged(v -> this.fireChanged());
        this.fields.add(field);
        this.removes.add(new IconButton(0, 0, REMOVE_SIZE, Icons.X, () -> {
        }).theme(this.theme).tooltip(this.removeLabel));
    }

    public ListEditor theme(Theme theme) {
        this.theme = theme;
        for (TextField field : this.fields) {
            field.theme(theme);
        }
        for (IconButton remove : this.removes) {
            remove.theme(theme);
        }
        this.addRow.theme(theme);
        return this;
    }

    /** Per-line validation: invalid lines render in the error colour, typing is never blocked. */
    public ListEditor lineValidator(Predicate<String> lineValidator) {
        this.lineValidator = lineValidator;
        for (TextField field : this.fields) {
            field.validator(lineValidator);
        }
        return this;
    }

    /** Runs on every edit, add and remove with the full raw line list. */
    public ListEditor onChanged(Consumer<List<String>> onChanged) {
        this.onChanged = onChanged;
        return this;
    }

    /** Overrides the add-row label. */
    public ListEditor addLabel(String addLabel) {
        this.addLabel = Ui.ui(Component.literal(addLabel));
        this.addRow.setMessage(this.addLabel);
        return this;
    }

    /** Overrides the remove button tooltip. */
    public ListEditor removeLabel(String removeLabel) {
        this.removeLabel = Component.literal(removeLabel);
        for (IconButton remove : this.removes) {
            remove.tooltip(this.removeLabel);
        }
        return this;
    }

    /**
     * Placeholder shown in an empty row, focused or not: the per-type hint that makes list
     * flavours distinguishable at a glance ("80" for an int list, "0.5" for a float list).
     * Null clears it.
     */
    public ListEditor fieldHint(@Nullable String fieldHint) {
        this.fieldHint = fieldHint;
        for (TextField field : this.fields) {
            field.hint(fieldHint == null ? "" : fieldHint);
        }
        return this;
    }

    /** The raw lines currently in the editor, blanks included. */
    public List<String> value() {
        List<String> lines = new ArrayList<>(this.fields.size());
        for (TextField field : this.fields) {
            lines.add(field.getValue());
        }
        return lines;
    }

    /** Replaces the rows programmatically (the reset path); fires {@code onChanged} once. */
    public ListEditor value(List<String> lines) {
        this.fields.clear();
        this.removes.clear();
        for (String line : lines) {
            this.makeField(line);
        }
        this.setHeight(this.computeHeight());
        this.fireChanged();
        return this;
    }

    private void fireChanged() {
        if (this.onChanged != null) {
            this.onChanged.accept(this.value());
        }
    }

    private void addRow() {
        this.makeField("");
        this.setHeight(this.computeHeight());
        if (!this.fields.isEmpty()) {
            this.fields.get(this.fields.size() - 1).setFocused(true);
        }
        this.fireChanged();
    }

    private void removeRow(int index) {
        this.fields.remove(index);
        this.removes.remove(index);
        this.setHeight(this.computeHeight());
        this.fireChanged();
    }

    private int computeHeight() {
        int rows = this.fields.size();
        int rowsHeight = rows == 0 ? 0 : rows * ROW_HEIGHT + (rows - 1) * ROW_GAP;
        return PAD + rowsHeight + (rows == 0 ? 0 : ADD_GAP) + ADD_HEIGHT + PAD;
    }

    private int fieldWidth() {
        return Math.max(1, this.getWidth() - 2 * PAD - REMOVE_SIZE - REMOVE_GAP);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            for (TextField field : this.fields) {
                field.setFocused(false);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.isActive()) {
            return super.mouseClicked(event, doubleClick);
        }
        for (int i = 0; i < this.fields.size(); i++) {
            if (this.removes.get(i).mouseClicked(event, doubleClick)) {
                this.removeRow(i);
                return true;
            }
            if (this.fields.get(i).mouseClicked(event, doubleClick)) {
                return true;
            }
        }
        if (this.addRow.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (this.isMouseOver(event.x(), event.y())) {
            // Empty space inside the editor: drop the field focus, keep the click.
            this.setFocused(false);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        for (int i = 0; i < this.fields.size(); i++) {
            if (this.fields.get(i).mouseReleased(event) || this.removes.get(i).mouseReleased(event)) {
                return true;
            }
        }
        if (this.addRow.mouseReleased(event)) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.isActive()) {
            for (TextField field : this.fields) {
                if (field.keyPressed(event)) {
                    // Enter in a field appends a fresh row and puts the caret there, the
                    // one-item-per-line muscle memory from the TextArea editors.
                    if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                        this.addRow();
                    }
                    return true;
                }
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.isActive()) {
            for (TextField field : this.fields) {
                if (field.charTyped(event)) {
                    return true;
                }
            }
        }
        return super.charTyped(event);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        this.setHeight(this.computeHeight());
        int fieldW = this.fieldWidth();
        int y = this.getY() + PAD;
        for (int i = 0; i < this.fields.size(); i++) {
            TextField field = this.fields.get(i);
            field.setPosition(this.getX() + PAD, y);
            field.setWidth(fieldW);
            field.active = this.isActive();
            IconButton remove = this.removes.get(i);
            remove.setPosition(this.getX() + PAD + fieldW + REMOVE_GAP, y + (ROW_HEIGHT - REMOVE_SIZE) / 2);
            remove.active = this.isActive();
            remove.extractRenderState(gfx, mouseX, mouseY, partialTick);
            field.extractRenderState(gfx, mouseX, mouseY, partialTick);
            y += ROW_HEIGHT + ROW_GAP;
        }
        this.addRow.setPosition(this.getX() + PAD, y - ROW_GAP + ADD_GAP);
        this.addRow.setWidth(this.getWidth() - 2 * PAD);
        this.addRow.active = this.isActive();
        this.addRow.extractRenderState(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Ui.ui(Component.literal("List editor")));
    }
}
