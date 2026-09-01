package net.fayber.faybergui.widget;

import net.fayber.faybergui.render.Theme;
import net.fayber.faybergui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * Square flat button that shows a glyph from the {@link Icons} atlas instead of text: the standard
 * shape for toolbar actions, row dismiss buttons, steppers and anywhere a 20-28px icon beats a
 * full label.
 *
 * <p>Weighted like {@link FlatButton}: {@link Style#PRIMARY} is an accent fill with the glyph in
 * {@code textOnAccent}, {@link Style#GHOST} is a card with a hairline border and the glyph in the
 * secondary text colour. Hovering brightens exactly like a FlatButton; a disabled button dims its
 * fill and glyph instead of relying on alpha, so it stays crisp on any background.
 *
 * <p>The glyph is always drawn centred, sized to a touch over half the button so its padding
 * matches the optical weight of a text label.
 */
public class IconButton extends AbstractButton {
    /**
     * Visual weight, shared with {@link FlatButton.Style} so the two read as one family.
     * {@code PRIMARY} = accent fill + glyph in {@code textOnAccent}, {@code GHOST} = card + border.
     */
    public enum Style {
        PRIMARY,
        GHOST
    }

    private final Icons.Glyph glyph;
    private final Runnable onPress;
    private Style style;

    protected Theme theme = Theme.dark();
    protected float radius = FlatButton.RADIUS;
    /** Optional glyph colour overrides; -1 falls back to the theme colours. */
    private int glyphOverride = -1;
    private int glyphHoverOverride = -1;

    public IconButton(int x, int y, int size, Icons.Glyph glyph, Runnable onPress) {
        this(x, y, size, glyph, onPress, Style.GHOST);
    }

    public IconButton(int x, int y, int size, Icons.Glyph glyph, Runnable onPress, Style style) {
        super(x, y, size, size, Component.empty());
        this.glyph = glyph;
        this.onPress = onPress;
        this.style = style;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.onPress.run();
    }

    public IconButton theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    /** Switches the visual weight after construction. */
    public IconButton style(Style style) {
        this.style = style;
        return this;
    }

    public IconButton radius(float radius) {
        this.radius = radius;
        return this;
    }

    /** Overrides the glyph colour for both states. */
    public IconButton glyphColor(int normal, int hover) {
        this.glyphOverride = normal;
        this.glyphHoverOverride = hover;
        return this;
    }

    public IconButton tooltip(Component tooltip) {
        this.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.ui(tooltip)));
        return this;
    }

    public IconButton tooltip(String tooltip) {
        return this.tooltip(Component.literal(tooltip));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHoveredOrFocused();
        boolean active = this.isActive();
        int fill, border, glyph;
        if (this.style == Style.PRIMARY) {
            fill = hovered ? this.theme.accentHover : this.theme.accent;
            border = fill;
            glyph = hovered
                    ? (this.glyphHoverOverride >= 0 ? this.glyphHoverOverride : this.theme.textOnAccent)
                    : (this.glyphOverride >= 0 ? this.glyphOverride : this.theme.textOnAccent);
        } else {
            fill = hovered ? this.theme.cardHover : this.theme.card;
            border = hovered ? this.theme.cardBorderHover : this.theme.cardBorder;
            glyph = hovered
                    ? (this.glyphHoverOverride >= 0 ? this.glyphHoverOverride : this.theme.text)
                    : (this.glyphOverride >= 0 ? this.glyphOverride : this.theme.textSecondary);
        }
        if (!active) {
            fill = Theme.darken(fill, 0.45f);
            border = Theme.darken(border, 0.45f);
            glyph = Theme.darken(glyph, 0.45f);
        }
        if (this.style == Style.PRIMARY) {
            Ui.roundRect(gfx, this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.radius, fill);
        } else {
            Ui.roundRectBorder(gfx, this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.radius,
                    fill, border, 1.0f);
        }

        float cx = this.getX() + this.getWidth() / 2.0f;
        float cy = this.getY() + this.getHeight() / 2.0f;
        this.glyph.draw(gfx, cx, cy, this.getWidth() * 0.55f, glyph);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
