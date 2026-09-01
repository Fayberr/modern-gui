package net.fayber.faybergui.widget;

import net.fayber.faybergui.render.Theme;
import net.fayber.faybergui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A horizontal hairline, half a GUI pixel tall. Because {@link Ui#rect} draws at physical-pixel
 * precision, the line is a single monitor pixel at any GUI scale: thin enough to separate without
 * boxing anything in.
 *
 * <p>The default colour is the card border darkened for a little more contrast. The
 * {@link #labeled} variant splits the line around a small all-caps muted caption, the classic
 * "SECTION" rule for grouping form fields without a full header.
 *
 * <p>Pure display: never takes input, narrates nothing.
 */
public class Divider extends AbstractWidget {
    private static final int LABEL_GAP = 6;

    private final Component caption; // empty for the plain hairline

    protected Theme theme = Theme.dark();
    /** Line colour override; -1 resolves to a darkened card border. */
    private int colorOverride = -1;
    /** Caption colour override; -1 resolves to the muted text colour. */
    private int captionColorOverride = -1;

    public Divider(int x, int y, int width) {
        this(x, y, width, null);
    }

    private Divider(int x, int y, int width, Component caption) {
        super(x, y, width, caption == null ? 1 : Ui.font().lineHeight, Component.empty());
        this.caption = caption;
        this.active = false;
    }

    /** A hairline split around a small all-caps muted caption. */
    public static Divider labeled(int x, int y, int width, String caption) {
        return new Divider(x, y, width, Ui.ui(caption));
    }

    /** Overrides the hairline colour. */
    public Divider color(int color) {
        this.colorOverride = color;
        return this;
    }

    /** Overrides the caption colour (labeled variant only). */
    public Divider captionColor(int color) {
        this.captionColorOverride = color;
        return this;
    }

    public Divider theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    private int lineColor() {
        return this.colorOverride >= 0 ? this.colorOverride : Theme.darken(this.theme.cardBorder, 0.25f);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        if (this.caption == null) {
            Ui.rect(gfx, this.getX(), this.getY(), this.getWidth(), 0.5f, this.lineColor());
            return;
        }
        int captionColor = this.captionColorOverride >= 0 ? this.captionColorOverride : this.theme.textMuted;
        int textY = this.getY() + (this.getHeight() - Ui.font().lineHeight) / 2;
        int textWidth = Ui.font().width(this.caption);
        int textLeft = this.getX() + (this.getWidth() - textWidth) / 2;
        float lineY = this.getY() + this.getHeight() / 2.0f;
        Ui.rect(gfx, this.getX(), lineY, Math.max(0, textLeft - LABEL_GAP - this.getX()), 0.5f, this.lineColor());
        Ui.rect(gfx, textLeft + textWidth + LABEL_GAP, lineY,
                Math.max(0, this.getX() + this.getWidth() - textLeft - textWidth - LABEL_GAP), 0.5f, this.lineColor());
        Ui.text(gfx, this.caption, textLeft, textY, captionColor);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Pure display; nothing to narrate.
    }
}
