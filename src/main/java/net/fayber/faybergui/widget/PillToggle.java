package net.fayber.faybergui.widget;

import net.fayber.faybergui.render.Theme;
import net.fayber.faybergui.render.Ui;
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
 * target each frame, which is what makes a toggle feel like an app control rather than a checkbox.
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

    /** 0 = off position, 1 = on position; negative means "not initialised yet". */
    private float knobPos = -1.0f;

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

    /** Runs after the value flips (the setter already ran; read the getter for the new state). */
    public PillToggle onChange(Runnable onChange) {
        this.onChange = onChange;
        return this;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        boolean on = Boolean.TRUE.equals(this.getter.get());
        float target = on ? 1.0f : 0.0f;
        if (this.knobPos < 0.0f) {
            this.knobPos = target; // opening the screen should not animate every toggle
        } else {
            this.knobPos += (target - this.knobPos) * 0.35f;
        }

        boolean hovered = this.isHoveredOrFocused();
        int track = on
                ? (hovered
                        ? (this.onHoverOverride >= 0 ? this.onHoverOverride : this.theme.accentHover)
                        : (this.onOverride >= 0 ? this.onOverride : this.theme.accent))
                : (hovered
                        ? (this.offHoverOverride >= 0 ? this.offHoverOverride : this.theme.sliderTrackHover)
                        : (this.offOverride >= 0 ? this.offOverride : this.theme.offTrack));
        Ui.pill(gfx, this.getX(), this.getY(), this.getWidth(), this.getHeight(), track);

        // Dark knob on the light on-track, light knob on the dark off-track.
        int knob = on ? this.theme.textOnAccent : this.theme.text;
        float knobRadius = this.getHeight() / 2.0f - KNOB_INSET;
        float left = this.getX() + KNOB_INSET + knobRadius;
        float right = this.getX() + this.getWidth() - KNOB_INSET - knobRadius;
        float cx = left + (right - left) * this.knobPos;
        Ui.circle(gfx, cx, this.getY() + this.getHeight() / 2.0f, knobRadius, knob);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
