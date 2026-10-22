package net.fayber.moderngui.anim;

/**
 * Vertical scroll state for custom-drawn content: where the content is scrolled to (the
 * {@linkplain #target() target}) versus where it is currently drawn (the {@linkplain #offset()
 * displayed offset}), the clamping between them, and the geometry of a scrollbar for it.
 *
 * <p>Vanilla's scrolling is a single integer that jumps. This keeps a float offset that glides,
 * in one of two modes:
 *
 * <ul>
 *   <li>{@link Mode#MOMENTUM} (the default, same physics as {@code CardList}): the wheel adds
 *       velocity, the offset coasts and decays exponentially, so a notch travels
 *       {@link #wheelStep} pixels in total spread over a fraction of a second, and fast spins
 *       accumulate (capped). Reaching an end stops dead.
 *   <li>{@link Mode#EASED}: the wheel moves a target and the displayed offset closes the gap
 *       exponentially, which reads as a smooth follow rather than a fling.
 * </ul>
 *
 * <p>{@link #jumpTo} sets the offset with no animation (opening a page at a remembered position,
 * dragging the scrollbar); {@link #scrollTo} glides there. Motion is wall-clock based, so it is
 * identical at any frame rate. Draw content at {@link #snappedOffset} rather than {@link
 * #offset}: whole physical pixels keep text crisp while it moves.
 *
 * <p>Pure logic (no Minecraft types); every time-reading method has an overload taking the
 * timestamp so it can be unit tested.
 */
public final class ScrollState {
    /** How the wheel and {@link #scrollTo} move the content. */
    public enum Mode {
        MOMENTUM,
        EASED
    }

    private static final double MAX_FRAME_SECONDS = 0.1;
    private static final double MAX_SPEED = 4000.0;
    private static final double MOMENTUM_STOP = 6.0;
    private static final double EASE_SETTLE = 0.05;

    private Mode mode = Mode.MOMENTUM;
    private float wheelStep = 40.0f;
    /** Momentum: velocity decay per second. Eased: gap-closing rate per second. */
    private double friction = 10.0;

    private float contentHeight;
    private float viewportHeight;

    /** The displayed offset in pixels from the top; 0 shows the start of the content. */
    private double offset;
    private double target;
    private double velocity;
    private long lastMs = -1L;

    public ScrollState mode(Mode mode) {
        this.mode = mode;
        return this;
    }

    /** Pixels one wheel notch scrolls in total. */
    public ScrollState wheelStep(float pixels) {
        this.wheelStep = pixels;
        return this;
    }

    /**
     * Glide stiffness per second: higher stops sooner. The default (10) coasts a notch for about
     * 0.4s in {@link Mode#MOMENTUM} and eases to the target in about 0.3s in {@link Mode#EASED}.
     */
    public ScrollState friction(double perSecond) {
        this.friction = Math.max(0.1, perSecond);
        return this;
    }

    /** Sets the sizes the scroll range derives from; re-clamps the offset. Call on resize/relayout. */
    public ScrollState setSizes(float contentHeight, float viewportHeight) {
        this.contentHeight = Math.max(0.0f, contentHeight);
        this.viewportHeight = Math.max(0.0f, viewportHeight);
        this.target = this.clamp(this.target);
        this.offset = this.clamp(this.offset);
        return this;
    }

    public float maxScroll() {
        return Math.max(0.0f, this.contentHeight - this.viewportHeight);
    }

    public boolean isScrollable() {
        return this.maxScroll() > 0.0f;
    }

    private double clamp(double value) {
        return Math.clamp(value, 0.0, this.maxScroll());
    }

    /** Applies a wheel event; {@code wheelDelta} is the raw wheel value (positive scrolls up). */
    public void wheel(double wheelDelta) {
        if (!this.isScrollable()) {
            return;
        }
        if (this.mode == Mode.MOMENTUM) {
            this.velocity = Math.clamp(this.velocity - wheelDelta * this.wheelStep * this.friction,
                    -MAX_SPEED, MAX_SPEED);
        } else {
            this.target = this.clamp(this.target - wheelDelta * this.wheelStep);
        }
    }

