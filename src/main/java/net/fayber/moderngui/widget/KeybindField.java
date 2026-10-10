package net.fayber.moderngui.widget;

import net.fayber.moderngui.render.Theme;
import net.fayber.moderngui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Keybind capture field: a ghost button showing the currently bound key; clicking it puts it into
 * a listening state ("Press a key...") where the next key press or mouse click becomes the bind.
 *
 * <p>Binding pattern: live-read plus callback. The displayed bind is read through the
 * {@code getter} every frame (so external resets show immediately), and a captured bind is
 * reported through {@code onChange}. The reported code is: SDL scancodes as-is for keyboard keys,
 * and {@code 1000 + button} for mouse buttons, so a single int carries both and
 * {@code code >= MOUSE_CODE_BASE} always means a mouse bind. {@link #keyName(int)} resolves either
 * kind to a display name and is public so other widgets (tooltips, conflict lists) can reuse it.
 *
 * <p>While listening, Escape cancels without changing the bind (keyboard Escape via
 * {@link #keyPressed}, mouse Escape is the click itself and cancels too). Capture consumes the
 * event that confirms the bind, so the press that starts listening does not also set it.
 */
public class KeybindField extends AbstractButton {
    /** Widget height. */
    public static final int HEIGHT = 26;
    /** Mouse codes passed through {@code onChange} start here: {@code MOUSE_CODE_BASE + button}. */
    public static final int MOUSE_CODE_BASE = 1000;
    /** Corner radius of the field card. */
    private static final float RADIUS = 5.0f;
    /** Period of the listening-state border pulse, in milliseconds. */
    private static final long PULSE_MS = 400;

    private final Supplier<Integer> getter;
    private final Consumer<Integer> onChange;

    protected Theme theme = Theme.dark();
    /** Optional border colour override while listening; -1 uses the theme hover border. */
    private int listeningBorderOverride = -1;

    private boolean listening;

    public KeybindField(int x, int y, int w, Supplier<Integer> getter, Consumer<Integer> onChange) {
        super(x, y, w, HEIGHT, Component.empty());
        this.getter = getter;
        this.onChange = onChange;
    }

    public KeybindField theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    /** Overrides the pulsing border colour while listening (defaults: theme cardBorderHover). */
    public KeybindField listeningColor(int border) {
        this.listeningBorderOverride = border;
        return this;
    }

    /**
     * Resolves a bind code (an SDL scancode, or {@code MOUSE_CODE_BASE + button} for mouse binds)
     * to a human-readable name such as {@code "A"}, {@code "F5"}, {@code "L Shift"} or
     * {@code "Mouse Left"}. Negative codes are the unbound sentinel and resolve to
     * {@code "Not bound"} (what vanilla and Cloth show). Public so other widgets can render key
     * names too.
     */
    public static String keyName(int code) {
        if (code < 0) {
            return "Not bound";
        }
        if (code >= MOUSE_CODE_BASE) {
            return mouseName(code - MOUSE_CODE_BASE);
        }
        if (code == InputConstants.KEY_ESCAPE) {
            return "Esc";
        }
        if (code == InputConstants.KEY_SPACE) {
            return "Space";
        }
        if (code == InputConstants.KEY_TAB) {
            return "Tab";
        }
        if (code == InputConstants.KEY_RETURN) {
            return "Enter";
        }
        if (code == InputConstants.KEY_BACKSPACE) {
            return "Backspace";
        }
        if (code == InputConstants.KEY_DELETE) {
            return "Delete";
        }
        if (code == InputConstants.KEY_HOME) {
            return "Home";
        }
        if (code == InputConstants.KEY_END) {
            return "End";
        }
        if (code == InputConstants.KEY_PAGEUP) {
            return "Page Up";
        }
        if (code == InputConstants.KEY_PAGEDOWN) {
            return "Page Down";
        }
        if (code == InputConstants.KEY_UP) {
            return "Up";
        }
        if (code == InputConstants.KEY_DOWN) {
            return "Down";
        }
        if (code == InputConstants.KEY_LEFT) {
            return "Left";
        }
        if (code == InputConstants.KEY_RIGHT) {
            return "Right";
        }
        if (code == InputConstants.KEY_LSHIFT) {
            return "L Shift";
        }
        if (code == InputConstants.KEY_RSHIFT) {
            return "R Shift";
        }
        if (code == InputConstants.KEY_LCONTROL) {
            return "L Ctrl";
        }
        if (code == InputConstants.KEY_RCONTROL) {
            return "R Ctrl";
        }
        if (code == InputConstants.KEY_LALT) {
            return "L Alt";
        }
        if (code == InputConstants.KEY_RALT) {
            return "R Alt";
        }
        if (code >= InputConstants.KEY_F1 && code <= InputConstants.KEY_F12) {
            return "F" + (code - InputConstants.KEY_F1 + 1);
        }
        String name = InputConstants.Type.KEYBOARD.getOrCreate(code).getDisplayName().getString();
        if (name != null && !name.isEmpty()) {
            // Single-character key names are shown uppercase.
            return name.length() == 1 ? name.toUpperCase() : name;
        }
        return "Key " + code;
    }

    /** Display name for a mouse button index (0-based). */
    private static String mouseName(int button) {
        return switch (button) {
            case InputConstants.MOUSE_BUTTON_LEFT -> "Mouse Left";
            case InputConstants.MOUSE_BUTTON_RIGHT -> "Mouse Right";
            case InputConstants.MOUSE_BUTTON_MIDDLE -> "Mouse Middle";
            default -> "Mouse " + button;
        };
    }

    //
    // The abstract onPress hook is unused: clicks are dispatched through the overridden
    // mouseClicked below (the widget is a selector, not a push button).
    @Override
    public void onPress(net.minecraft.client.input.InputWithModifiers input) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.listening) {
            // Any click while listening confirms the bind as a mouse button (Escape included,
            // since a pressed Escape here arrives as a mouse event, not a key event).
            this.setBind(MOUSE_CODE_BASE + event.button());
            return true;
        }
        if (this.containsPoint(event.x(), event.y())) {
            this.listening = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.listening) {
            return false;
        }
        if (event.isEscape()) {
            this.listening = false; // cancel without changing the bind
            return true;
        }
        this.setBind(event.key());
        return true;
    }

    /** Applies a captured bind code and leaves the listening state. */
    private void setBind(int code) {
        this.listening = false;
        this.onChange.accept(code);
    }

    /** True when the GUI-space point sits inside this widget's bounds. */
    private boolean containsPoint(double x, double y) {
        return x >= this.getX() && x < this.getX() + this.getWidth()
                && y >= this.getY() && y < this.getY() + this.getHeight();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isActive() && this.isHoveredOrFocused();
        int fill = hovered && !this.listening ? this.theme.cardHover : this.theme.card;
        int border = this.listening
                ? this.pulseBorder()
                : (hovered ? this.theme.cardBorderHover : this.theme.cardBorder);
        Ui.roundRectBorder(gfx, this.getX(), this.getY(), this.getWidth(), this.getHeight(), RADIUS,
                fill, border, 1.0f);

        int textY = this.getY() + (this.getHeight() - Ui.font().lineHeight) / 2 + 1;
        if (this.listening) {
            Ui.textCentered(gfx, Ui.ui("Press a key..."),
                    this.getX() + this.getWidth() / 2, textY, this.theme.textMuted);
        } else {
            int code = this.getter.get();
            boolean unbound = code < 0; // the unbound sentinel resolves to "Not bound" below
            int text = unbound ? this.theme.textMuted
                    : (hovered ? this.theme.text : this.theme.textSecondary);
            if (!this.isActive()) {
                text = Theme.darken(text, 0.45f);
            }
            Ui.textCentered(gfx, Ui.ui(keyName(code)),
                    this.getX() + this.getWidth() / 2, textY, text);
        }
    }

    /**
     * Border colour while listening: a subtle pulse that lerps between the resting and hover
     * border colours on a {@value #PULSE_MS} ms square wave.
     */
    private int pulseBorder() {
        int base = this.listeningBorderOverride >= 0 ? this.listeningBorderOverride : this.theme.cardBorder;
        int bright = this.listeningBorderOverride >= 0 ? this.listeningBorderOverride : this.theme.cardBorderHover;
        boolean phase = (Util.getMillis() / PULSE_MS) % 2 == 0;
        return phase ? bright : base;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
