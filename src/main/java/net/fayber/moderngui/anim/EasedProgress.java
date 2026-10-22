package net.fayber.moderngui.anim;

/**
 * An eased 0..1 animator: it glides from its current value to a new target over a fixed duration
 * along an {@link Easing} curve. This is the reusable core of a sliding toggle knob, an expanding
 * section or a fade.
 *
 * <p>Time is wall-clock milliseconds, so the motion is identical at any frame rate. Retargeting
 * mid-flight continues from the value currently shown (no jump) and takes time in proportion to
 * the distance left, so flipping a toggle back halfway is not slower than a full flip. Nothing
 * here touches Minecraft, which keeps it unit-testable: every method that reads the clock has an
 * overload taking the time explicitly.
 *
 * <p>{@link #snap} sets a value with no animation; use it for the first frame, so opening a
 * screen does not animate every control from its default.
 */
public final class EasedProgress {
    private long durationMs = 160L;
    private Easing easing = Easing.EASE_OUT_CUBIC;

    private float from;
    private float to;
    private long startMs;
    private float value;
    private boolean animating;

    public EasedProgress() {
    }

    public EasedProgress(float initial) {
        this.from = initial;
        this.to = initial;
        this.value = initial;
    }

    /** Wall-clock milliseconds on a monotonic clock. */
    public static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    /** Duration of a full 0 to 1 sweep; 0 makes every change instant. */
    public EasedProgress duration(long millis) {
        this.durationMs = Math.max(0L, millis);
        return this;
    }

    public EasedProgress easing(Easing easing) {
        this.easing = easing;
        return this;
    }

    public long durationMs() {
        return this.durationMs;
    }

    /** Heads for {@code target}, starting from whatever is currently shown. */
    public void animateTo(float target) {
        this.animateTo(target, nowMs());
    }

    public void animateTo(float target, long nowMs) {
        if (target == this.to && (this.animating || target == this.value)) {
            return;
        }
        this.update(nowMs);
        this.from = this.value;
        this.to = target;
        this.startMs = nowMs;
        this.animating = this.from != this.to;
        if (!this.animating || this.durationMs == 0L) {
            this.snapTo(target);
        }
    }

    /** Jumps to {@code target} immediately and stops any animation. */
    public void snap(float target) {
        this.snapTo(target);
    }

    private void snapTo(float target) {
        this.from = target;
        this.to = target;
        this.value = target;
        this.animating = false;
    }

    /** Advances to now and returns the eased value. */
    public float update() {
        return this.update(nowMs());
    }

    public float update(long nowMs) {
        if (!this.animating) {
            return this.value;
        }
        // Time scales with the distance travelled: a reversal from 30% takes 30% of the duration.
        float distance = Math.abs(this.to - this.from);
        float span = Math.max(1.0f, this.durationMs * Math.min(1.0f, distance));
        float t = Math.clamp((nowMs - this.startMs) / span, 0.0f, 1.0f);
        this.value = this.from + (this.to - this.from) * this.easing.apply(t);
        if (t >= 1.0f) {
            this.snapTo(this.to);
        }
        return this.value;
    }

    /** The value as of the last {@link #update}. */
    public float value() {
        return this.value;
    }

    public float target() {
        return this.to;
    }

    public boolean isAnimating() {
        return this.animating;
    }
}