    /** Scrolls by {@code pixels} (positive moves down), gliding there. */
    public void scrollBy(double pixels) {
        this.scrollTo(this.target + pixels);
    }

    /**
     * Glides to {@code position} with the eased approach (in either mode: momentum has no target
     * to chase, so an explicit destination always uses the ease) and cancels any coast.
     */
    public void scrollTo(double position) {
        this.velocity = 0.0;
        this.target = this.clamp(position);
    }

    /** Sets the offset immediately with no animation and cancels any glide. */
    public void jumpTo(double position) {
        this.velocity = 0.0;
        this.target = this.clamp(position);
        this.offset = this.target;
    }

    /** Advances the glide to now. Returns true when the displayed offset changed. */
    public boolean update() {
        return this.update(EasedProgress.nowMs());
    }

    public boolean update(long nowMs) {
        // Track the gap every frame so a wheel event after a long idle is not a giant first step.
        double dt = this.lastMs < 0L ? 0.0 : Math.min((nowMs - this.lastMs) / 1000.0, MAX_FRAME_SECONDS);
        this.lastMs = nowMs;
        return this.advance(dt);
    }

    private boolean advance(double dt) {
        double before = this.offset;
        if (this.velocity != 0.0 && this.isScrollable()) {
            double next = this.offset + this.velocity * dt;
            this.velocity *= Math.exp(-dt * this.friction);
            if (Math.abs(this.velocity) < MOMENTUM_STOP) {
                this.velocity = 0.0;
            }
            double clamped = this.clamp(next);
            if (clamped != next) {
                // Reached an end: stop dead instead of pressing against the edge.
                this.velocity = 0.0;
            }
            this.offset = clamped;
            this.target = clamped;
        } else if (this.offset != this.target) {
            this.offset += (this.target - this.offset) * (1.0 - Math.exp(-dt * this.friction));
            if (Math.abs(this.target - this.offset) < EASE_SETTLE) {
                this.offset = this.target;
            }
        }
        return this.offset != before;
    }

    /** Where the content is drawn, in pixels from the top (fractional while gliding). */
    public float offset() {
        return (float) this.offset;
    }

    /** Where the content is heading; equals {@link #offset} at rest. */
    public float target() {
        return (float) this.target;
    }

    /**
     * {@link #offset} rounded to a whole physical pixel for the given GUI scale. Translate content
     * by the negative of this: text and 1px lines then never straddle a device pixel mid-glide.
     */
    public float snappedOffset(float guiScale) {
        return Math.round(this.offset * guiScale) / guiScale;
    }

    public boolean isAnimating() {
        return this.velocity != 0.0 || this.offset != this.target;
    }

    // ---- scrollbar geometry -------------------------------------------------------------

    /**
     * Thumb length for a track of {@code trackLength} pixels: the visible fraction of the content,
     * never shorter than {@code minThumb} so it stays grabbable.
     */
    public float thumbLength(float trackLength, float minThumb) {
        if (!this.isScrollable() || this.contentHeight <= 0.0f) {
            return trackLength;
        }
        float length = trackLength * this.viewportHeight / this.contentHeight;
        return Math.clamp(length, Math.min(minThumb, trackLength), trackLength);
    }

    /** Thumb top, in pixels from the track top, for the displayed offset. */
    public float thumbOffset(float trackLength, float thumbLength) {
        float span = trackLength - thumbLength;
        float max = this.maxScroll();
        if (span <= 0.0f || max <= 0.0f) {
            return 0.0f;
        }
        return (float) (this.offset * span / max);
    }

    /**
     * The content offset that centres the thumb under a pointer {@code pointerInTrack} pixels from
     * the track top ("grab and centre", like a website scrollbar); feed it to {@link #jumpTo} while
     * dragging.
     */
    public float offsetForPointer(float pointerInTrack, float trackLength, float thumbLength) {
        float span = trackLength - thumbLength;
        if (span <= 0.0f) {
            return 0.0f;
        }
        float fraction = Math.clamp((pointerInTrack - thumbLength / 2.0f) / span, 0.0f, 1.0f);
        return fraction * this.maxScroll();
    }
}
