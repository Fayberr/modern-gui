package net.fayber.moderngui.widget;

import net.fayber.moderngui.render.Theme;
import net.fayber.moderngui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A small non-interactive pill label: the status chip ("Beta", "3 new", "Required") next to titles
 * and list rows. Width sizes itself to the text plus padding.
 *
 * <p>{@link #success}, {@link #warning} and {@link #danger} cover the common semantic colours;
 * {@link #tint} takes any custom (fill, text) pair. It is a real {@link AbstractWidget} so it can
 * sit in layouts like any other widget.
 */
public class Badge extends AbstractWidget {
    /** Horizontal padding on each side of the text. */
    private static final int PADDING = 8;

    private final Component text;
    private int fill;
    /** Label colour override; -1 resolves to {@code theme.textSecondary}. */
    private int textColorOverride = -1;

    protected Theme theme = Theme.dark();

    public Badge(int x, int y, String text) {
        this(x, y, Ui.ui(text));
    }

    public Badge(int x, int y, Component text) {
        super(x, y, Ui.font().width(text) + 2 * PADDING, 16, Component.empty());
        this.text = Ui.ui(text);
        this.fill = 0x22FFFFFF;
        this.active = false;
    }

    /** Soft green: "success", "synced", "enabled". */
    public static Badge success(int x, int y, String text) {
        return success(x, y, Component.literal(text));
    }

    /** Component variant: a translatable label resolves at draw time. */
    public static Badge success(int x, int y, Component text) {
        return new Badge(x, y, text).tint(0x229FDCA8, 0xFF9FDCA8);
    }

    /** Soft amber: "beta", "experimental", "pending". */
    public static Badge warning(int x, int y, String text) {
        return warning(x, y, Component.literal(text));
    }

    /** Component variant: a translatable label resolves at draw time. */
    public static Badge warning(int x, int y, Component text) {
        return new Badge(x, y, text).tint(0x22F7D79E, 0xFFF7D79E);
    }

    /** Soft red: "error", "failed", "conflict". */
    public static Badge danger(int x, int y, String text) {
        return danger(x, y, Component.literal(text));
    }

    /** Component variant: a translatable label resolves at draw time. */
    public static Badge danger(int x, int y, Component text) {
        return new Badge(x, y, text).tint(0x22F28B82, 0xFFF28B82);
    }

    /** Custom colours: a translucent fill wash and a fully opaque label colour. */
    public Badge tint(int fillArgb, int textArgb) {
        this.fill = fillArgb;
        this.textColorOverride = textArgb;
        return this;
    }

    public Badge theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        int label = this.textColorOverride >= 0 ? this.textColorOverride : this.theme.textSecondary;
        Ui.pill(gfx, this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.fill);
        int textY = this.getY() + (this.getHeight() - Ui.font().lineHeight) / 2 + 1;
        Ui.textCentered(gfx, this.text, this.getX() + this.getWidth() / 2, textY, label);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Pure display; nothing to narrate.
    }
}
