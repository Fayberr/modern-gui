package net.fayber.faybergui.widget;

import net.fayber.faybergui.render.Theme;
import net.fayber.faybergui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * Flat rounded button in two weights: {@link Style#PRIMARY} (near-white fill, dark label, for the
 * confirming action) and {@link Style#GHOST} (dark card, light label, for everything else).
 *
 * <p>{@link AbstractButton#extractWidgetRenderState} is final but only dispatches to
 * {@link #extractContents}, so overriding {@code extractContents} fully replaces the vanilla
 * sprite rendering while keeping click sounds, enter/space activation and narration.
 *
 * <p>Configurable through fluent setters: {@link #theme}, {@link #radius}, {@link #size},
 * per-state fill overrides ({@link #fill}, {@link #fillHover}) and text colour overrides. Disabled
 * buttons dim automatically ({@link #setAlpha}); an {@link #enabledColor} override can tint the
 * label instead.
 */
public class FlatButton extends AbstractButton {
    public enum Style {
        PRIMARY,
        GHOST
    }

    /** Standard heights: COMPACT 22, NORMAL 28, LARGE 34. */
    public enum Size {
        COMPACT(22),
        NORMAL(28),
        LARGE(34);

        public final int height;

        Size(int height) {
            this.height = height;
        }
    }

    /** Default corner radius. */
    protected static final float RADIUS = 5.0f;

    protected final Runnable onPress;
    protected final Style style;

    protected Theme theme = Theme.dark();
    protected float radius = RADIUS;
    /** Optional per-widget fill overrides; -1 falls back to the theme colours. */
    protected int fillOverride = -1;
    protected int fillHoverOverride = -1;
    protected int textOverride = -1;
    protected int textHoverOverride = -1;
    /** Corner radius override for the disabled state is not needed; the alpha handles it. */

    public FlatButton(int x, int y, int w, int h, Component message, Runnable onPress) {
        this(x, y, w, h, message, onPress, Style.GHOST);
    }

    public FlatButton(int x, int y, int w, int h, Component message, Runnable onPress, Style style) {
        super(x, y, w, h, Ui.ui(message));
        this.onPress = onPress;
        this.style = style;
    }

    /** A GHOST button with a standard height. */
    public static FlatButton builder(Component message, int x, int y, int w, Runnable onPress) {
        return new FlatButton(x, y, w, Size.NORMAL.height, message, onPress);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.onPress.run();
    }

    // ------------------------------------------------------------- fluent config

    public FlatButton theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    public FlatButton radius(float radius) {
        this.radius = radius;
        return this;
    }

    /** Overrides the fill colour for both states; pass the hover colour for a subtler effect. */
    public FlatButton fill(int fill, int hover) {
        this.fillOverride = fill;
        this.fillHoverOverride = hover;
        return this;
    }

    /** Overrides the label colour for both states. */
    public FlatButton textColor(int text, int hover) {
        this.textOverride = text;
        this.textHoverOverride = hover;
        return this;
    }

    public FlatButton tooltip(Component tooltip) {
        this.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.ui(tooltip)));
        return this;
    }

    public FlatButton tooltip(String tooltip) {
        return this.tooltip(Component.literal(tooltip));
    }

    public FlatButton enabledColor(int color) {
        this.textOverride = color;
        return this;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHoveredOrFocused();
        boolean active = this.isActive();
        int fill, border, textColor;
        if (this.style == Style.PRIMARY) {
            fill = hovered
                    ? (this.fillHoverOverride >= 0 ? this.fillHoverOverride : this.theme.accentHover)
                    : (this.fillOverride >= 0 ? this.fillOverride : this.theme.accent);
            border = fill;
            textColor = this.textOverride >= 0 ? this.textOverride : this.theme.textOnAccent;
            Ui.roundRect(gfx, this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.radius, fill);
        } else {
            fill = hovered
                    ? (this.fillHoverOverride >= 0 ? this.fillHoverOverride : this.theme.cardHover)
                    : (this.fillOverride >= 0 ? this.fillOverride : this.theme.card);
            border = hovered
                    ? (this.fillHoverOverride >= 0 ? this.fillHoverOverride : this.theme.cardBorderHover)
                    : (this.fillOverride >= 0 ? this.fillOverride : this.theme.cardBorder);
            textColor = hovered
                    ? (this.textHoverOverride >= 0 ? this.textHoverOverride : this.theme.text)
                    : (this.textOverride >= 0 ? this.textOverride : this.theme.textSecondary);
            Ui.roundRectBorder(gfx, this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.radius,
                    fill, border, 1.0f);
        }
        if (!active) {
            textColor = Theme.darken(textColor, 0.45f);
        }

        int textY = this.getY() + (this.getHeight() - Ui.font().lineHeight) / 2 + 1;
        Ui.textCentered(gfx, this.getMessage(), this.getX() + this.getWidth() / 2, textY, textColor);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
