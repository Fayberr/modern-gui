package net.fayber.faybergui.widget;

import net.fayber.faybergui.render.Theme;
import net.fayber.faybergui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Stepper: a compact {@code [-] value [+]} control for numbers, like the numeric steppers in
 * native macOS and iOS preferences.
 *
 * <p>Binding pattern: live-read. The value is read through {@code getter} every frame (so
 * external changes, clamps and other writers to the same setting show immediately) and each step
 * writes through {@code setter}, clamped to {@code [min, max]}.
 *
 * <p>The minus and plus buttons support press-and-hold repeat: an initial 400 ms delay,
 * then one step every 80 ms, so "sweeping" to a far value does not need dozens of clicks. Holding
 * is tracked from the mouse press here rather than through {@code onPress}, because the repeat
 * must keep firing between mouse events (checked each drawn frame). Buttons at the {@code min} or
 * {@code max} limit render darkened and do nothing.
 */
public class Stepper extends AbstractButton {
    /** Widget height. */
    public static final int HEIGHT = 24;
    /** Width of the widget (two 24px buttons around a 44px value slot). */
    public static final int WIDTH = 92;
    /** Side of the square minus/plus buttons. */
    private static final int BUTTON = 24;
    /** Corner radius of the buttons. */
    private static final float RADIUS = 4.0f;
    /** Size of the minus/plus glyphs. */
    private static final float GLYPH_SIZE = 10.0f;
    /** Hold time before repeating starts. */
    private static final long HOLD_DELAY_MS = 400;
    /** Repeat interval once holding. */
    private static final long REPEAT_MS = 80;

    private final Supplier<Integer> getter;
    private final IntConsumer setter;
    private final int min;
    private final int max;
    private final int step;

    protected Theme theme = Theme.dark();
    /** Optional colour overrides for the button fill; -1 falls back to the theme. */
    private int buttonOverride = -1;
    private int buttonHoverOverride = -1;

    /** Side currently held down: -1 minus, 0 none, 1 plus. */
    private int holding;
    /** When the held button went down, for the initial repeat delay. */
    private long holdStartMs;
    /** When the last held repeat fired. */
    private long lastRepeatMs;

    public Stepper(int x, int y, Supplier<Integer> getter, IntConsumer setter, int min, int max, int step) {
        super(x, y, WIDTH, HEIGHT, Component.empty());
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
        this.step = Math.max(1, step);
    }

    public Stepper theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    /** Overrides the button fill for both states (defaults: theme card / cardHover). */
    public Stepper buttonColor(int fill, int hover) {
        this.buttonOverride = fill;
        this.buttonHoverOverride = hover;
        return this;
    }

    // ------------------------------------------------------------------ behaviour

    /** Applies {@code direction} steps (-1 or 1), clamped; returns false when already at the limit. */
    private boolean step(int direction) {
        int current = this.getter.get();
        int next = Math.clamp(current + direction * this.step, this.min, this.max);
        if (next == current) {
            return false;
        }
        this.setter.accept(next);
        return true;
    }

    //
    // The abstract onPress hook is unused: clicks are dispatched through the overridden
    // mouseClicked below (the widget is a selector, not a push button).
    @Override
    public void onPress(net.minecraft.client.input.InputWithModifiers input) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int side = this.sideAt(event.x(), event.y());
        if (side != 0 && this.step(side)) {
            this.holding = side;
            this.holdStartMs = Util.getMillis();
            this.lastRepeatMs = this.holdStartMs;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.holding = 0;
        return super.mouseReleased(event);
    }

    /** -1 when the point is on the minus button, 1 on the plus button, 0 elsewhere. */
    private int sideAt(double mx, double my) {
        if (my < this.getY() || my >= this.getY() + this.getHeight()) {
            return 0;
        }
        if (mx >= this.getX() && mx < this.getX() + BUTTON) {
            return -1;
        }
        if (mx >= this.getX() + this.getWidth() - BUTTON && mx < this.getX() + this.getWidth()) {
            return 1;
        }
        return 0;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        // Press-and-hold repeat: after the initial delay, one step every REPEAT_MS. Checked here
        // so it keeps firing between mouse events without any timers.
        if (this.holding != 0) {
            long now = Util.getMillis();
            if (now - this.holdStartMs >= HOLD_DELAY_MS && now - this.lastRepeatMs >= REPEAT_MS) {
                this.lastRepeatMs = now;
                if (!this.step(this.holding)) {
                    this.holding = 0; // ran into the limit while holding; stop repeating
                }
            }
        }

        int value = this.getter.get();
        int leftX = this.getX();
        int rightX = this.getX() + this.getWidth() - BUTTON;
        int btnY = this.getY() + (this.getHeight() - BUTTON) / 2;
        boolean minusEnabled = value > this.min;
        boolean plusEnabled = value < this.max;

        this.drawButton(gfx, leftX, btnY, Icons.MINUS, minusEnabled,
                this.holding < 0 || (this.holding == 0 && this.sideAt(mouseX, mouseY) < 0));
        this.drawButton(gfx, rightX, btnY, Icons.PLUS, plusEnabled,
                this.holding > 0 || (this.holding == 0 && this.sideAt(mouseX, mouseY) > 0));

        int textY = this.getY() + (this.getHeight() - Ui.font().lineHeight) / 2 + 1;
        int textColor = this.theme.text;
        if (!this.isActive()) {
            textColor = Theme.darken(textColor, 0.45f);
        }
        Ui.textCentered(gfx, Ui.ui(String.valueOf(value)), this.getX() + this.getWidth() / 2, textY, textColor);
    }

    /** Draws one minus/plus button; a disabled button is darkened and ignores hover. */
    private void drawButton(GuiGraphicsExtractor gfx, int x, int y, Icons.Glyph glyph,
                            boolean enabled, boolean hovered) {
        int fill, border, glyphColor;
        if (enabled) {
            fill = hovered
                    ? (this.buttonHoverOverride >= 0 ? this.buttonHoverOverride : this.theme.cardHover)
                    : (this.buttonOverride >= 0 ? this.buttonOverride : this.theme.card);
            border = hovered ? this.theme.cardBorderHover : this.theme.cardBorder;
            glyphColor = this.theme.text;
        } else {
            fill = Theme.darken(this.buttonOverride >= 0 ? this.buttonOverride : this.theme.card, 0.45f);
            border = Theme.darken(this.theme.cardBorder, 0.45f);
            glyphColor = Theme.darken(this.theme.textMuted, 0.45f);
        }
        Ui.roundRectBorder(gfx, x, y, BUTTON, BUTTON, RADIUS, fill, border, 1.0f);
        glyph.draw(gfx, x + BUTTON / 2.0f, y + BUTTON / 2.0f, GLYPH_SIZE, glyphColor);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
