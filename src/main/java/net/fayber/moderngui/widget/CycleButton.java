package net.fayber.moderngui.widget;

import net.fayber.moderngui.render.Theme;
import net.fayber.moderngui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Cycle button: a flat card that shows the current value and steps through a fixed list of values
 * on click (left click forward, right click backward). Reads its value through the getter every
 * frame, so external changes show immediately, and writes through on press (live preview).
 *
 * <p>Sizes itself to the widest value label plus padding, so a short enum does not carry a button
 * that is wider than its longest value. Styled like {@link FlatButton}'s GHOST weight, which is
 * what every other value-ish control on the right of a row uses. Configurable: {@link #theme},
 * fixed width, a min width, and a wrap toggle (off = clamped at the ends).
 */
public class CycleButton<T> extends AbstractButton {
    /** Horizontal room either side of the widest value label. */
    private static final int PADDING = 10;

    private final Supplier<T> getter;
    private final Consumer<T> setter;
    private final T[] values;
    private final Function<T, Component> namer;

    protected Theme theme = Theme.dark();
    private boolean wrap = true;
    private int minWidth = 0;
    private Runnable onChange;

    public CycleButton(int x, int y, int height, Supplier<T> getter, Consumer<T> setter,
                       T[] values, Function<T, String> namer) {
        super(x, y, 20, height, Component.empty());
        this.getter = getter;
        this.setter = setter;
        this.values = values;
        this.namer = value -> Ui.ui(namer.apply(value));
        int widest = 0;
        for (T value : values) {
            widest = Math.max(widest, Ui.font().width(this.namer.apply(value)));
        }
        this.setWidth(Math.max(widest + 2 * PADDING, minWidth));
    }

    /** Fixes the width instead of auto-sizing to the widest label. */
    public CycleButton<T> fixedWidth(int width) {
        this.setWidth(width);
        return this;
    }

    public CycleButton<T> minWidth(int minWidth) {
        this.minWidth = minWidth;
        this.setWidth(Math.max(this.getWidth(), minWidth));
        return this;
    }

    /** By default clicking past the last value wraps to the first; turning this off clamps. */
    public CycleButton<T> noWrap() {
        this.wrap = false;
        return this;
    }

    /** Runs after the value changes (the setter already ran). */
    public CycleButton<T> onChange(Runnable onChange) {
        this.onChange = onChange;
        return this;
    }

    public CycleButton<T> theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        boolean backward = input instanceof MouseButtonEvent event
                && event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        T next = this.values[this.nextIndex(backward ? -1 : 1)];
        this.setter.accept(next);
        if (this.onChange != null) {
            this.onChange.run();
        }
    }

    /** Index of the value {@code step} places past the current one, wrapping at the ends. */
    private int nextIndex(int step) {
        int current = -1;
        T value = this.getter.get();
        for (int i = 0; i < this.values.length; i++) {
            if (this.values[i] == value || this.values[i].equals(value)) {
                current = i;
                break;
            }
        }
        if (current < 0) {
            return 0;
        }
        int next = current + step;
        if (this.wrap) {
            return Math.floorMod(next, this.values.length);
        }
        return Math.clamp(next, 0, this.values.length - 1);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHoveredOrFocused();
        Ui.roundRectBorder(gfx, this.getX(), this.getY(), this.getWidth(), this.getHeight(), FlatButton.RADIUS,
                hovered ? this.theme.cardHover : this.theme.card,
                hovered ? this.theme.cardBorderHover : this.theme.cardBorder, 1.0f);
        int textY = this.getY() + (this.getHeight() - Ui.font().lineHeight) / 2 + 1;
        T value = this.getter.get();
        Component label = value == null ? Component.empty() : this.namer.apply(value);
        Ui.textCentered(gfx, label, this.getX() + this.getWidth() / 2, textY,
                hovered ? this.theme.text : this.theme.textSecondary);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
