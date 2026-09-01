package net.fayber.faybergui.widget;

import net.fayber.faybergui.render.Theme;
import net.fayber.faybergui.render.Ui;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A small indeterminate spinner: eight dots on a circle chasing each other around, the classic
 * loading affordance for inline waits ("connecting...", "loading list"). Driven straight off
 * {@link Util#getMillis()}, so it animates whether or not anything else on the screen redraws.
 *
 * <p>Pure display: never takes input, narrates nothing.
 */
public class Spinner extends AbstractWidget {
    private static final int DOTS = 8;
    /** Milliseconds between one dot and the next reaching full brightness. */
    private static final long STEP_MS = 90;

    protected Theme theme = Theme.dark();
    /** Dot colour override; -1 resolves to the theme text colour. */
    private int colorOverride = -1;

    public Spinner(int x, int y, int size) {
        super(x, y, size, size, Component.empty());
        this.active = false;
    }

    /** Overrides the dot colour. */
    public Spinner color(int color) {
        this.colorOverride = color;
        return this;
    }

    public Spinner theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        int base = this.colorOverride >= 0 ? this.colorOverride : this.theme.text;
        float cx = this.getX() + this.getWidth() / 2.0f;
        float cy = this.getY() + this.getHeight() / 2.0f;
        float radius = this.getWidth() / 2.0f - 2.0f;
        float dotRadius = Math.max(1.0f, this.getWidth() * 0.09f);

        long t = Util.getMillis();
        for (int i = 0; i < DOTS; i++) {
            // Phase of dot i: one step behind its neighbour, eased through the full brightness.
            float phase = ((t / STEP_MS + i) % DOTS) / (float) DOTS;
            float eased = (float) Math.sin(phase * Math.PI); // 0 -> 1 -> 0 across the cycle
            int alpha = Math.round((0.25f + 0.75f * eased) * 255.0f);
            int color = (base & 0x00FFFFFF) | (alpha << 24);

            float angle = (float) (Math.PI * 2.0 * (i / (float) DOTS) - Math.PI / 2.0);
            float dx = cx + (float) Math.cos(angle) * radius;
            float dy = cy + (float) Math.sin(angle) * radius;
            Ui.circle(gfx, dx, dy, dotRadius, color);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Pure display; nothing to narrate.
    }
}
