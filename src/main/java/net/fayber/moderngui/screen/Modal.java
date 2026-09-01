package net.fayber.moderngui.screen;

import net.fayber.moderngui.render.Theme;
import net.fayber.moderngui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A modal dialog: dims the whole screen and floats a centred card with a title, wrapped body
 * lines and up to three buttons. While one is open the {@link PopupHost} swallows every click
 * outside the card and routes ESC (cancel) and ENTER (primary action) here first, so the widgets
 * underneath are untouchable.
 *
 * <p>Promise-style helpers: {@link #confirm} takes an on-confirm and an on-cancel callback
 * (either may be null), and {@link #info} shows a single OK button.
 */
public class Modal implements ModalLayer {
    private static final int MIN_WIDTH = 240;
    private static final int MAX_WIDTH = 320;
    private static final float RADIUS = 10.0f;
    private static final int BUTTON_H = 26;
    private static final int BUTTON_GAP = 8;
    private static final int PADDING = 16;

    private final Component title;
    private final Component body;
    private final List<Button> buttons;

    private int x;
    private int y;
    private int width;
    private int height;
    private List<Component> bodyLines;

    private Modal(Component title, Component body, List<Button> buttons) {
        this.title = Ui.uiBold(title);
        this.body = body;
        this.buttons = buttons;
    }

    /** A confirm/cancel dialog; either callback may be null. */
    public static Modal confirm(Component title, Component body, Runnable onConfirm, Runnable onCancel) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(new Button("Cancel", ButtonStyle.GHOST, onCancel));
        buttons.add(new Button("Confirm", ButtonStyle.PRIMARY, onConfirm));
        return new Modal(title, body, buttons);
    }

    /** A confirm/cancel dialog with custom button labels; either callback may be null. */
    public static Modal confirm(Component title, Component body, String confirmLabel, String cancelLabel,
                                Runnable onConfirm, Runnable onCancel) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(new Button(cancelLabel, ButtonStyle.GHOST, onCancel));
        buttons.add(new Button(confirmLabel, ButtonStyle.PRIMARY, onConfirm));
        return new Modal(title, body, buttons);
    }

    /** A single-OK dialog. */
    public static Modal info(Component title, Component body) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(new Button("OK", ButtonStyle.PRIMARY, null));
        return new Modal(title, body, buttons);
    }

    /** A single-OK dialog with a custom label. */
    public static Modal info(Component title, Component body, String okLabel) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(new Button(okLabel, ButtonStyle.PRIMARY, null));
        return new Modal(title, body, buttons);
    }

    private void layout(Theme theme) {
        if (this.bodyLines != null) {
            return;
        }
        var mc = net.minecraft.client.Minecraft.getInstance();
        this.bodyLines = Ui.wrap(Ui.ui(this.body), MAX_WIDTH - PADDING * 2 - 20);

        int textW = 0;
        for (Component line : this.bodyLines) {
            textW = Math.max(textW, Ui.font().width(line));
        }
        this.width = Math.clamp(Math.max(textW, Ui.font().width(this.title)) + PADDING * 2 + 20,
                MIN_WIDTH, Math.min(MAX_WIDTH, mc.getWindow().getGuiScaledWidth() - 16));
        this.width = Math.max(this.width, MIN_WIDTH);
        int bodyH = this.bodyLines.size() * (Ui.font().lineHeight + 2);
        this.height = PADDING + Ui.font().lineHeight + 8 + bodyH + PADDING + BUTTON_H + PADDING;
        this.x = (mc.getWindow().getGuiScaledWidth() - this.width) / 2;
        this.y = (mc.getWindow().getGuiScaledHeight() - this.height) / 2;
    }

    /** @return true always: the modal owns the whole screen while it is open. */
    @Override
    public boolean handleClick(PopupHost host, net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        this.layout(host.theme);
        for (Button b : this.buttons) {
            if (event.x() >= b.x && event.x() < b.x + b.width && event.y() >= b.y && event.y() < b.y + b.height) {
                if (b.closeOnPress) {
                    host.closeModal();
                }
                if (b.action != null) {
                    b.action.run();
                }
                return true;
            }
        }
        // Click on the scrim or card body: swallowed, nothing outside is reachable.
        return true;
    }

    @Override
    public boolean handleKey(PopupHost host, net.minecraft.client.input.KeyEvent event) {
        if (event.isEscape()) {
            // ESC cancels: run the last button's action if it is a cancel-style ghost, else just close.
            Button cancel = this.buttons.size() > 1 ? this.buttons.get(0) : null;
            host.closeModal();
            if (cancel != null && cancel.action != null) {
                cancel.action.run();
            }
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            Button primary = this.buttons.get(this.buttons.size() - 1);
            host.closeModal();
            if (primary.action != null) {
                primary.action.run();
            }
            return true;
        }
        return true;
    }

    @Override
    public void extract(GuiGraphicsExtractor gfx, Theme theme, int mouseX, int mouseY, float partialTick) {
        this.layout(theme);
        var mc = net.minecraft.client.Minecraft.getInstance();
        // Dim over everything, on top of the content (the host draws after all widgets).
        net.fayber.moderngui.render.Ui.rect(gfx, 0, 0, mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(), 0x88000000);

        net.fayber.moderngui.render.Ui.shadow(gfx, this.x, this.y, this.width, this.height, RADIUS, 8.0f, 5);
        net.fayber.moderngui.render.Ui.roundRect(gfx, this.x, this.y, this.width, this.height, RADIUS, theme.card);

        Ui.text(gfx, this.title, this.x + PADDING, this.y + PADDING, theme.text);
        int lineY = this.y + PADDING + Ui.font().lineHeight + 8;
        for (Component line : this.bodyLines) {
            Ui.text(gfx, line, this.x + PADDING, lineY, theme.textSecondary);
            lineY += Ui.font().lineHeight + 2;
        }

        // Buttons, right-aligned along the bottom.
        int bx = this.x + this.width - PADDING;
        int by = this.y + this.height - BUTTON_H - PADDING;
        for (int i = this.buttons.size() - 1; i >= 0; i--) {
            Button b = this.buttons.get(i);
            bx -= b.width;
            b.x = bx;
            b.y = by;
            this.drawButton(gfx, theme, b, mouseX, mouseY);
            bx -= BUTTON_GAP;
        }
    }

    private void drawButton(GuiGraphicsExtractor gfx, Theme theme, Button b, int mouseX, int mouseY) {
        boolean hovered = mouseX >= b.x && mouseX < b.x + b.width && mouseY >= b.y && mouseY < b.y + b.height;
        int textY = b.y + (b.height - Ui.font().lineHeight) / 2 + 1;
        Component label = Ui.ui(b.label);
        if (b.style == ButtonStyle.PRIMARY) {
            Ui.roundRect(gfx, b.x, b.y, b.width, b.height, 5.0f,
                    hovered ? theme.accentHover : theme.accent);
            Ui.textCentered(gfx, label, b.x + b.width / 2, textY, theme.textOnAccent);
        } else {
            Ui.roundRectBorder(gfx, b.x, b.y, b.width, b.height, 5.0f,
                    hovered ? theme.cardHover : theme.card,
                    hovered ? theme.cardBorderHover : theme.cardBorder, 1.0f);
            Ui.textCentered(gfx, label, b.x + b.width / 2, textY, hovered ? theme.text : theme.textSecondary);
        }
    }

    private enum ButtonStyle {
        PRIMARY,
        GHOST
    }

    private static final class Button {
        final Component label;
        final ButtonStyle style;
        final Runnable action;
        final int width;
        final int height = BUTTON_H;
        final boolean closeOnPress;
        int x;
        int y;

        Button(String label, ButtonStyle style, Runnable action) {
            this.label = Ui.ui(Component.literal(label));
            this.style = style;
            this.action = action;
            this.width = Math.max(Ui.font().width(this.label) + 24, 72);
            this.closeOnPress = true;
        }
    }
}
