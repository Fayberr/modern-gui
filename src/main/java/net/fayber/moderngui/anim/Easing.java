package net.fayber.moderngui.anim;

/**
 * A curve that maps linear time {@code t} in 0..1 to eased progress, 0 at the start and 1 at the
 * end. A functional interface, so a consumer can pass its own lambda; the constants cover what a
 * GUI normally needs. Inputs outside 0..1 are clamped by the callers, not here.
 */
@FunctionalInterface
public interface Easing {
    float apply(float t);

    Easing LINEAR = t -> t;

    /** Starts slow, ends fast. */
    Easing EASE_IN_CUBIC = t -> t * t * t;

    /** Starts fast, settles gently: the default for things that arrive. */
    Easing EASE_OUT_CUBIC = t -> {
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    };

    /** Slow at both ends. */
    Easing EASE_IN_OUT_CUBIC = t -> t < 0.5f
            ? 4.0f * t * t * t
            : 1.0f - (float) Math.pow(-2.0f * t + 2.0f, 3.0) / 2.0f;

    /** A longer, softer landing than cubic; good for slides across a whole panel. */
    Easing EASE_OUT_QUART = t -> {
        float u = 1.0f - t;
        return 1.0f - u * u * u * u;
    };

    /** Overshoots the target slightly and settles back: a springy knob. */
    Easing EASE_OUT_BACK = t -> {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float u = t - 1.0f;
        return 1.0f + c3 * u * u * u + c1 * u * u;
    };
}
