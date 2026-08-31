package net.fayber.faybergui.widget;

import net.fayber.faybergui.render.Theme;
import net.fayber.faybergui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * A standalone bold section title: uppercase semibold in the muted colour, matching the section
 * headers of a config list, but placeable anywhere. Optionally carries a hairline under it
 * ({@link #withDivider}) to close off the section visually.
 *
 * <p>Pure display: never takes input, narrates nothing.
 */
public class SectionHeader extends AbstractWidget {
    private static final int DIVIDER_GAP = 8;

    private final Component title;
    private boolean divider;
    private int dividerWidth = -1;

    protected Theme theme = Theme.dark();
    /** Title colour override; -1 resolves to the muted text colour. */
    private int colorOverride = -1;
    /** Divider colour override; -1 resolves to a darkened card border. */
    private int dividerColorOverride = -1;

    public SectionHeader(int x, int y, String title) {
        this(x, y, Ui.ui(title));
    }

    public SectionHeader(int x, int y, Component title) {
        super(x, y, Ui.font().width(title), Ui.font().lineHeight + 2, Component.empty());
        this.title = Ui.ui(title);
        this.active = false;
    }

    /** Adds a hairline {@link #DIVIDER_GAP} px below the text, spanning the given width. */
    public SectionHeader withDivider(int width) {
        this.divider = true;
        this.dividerWidth = width;
        this.setHeight(Ui.font().lineHeight + DIVIDER_GAP + 2);
        return this;
    }

    // ------------------------------------------------------------- fluent config

    /** Overrides the title colour. */
    public SectionHeader color(int color) {
        this.colorOverride = color;
        return this;
    }

    /** Overrides the divider colour. */
    public SectionHeader dividerColor(int color) {
        this.dividerColorOverride = color;
        return this;
    }

    public SectionHeader theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        int color = this.colorOverride >= 0 ? this.colorOverride : this.theme.textMuted;
        Ui.text(gfx, Ui.uiBold(this.title.getString().toUpperCase(Locale.ROOT)), this.getX(), this.getY(), color);
        if (this.divider) {
            int lineColor = this.dividerColorOverride >= 0
                    ? this.dividerColorOverride
                    : Theme.darken(this.theme.cardBorder, 0.25f);
            float lineY = this.getY() + Ui.font().lineHeight + DIVIDER_GAP;
            int width = this.dividerWidth >= 0 ? this.dividerWidth : this.getWidth();
            Ui.rect(gfx, this.getX(), lineY, width, 0.5f, lineColor);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Pure display; nothing to narrate.
    }
}
