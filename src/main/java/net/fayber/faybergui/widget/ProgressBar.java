package net.fayber.faybergui.widget;

import net.fayber.faybergui.render.Theme;
import net.fayber.faybergui.render.Ui;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleSupplier;

/**
 * A rounded progress track with an eased fill, live-read like the sliders: the value comes from a
 * {@link DoubleSupplier} every frame, so external progress shows immediately.
 *
 * <p>Two modes:
 *
 * <ul>
 *   <li><b>Determinate</b> (default): the fill eases towards the target each frame
 *       ({@code current += (target - current) * (1 - exp(-dt * 10))}), so bursty updates read as
 *       one smooth motion. Disable with {@link #animated(boolean)} for exact per-frame fills
 *       (scrubbing, progress mirrors of another slider).</li>
 *   <li><b>Indeterminate</b> ({@link #indeterminate(boolean)}): a 30%-wide pill sweeps back and
 *       forth, for work with no known duration.</li>
 * </ul>
 *
 * <p>At height 16 or more the percentage is drawn centred inside the bar (in
 * {@link #theme}.text); below that the bar is bare.
 *
 * <p>Pure display: never takes input, narrates nothing.
 */
public class ProgressBar extends AbstractWidget {
    /** Exponential ease rate, per second; matches the slider knob feel. */
    private static final float EASE_SPEED = 10.0f;
    /** Never integrate a longer step than this, so alt-tabbing does not snap the animation. */
    private static final double MAX_FRAME_SECONDS = 0.1;
    /** One full left-right sweep of the indeterminate pill, in milliseconds. */
    private static final float INDETERMINATE_PERIOD_MS = 1200.0f;

    private DoubleSupplier valueSupplier;

    protected Theme theme = Theme.dark();
    private boolean animated = true;
    private boolean indeterminate;
    private boolean showLabel = true;
    /** Fill colour override; -1 resolves to the slider hover fill (reads as accent on accent themes). */
    private int fillOverride = -1;
    /** Track colour override; -1 resolves to the slider track. */
    private int trackOverride = -1;

    /** Eased display value, 0..1; -1 means "not initialised yet". */
    private float displayValue = -1.0f;
    private long lastFrameMs = -1;

    public ProgressBar(int x, int y, int w) {
        this(x, y, w, 8, () -> 0.0);
    }

    public ProgressBar(int x, int y, int w, int h) {
        this(x, y, w, h, () -> 0.0);
    }

    public ProgressBar(int x, int y, int w, int h, DoubleSupplier valueSupplier) {
        super(x, y, w, h, Component.empty());
        this.valueSupplier = valueSupplier;
        this.active = false;
    }

    // ------------------------------------------------------------- fluent config

    /** Sources the 0..1 value live, every frame. */
    public ProgressBar value(DoubleSupplier valueSupplier) {
        this.valueSupplier = valueSupplier;
        return this;
    }

    public ProgressBar animated(boolean animated) {
        this.animated = animated;
        return this;
    }

    public ProgressBar indeterminate(boolean indeterminate) {
        this.indeterminate = indeterminate;
        return this;
    }

    /** Draws the percentage centred inside the bar (only rendered at height >= 16). */
    public ProgressBar showLabel(boolean showLabel) {
        this.showLabel = showLabel;
        return this;
    }

    public ProgressBar fillColor(int color) {
        this.fillOverride = color;
        return this;
    }

    public ProgressBar trackColor(int color) {
        this.trackOverride = color;
        return this;
    }

    public ProgressBar theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        int track = this.trackOverride >= 0 ? this.trackOverride : this.theme.sliderTrack;
        int fill = this.fillOverride >= 0 ? this.fillOverride : this.theme.sliderFillHover;
        float radius = this.getHeight() / 2.0f;
        Ui.pill(gfx, this.getX(), this.getY(), this.getWidth(), this.getHeight(), track);

        if (this.indeterminate) {
            float t = (Util.getMillis() % 1200.0f) / INDETERMINATE_PERIOD_MS;
            // Triangle wave: 0 -> 1 -> 0 over the period.
            float phase = t < 0.5f ? t * 2.0f : 2.0f - t * 2.0f;
            float pillWidth = this.getWidth() * 0.3f;
            float x = this.getX() + (this.getWidth() - pillWidth) * phase;
            Ui.pill(gfx, x, this.getY(), pillWidth, this.getHeight(), fill);
            return;
        }

        float target = (float) Math.clamp(this.valueSupplier.getAsDouble(), 0.0, 1.0);
        if (!this.animated || this.displayValue < 0.0f) {
            this.displayValue = target;
        } else {
            long now = Util.getMillis();
            double dt = this.lastFrameMs < 0
                    ? 0.0
                    : Math.min((now - this.lastFrameMs) / 1000.0, MAX_FRAME_SECONDS);
            this.displayValue += (target - this.displayValue) * (float) (1.0 - Math.exp(-dt * EASE_SPEED));
        }
        this.lastFrameMs = Util.getMillis();

        float fillWidth = Math.max(this.getHeight(), this.getWidth() * this.displayValue);
        if (fillWidth > radius * 2.0f) {
            Ui.pill(gfx, this.getX(), this.getY(), fillWidth, this.getHeight(), fill);
        }

        if (this.showLabel && this.getHeight() >= 16) {
            int percent = Math.round(this.displayValue * 100.0f);
            Component label = Ui.ui(percent + "%");
            int textY = this.getY() + (this.getHeight() - Ui.font().lineHeight) / 2 + 1;
            Ui.textCentered(gfx, label, this.getX() + this.getWidth() / 2, textY, this.theme.text);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Pure display; nothing to narrate.
    }
}
