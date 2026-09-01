package net.fayber.moderngui.widget;

import net.fayber.moderngui.render.Theme;
import net.fayber.moderngui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A static text element. It is a widget (rather than something a screen draws itself) so it can
 * sit in layouts, be positioned and sized like every other control and re-styled with a
 * {@link Theme} like every other control.
 *
 * <p>{@link Style} picks the font and default colour: {@link Style#REGULAR}/{@link Style#BOLD}
 * use the primary text colour, {@link Style#MUTED} the muted one (hints, footnotes) and
 * {@link Style#HEADING} bold in the full text colour (MC fonts are fixed-size, so weight and
 * colour, not point size, carry hierarchy). {@link #color} overrides any of that.
 *
 * <p>Alignment matters once the label has a real width: call {@code setWidth(...)} (or use a
 * layout that sets it) and choose an {@link Align}; the default width is the text's natural width
 * with {@link Align#LEFT}.
 *
 * <p>Pure display: never takes input, narrates nothing.
 */
public class Label extends AbstractWidget {
    /** Font weight and default colour. */
    public enum Style {
        REGULAR,
        BOLD,
        /** Muted colour, regular weight; hints and footnotes. */
        MUTED,
        /** Bold in the full text colour; section titles. */
        HEADING
    }

    /** Horizontal alignment within the widget's width. */
    public enum Align {
        LEFT,
        CENTER,
        RIGHT
    }

    private final Component text;
    private Style style = Style.REGULAR;
    private Align align = Align.LEFT;

    protected Theme theme = Theme.dark();
    /** Text colour override; -1 resolves through the {@link Style}. */
    private int colorOverride = -1;

    public Label(int x, int y, String text) {
        this(x, y, Ui.ui(text));
    }

    public Label(int x, int y, Component text) {
        super(x, y, Ui.font().width(text), Ui.font().lineHeight, Component.empty());
        this.text = Ui.ui(text);
        this.active = false;
    }

    public Label style(Style style) {
        this.style = style;
        return this;
    }

    public Label align(Align align) {
        this.align = align;
        return this;
    }

    /** Overrides the style's default colour. */
    public Label color(int color) {
        this.colorOverride = color;
        return this;
    }

    public Label theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        boolean bold = this.style == Style.BOLD || this.style == Style.HEADING;
        int color = this.colorOverride >= 0 ? this.colorOverride : switch (this.style) {
            case MUTED -> this.theme.textMuted;
            case HEADING -> this.theme.text;
            default -> this.theme.text;
        };

        int x = switch (this.align) {
            case CENTER -> this.getX() + (this.getWidth() - Ui.font().width(this.text)) / 2;
            case RIGHT -> this.getX() + this.getWidth() - Ui.font().width(this.text);
            case LEFT -> this.getX();
        };
        int y = this.getY() + (this.getHeight() - Ui.font().lineHeight) / 2;
        Ui.text(gfx, bold ? Ui.uiBold(this.text) : this.text, x, y, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Pure display; nothing to narrate.
    }
}
