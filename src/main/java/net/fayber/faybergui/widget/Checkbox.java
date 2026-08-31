package net.fayber.faybergui.widget;

import net.fayber.faybergui.render.Theme;
import net.fayber.faybergui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Checkbox: a small rounded square with an optional label to its right; the whole widget is the
 * click target, so the label is clickable too (unlike vanilla, where the box is a few pixels).
 *
 * <p>Two binding patterns are supported, mirroring the rest of the toolkit:
 *
 * <ul>
 *   <li><b>Live-read</b> (getter/setter): the checked state is read through the getter every
 *       frame, so external changes (config reloads, other widgets writing the same value) show
 *       immediately, and presses write through the setter (live preview).</li>
 *   <li><b>Internal state</b> (initial value + {@link Consumer}&lt;Boolean&gt;): the widget owns
 *       the boolean and reports flips through the callback; use it for standalone dialogs where
 *       nothing else reads the value.</li>
 * </ul>
 *
 * <p>When checked the box fills with the accent colour and a checkmark scales in over ~100 ms
 * (time-normalised, so the feel is identical at any frame rate); unchecked it is a bordered card.
 * The label uses the full text colour while checked and the secondary colour while unchecked, so
 * the row reads as "on" at a glance. Configurable through {@link #theme} and colour overrides.
 */
public class Checkbox extends AbstractButton {
    /** Side of the rounded square, GUI pixels. */
    public static final int BOX = 14;
    /** Gap between the box and the label. */
    private static final int LABEL_GAP = 8;
    /** Check glyph size when fully scaled in. */
    private static final float CHECK_SIZE = 10.0f;
    /** Time-normalised check scale-in speed (per second); ~95% in 100 ms. */
    private static final float CHECK_SPEED = 30.0f;
    /** Frame gap cap so a stall never teleports the ease. */
    private static final float MAX_FRAME_SECONDS = 0.1f;
    /** Corner radius of the box. */
    private static final float RADIUS = 4.0f;

    private final Supplier<Boolean> getter;
    private final Consumer<Boolean> setter;
    private final Component label;
    /** Backing state for the internal-state constructor; unused by the live-read constructor. */
    private boolean internalState;

    protected Theme theme = Theme.dark();
    /** Optional colour overrides; -1 falls back to the theme colours. */
    private int checkedOverride = -1;
    private int checkedHoverOverride = -1;
    private int labelOverride = -1;
    private int labelUncheckedOverride = -1;

    /** 0 = hidden, 1 = fully drawn; negative means "not initialised yet". */
    private float checkScale = -1.0f;
    /** Timestamp of the last drawn frame, for the time-normalised ease. */
    private long lastFrameMs = -1L;

    /**
     * Live-read constructor: the state is read through {@code getter} every frame and presses
     * write through {@code setter}, so external changes show immediately (live preview).
     */
    public Checkbox(int x, int y, String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        super(x, y, 0, 0, Component.empty());
        this.getter = getter;
        this.setter = setter;
        this.label = Ui.ui(label);
        this.setWidth(BOX + LABEL_GAP + Ui.font().width(this.label));
        this.setHeight(Math.max(18, Ui.font().lineHeight));
    }

    /**
     * Internal-state constructor for standalone use: the widget owns the boolean, starts at
     * {@code initial} and reports every flip through {@code onChange} (already flipped).
     */
    public Checkbox(int x, int y, String label, boolean initial, Consumer<Boolean> onChange) {
        super(x, y, 0, 0, Component.empty());
        this.internalState = initial;
        this.getter = () -> this.internalState;
        this.setter = value -> {
            this.internalState = value;
            onChange.accept(value);
        };
        this.label = Ui.ui(label);
        this.setWidth(BOX + LABEL_GAP + Ui.font().width(this.label));
        this.setHeight(Math.max(18, Ui.font().lineHeight));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        boolean next = !Boolean.TRUE.equals(this.getter.get());
        this.setter.accept(next);
    }

    // ------------------------------------------------------------- fluent config

    public Checkbox theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    /** Overrides the checked box fill for both states (defaults: theme accent / accentHover). */
    public Checkbox checkedColor(int fill, int hover) {
        this.checkedOverride = fill;
        this.checkedHoverOverride = hover;
        return this;
    }

    /** Overrides the label colour for both states (defaults: theme text when checked, else textSecondary). */
    public Checkbox labelColor(int checked, int unchecked) {
        this.labelOverride = checked;
        this.labelUncheckedOverride = unchecked;
        return this;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        boolean checked = Boolean.TRUE.equals(this.getter.get());
        boolean hovered = this.isHoveredOrFocused();

        // Ease the check scale toward its target, time-normalised like StyledSlider's knob glide.
        float target = checked ? 1.0f : 0.0f;
        long now = Util.getMillis();
        if (this.checkScale < 0.0f) {
            this.checkScale = target; // opening the screen should not animate every checkbox
        } else {
            float dt = Math.min((now - this.lastFrameMs) / 1000.0f, MAX_FRAME_SECONDS);
            this.checkScale += (target - this.checkScale) * (1.0f - (float) Math.exp(-dt * CHECK_SPEED));
        }
        this.lastFrameMs = now;

        int boxX = this.getX();
        int boxY = this.getY() + (this.getHeight() - BOX) / 2;
        if (checked) {
            int fill = hovered
                    ? (this.checkedHoverOverride >= 0 ? this.checkedHoverOverride : this.theme.accentHover)
                    : (this.checkedOverride >= 0 ? this.checkedOverride : this.theme.accent);
            Ui.roundRect(gfx, boxX, boxY, BOX, BOX, RADIUS, fill);
            if (this.checkScale > 0.01f) {
                Icons.CHECK.draw(gfx, boxX + BOX / 2.0f, boxY + BOX / 2.0f,
                        CHECK_SIZE * this.checkScale, this.theme.textOnAccent);
            }
        } else {
            Ui.roundRectBorder(gfx, boxX, boxY, BOX, BOX, RADIUS,
                    hovered ? this.theme.cardHover : this.theme.card,
                    hovered ? this.theme.cardBorderHover : this.theme.cardBorder, 1.0f);
        }

        int textY = this.getY() + (this.getHeight() - Ui.font().lineHeight) / 2 + 1;
        int labelColor = this.labelOverride >= 0 && checked
                ? this.labelOverride
                : (this.labelUncheckedOverride >= 0 && !checked
                        ? this.labelUncheckedOverride
                        : (checked || hovered ? this.theme.text : this.theme.textSecondary));
        Ui.text(gfx, this.label, this.getX() + BOX + LABEL_GAP, textY, labelColor);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
