package net.fayber.moderngui.widget;

import net.fayber.moderngui.anim.EasedProgress;
import net.fayber.moderngui.anim.Easing;
import net.fayber.moderngui.render.Theme;
import net.fayber.moderngui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Pill toggle: a capsule track (near-white when on, dark when off) with a round knob that slides
 * between the ends. Reads its state through the entry's getter every frame, so external changes
 * show immediately, and writes through on press (live preview). The knob eases towards its
 * target over a fixed time (an {@link EasedProgress}, so it takes the same time at any frame rate),
 * which is what makes a toggle feel like an app control rather than a checkbox. Tune it with
 * {@link #animation} or read the 0..1 position with {@link #knobProgress}, e.g. to slide a
 * label or fade a neighbour in step with the knob.
 */
public class PillToggle extends AbstractButton {
    /** Track sizes: SMALL 26x14, NORMAL 34x18, LARGE 44x24. */
    public enum Size {
        SMALL(26, 14),
        NORMAL(34, 18),
        LARGE(44, 24);

        public final int width;
        public final int height;

        Size(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private static final float KNOB_INSET = 2.5f;

    private final Supplier<Boolean> getter;
    private final Consumer<Boolean> setter;

    protected Theme theme = Theme.dark();
    private int onOverride = -1;
    private int onHoverOverride = -1;
    private int offOverride = -1;
    private int offHoverOverride = -1;
    private Runnable onChange;

    /** 0 = off position, 1 = on position. */
    private final EasedProgress knob = new EasedProgress().duration(160L).easing(Easing.EASE_OUT_CUBIC);
    /** False until the first frame: opening a screen must not animate every toggle. */
    private boolean knobInitialised;

    public PillToggle(int x, int y, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        super(x, y, Size.NORMAL.width, Size.NORMAL.height, Component.empty());
        this.getter = getter;
        this.setter = setter;
    }

    /** Constructor taking an explicit size variant. */
    public PillToggle(int x, int y, Size size, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        super(x, y, size.width, size.height, Component.empty());
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        boolean next = !Boolean.TRUE.equals(this.getter.get());
        this.setter.accept(next);
        if (this.onChange != null) {
            this.onChange.run();
        }
    }

    public PillToggle theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    /** Overrides the on-state track colour for both states. */
    public PillToggle onColor(int on, int onHover) {
        this.onOverride = on;
        this.onHoverOverride = onHover;
        return this;
    }

    /** Overrides the off-state track colour, normal and hovered. */
    public PillToggle offColor(int off, int offHover) {
        this.offOverride = off;
        this.offHoverOverride = offHover;
        return this;
    }

    /**
     * Sets how the knob slides: the full-travel duration in milliseconds (0 makes it snap) and the
     * easing curve. The default is 160 ms ease-out; {@link Easing#EASE_OUT_BACK} gives a springy knob.
     */
    public PillToggle animation(long durationMs, Easing easing) {
        this.knob.duration(durationMs).easing(easing);
        return this;
    }

    /** The knob position as last drawn: 0 at the off end, 1 at the on end (eased, may overshoot). */
    public float knobProgress() {
        return this.knob.value();
    }

    /** Runs after the value flips (the setter already ran; read the getter for the new state). */
    public PillToggle onChange(Runnable onChange) {
        this.onChange = onChange;
        return this;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        boolean on = Boolean.TRUE.equals(this.getter.get());
        float target = on ? 1.0f : 0.0f;
        if (!this.knobInitialised) {
            this.knobInitialised = true;
            this.knob.snap(target); // opening the screen should not animate every toggle
        } else {
            this.knob.animateTo(target);
        }
        float knobPos = this.knob.update();

        boolean hovered = this.isActive() && this.isHoveredOrFocused();
        int track = on
                ? (hovered
                        ? (this.onHoverOverride >= 0 ? this.onHoverOverride : this.theme.accentHover)
                        : (this.onOverride >= 0 ? this.onOverride : this.theme.accent))
                : (hovered
                        ? (this.offHoverOverride >= 0 ? this.offHoverOverride : this.theme.sliderTrackHover)
                        : (this.offOverride >= 0 ? this.offOverride : this.theme.offTrack));
        // Dark knob on the light on-track, light knob on the dark off-track.
        int knob = on ? this.theme.textOnAccent : this.theme.text;
        if (!this.isActive()) {
            track = Theme.darken(track, 0.45f);
            knob = Theme.darken(knob, 0.45f);
        }
        Ui.pill(gfx, this.getX(), this.getY(), this.getWidth(), this.getHeight(), track);

        float knobRadius = this.getHeight() / 2.0f - KNOB_INSET;
        float left = this.getX() + KNOB_INSET + knobRadius;
        float right = this.getX() + this.getWidth() - KNOB_INSET - knobRadius;
        float cx = left + (right - left) * knobPos;
        Ui.circle(gfx, cx, this.getY() + this.getHeight() / 2.0f, knobRadius, knob);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
