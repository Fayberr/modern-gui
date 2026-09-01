package net.fayber.moderngui.widget;

import net.fayber.moderngui.render.Theme;
import net.fayber.moderngui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Styled single-line text field wrapping a vanilla {@link EditBox}.
 *
 * <p>The EditBox stays in charge of everything hard about text input: the caret and its blink,
 * selection highlighting, word-wise cursor movement, the clipboard shortcuts and IME handling.
 * This widget only replaces its look and its focus plumbing:
 *
 * <ul>
 *   <li>Look: the box runs borderless with its drop shadow off, so its text lands at
 *       exactly {@code (x, y)}; this widget draws the card underneath (theme card fill plus a
 *       hairline border that highlights while focused) and the hint text when the field is empty
 *       and unfocused. A {@link EditBox#addFormatter formatter} re-styles every line into the
 *       Inter UI font, which makes the typed text ride {@link Ui}'s font instead of the vanilla
 *       bitmap one.
 *   <li>Focus: the widget owns the focus state and mirrors it onto the inner box: a click
 *       inside takes focus (and forwards the click so the caret lands under it), a click outside
 *       drops it. {@link #setFocused}/{@link #isFocused} delegate, so screens that dispatch keys
 *       to the focused widget reach the EditBox through the forwarding handlers below. The inner
 *       box is also exposed via {@link #children()} so container-style dispatch works too.
 *   <li>Validation: a {@link Predicate} marks the current text as invalid (rendered in the
 *       error colour) while typing, but never blocks input, so users can pass through invalid
 *       intermediate states (a half-typed URL, a negative number mid-edit). {@link #getValue()}
 *       returns the raw text; {@link #getValidValue()} the last value that passed validation.
 * </ul>
 *
 * <p>Subclasses ({@link NumberField}, {@link SearchField}) tune the left/right padding to make
 * room for icons and override the key handling.
 */
public class TextField extends AbstractWidget {
    /** Colour used for text that fails the {@link #validator}. */
    public static final int ERROR_COLOR = 0xFFF28B82;

    /** Default corner radius. */
    protected static final float RADIUS = 5.0f;
    /** Default character cap. */
    protected static final int DEFAULT_MAX_LENGTH = 128;

    /** The wrapped vanilla box: caret, selection, clipboard, IME. */
    protected final EditBox box;

    protected Theme theme = Theme.dark();
    protected float radius = RADIUS;
    /** Draws the rounded card behind the text; false leaves the background to the caller. */
    protected boolean bordered = true;
    protected String hint = "";
    /** Marks the current text invalid (error colour) without blocking typing. */
    protected Predicate<String> validator = v -> true;
    protected Consumer<String> onChanged;
    /** Last text that passed the validator; starts as the initial value. */
    protected String lastValid = "";
    /** Optional text colour override; -1 falls back to the theme (or the error colour). */
    protected int textOverride = -1;
    /** Horizontal text padding, split into a left and a right half; icons increase these. */
    protected int leftPad = 8;
    protected int rightPad = 8;

    public TextField(int x, int y, int w, int h) {
        super(x, y, w, h, Component.empty());
        int lineHeight = Ui.font().lineHeight;
        this.box = new EditBox(Ui.font(), x + this.leftPad, y + (h - lineHeight) / 2 + 1,
                w - this.leftPad - this.rightPad, lineHeight, Component.empty());
        this.box.setBordered(false);
        this.box.setTextShadow(false);
        this.box.setMaxLength(DEFAULT_MAX_LENGTH);
        // House trick: re-style every formatted line into the Inter font so the typed text
        // matches the rest of the toolkit. The style is picked per GUI scale by Ui; it is
        // captured once here, which is fine because widgets re-create per screen.
        this.box.addFormatter((s, i) -> FormattedCharSequence.forward(s, Ui.ui("").getStyle()));
        this.box.setResponder(this::onEdited);
    }

    /** Called by the EditBox responder on every edit; subclasses override to hook raw edits. */
    protected void onEdited(String value) {
        if (this.validator.test(value)) {
            this.lastValid = value;
        }
        if (this.onChanged != null) {
            this.onChanged.accept(value);
        }
    }

    public TextField theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    public TextField radius(float radius) {
        this.radius = radius;
        return this;
    }

    /** Sets the text programmatically (fires the responder like typing does). */
    public TextField value(String value) {
        this.box.setValue(value);
        return this;
    }

    /** Runs on every edit with the raw text. */
    public TextField onChanged(Consumer<String> onChanged) {
        this.onChanged = onChanged;
        return this;
    }

    public TextField hint(String hint) {
        this.hint = hint;
        return this;
    }

    public TextField maxLength(int maxLength) {
        this.box.setMaxLength(maxLength);
        return this;
    }

    /**
     * Marks text failing the predicate with the error colour while typing. Validation never
     * blocks input; see the class javadoc.
     */
    public TextField validator(Predicate<String> validator) {
        this.validator = validator;
        return this;
    }

    /** Draws (true, default) or skips the rounded card behind the text. */
    public TextField bordered(boolean bordered) {
        this.bordered = bordered;
        return this;
    }

    /** Overrides the text colour for the valid state; -1 uses the theme. */
    public TextField textColor(int color) {
        this.textOverride = color;
        return this;
    }

    public TextField tooltip(Component tooltip) {
        this.setTooltip(Tooltip.create(Ui.ui(tooltip)));
        return this;
    }

    public TextField tooltip(String tooltip) {
        return this.tooltip(Component.literal(tooltip));
    }

    /** The raw text currently in the field. */
    public String getValue() {
        return this.box.getValue();
    }

    /** The last text that passed the validator (starts as the initial value). */
    public String getValidValue() {
        return this.lastValid;
    }

    public boolean isValid() {
        return this.validator.test(this.box.getValue());
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        this.box.setFocused(focused);
    }

    @Override
    public boolean isFocused() {
        return this.box.isFocused();
    }

    // No children() override: in 26.1 children() lives on ContainerEventHandler, which
    // AbstractWidget does not implement. The handlers below forward to the inner box, which is
    // enough for a self-contained widget.

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.isActive()) {
            if (this.isMouseOver(event.x(), event.y())) {
                this.box.setFocused(true);
                // The inner box is shorter and narrower than the card (text padding on all
                // sides), so a click on the padding passes our hit test but is dropped by
                // EditBox's own. Returning false there would make 26.1's container dispatch
                // (getChildAt routes to one child; keyboard focus is only granted when that
                // child returns true) withhold screen focus, so the field lights up but
                // typing goes nowhere until a click lands inside the box. Remap the click
                // into the box instead: exact position for real hits, nearest edge for
                // padding clicks, and claim the click either way.
                this.box.mouseClicked(this.intoBox(event), doubleClick);
                return true;
            }
            this.box.setFocused(false);
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Maps a click anywhere on the card into the inner box's rectangle (the caret seeks there). */
    private MouseButtonEvent intoBox(MouseButtonEvent event) {
        double x = Math.clamp(event.x(),
                this.box.getX() + 0.5, this.box.getX() + Math.max(1, this.box.getWidth()) - 0.5);
        double y = this.box.getY() + Math.max(1, this.box.getHeight()) / 2.0;
        return new MouseButtonEvent(x, y, event.buttonInfo());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.isActive() && this.box.isFocused()) {
            return this.box.mouseReleased(event);
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.isActive() && this.box.isFocused() && this.box.canConsumeInput()) {
            return this.box.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.isActive() && this.box.isFocused()) {
            return this.box.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        boolean focused = this.box.isFocused();
        if (this.bordered) {
            int border = focused ? this.theme.cardBorderHover : this.theme.cardBorder;
            Ui.roundRectBorder(gfx, this.getX(), this.getY(), this.getWidth(), this.getHeight(),
                    this.radius, this.theme.card, border, 1.0f);
        }

        // Error colour while the text fails validation; never blocks typing (see class javadoc).
        int color = this.textOverride >= 0 ? this.textOverride : this.theme.text;
        if (!this.isValid()) {
            color = ERROR_COLOR;
        }
        this.box.setTextColor(color);

        int lineHeight = Ui.font().lineHeight;
        int textX = this.getX() + this.leftPad;
        int textY = this.getY() + (this.getHeight() - lineHeight) / 2 + 1;
        this.box.setPosition(textX, textY);
        this.box.setWidth(Math.max(1, this.getWidth() - this.leftPad - this.rightPad));

        boolean empty = this.box.getValue().isEmpty();
        if (empty && this.hint != null && !this.hint.isEmpty() && !focused) {
            // Draw the hint ourselves (in the Inter font) instead of EditBox's vanilla-font hint.
            Ui.text(gfx, Ui.ui(this.hint), textX, textY, this.theme.textMuted);
        }

        // The inner box draws its own text, caret and selection, riding our font via the formatter.
        this.box.extractRenderState(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE,
                Ui.ui(this.hint == null || this.hint.isEmpty() ? "Text field" : this.hint));
    }
}
