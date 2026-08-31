package net.fayber.faybergui.widget;

import net.fayber.faybergui.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A {@link TextField} restricted to numbers: digits, one decimal point and a leading minus sign
 * pass the filter (anything else renders in the error colour but still types, so "1e" halfway to
 * nothing is never a dead end - users can delete their way back to validity).
 *
 * <p>Values commit on Enter, on focus loss and on arrow-key stepping: the text is parsed,
 * clamped to {@code [min, max]}, snapped onto the {@code step} grid ({@code round(v/step)*step},
 * then clamped) and written back as the canonical trimmed string, so the field never rests on
 * "1.4999999999" or a value the bound system would reject. {@link Consumer}<code>&lt;Double&gt;</code>
 * change hooks fire on every commit; arrow keys (while focused, if {@link #arrowKeysStep} is on)
 * step and write through on each press for live binding.
 *
 * <p>With a {@link #valueSupplier} set, the field follows the bound value every frame while it
 * is not focused (the same read-every-frame trick the sliders and {@link PillToggle} use), so
 * external changes show up without a round trip.
 */
public class NumberField extends TextField {
    protected final double min;
    protected final double max;
    protected final double step;

    private Consumer<Double> onChange;
    private Supplier<Double> valueSupplier;
    private boolean arrowKeysStep = true;
    /** True when the committed value differs from the text, so focus loss can commit it. */
    private boolean dirty;

    public NumberField(int x, int y, int w, int h, double min, double max, double step) {
        super(x, y, w, h);
        this.min = min;
        this.max = max;
        this.step = step != 0.0 ? Math.abs(step) : 1e-9;
        // Character filter: empty, a bare minus, digits, and one decimal point. This only drives
        // the error colour; commits re-parse and clamp regardless.
        this.validator = NumberField::isNumberLike;
    }

    @Override
    protected void onEdited(String value) {
        this.dirty = true;
        super.onEdited(value);
    }

    /** Accepts digits, one dot and one leading minus; also the empty and in-progress states. */
    private static boolean isNumberLike(String s) {
        if (s.isEmpty() || s.equals("-")) {
            return true;
        }
        boolean sawDot = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '-') {
                if (i != 0) {
                    return false;
                }
            } else if (c == '.') {
                if (sawDot) {
                    return false;
                }
                sawDot = true;
            } else if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------- fluent config

    public NumberField theme(Theme theme) {
        super.theme(theme);
        return this;
    }

    /** Runs on every commit (Enter, focus loss, arrow step) with the clamped and snapped value. */
    public NumberField onChange(Consumer<Double> onChange) {
        this.onChange = onChange;
        return this;
    }

    /**
     * Live binding: while unfocused, the field follows this supplier every frame, so external
     * changes show immediately. Pass a snapshot supplier, not something that re-parses the field.
     */
    public NumberField valueSupplier(Supplier<Double> valueSupplier) {
        this.valueSupplier = valueSupplier;
        return this;
    }

    public NumberField arrowKeysStep(boolean arrowKeysStep) {
        this.arrowKeysStep = arrowKeysStep;
        return this;
    }

    // ------------------------------------------------------------------ behaviour

    /** Parses, clamps, snaps and writes back the canonical value; fires the change hook. */
    public void commit() {
        double parsed = this.parse(this.getValue());
        double snapped = Math.clamp(Math.round(parsed / this.step) * this.step, this.min, this.max);
        String canonical = canonical(snapped);
        this.dirty = false;
        if (!canonical.equals(this.getValue())) {
            this.value(canonical);
        } else {
            // The text already reads the same but may still parse differently (e.g. "07"); the
            // responder only ran on edits, so fire the hook here explicitly.
            this.lastValid = canonical;
        }
        if (this.onChange != null) {
            this.onChange.accept(snapped);
        }
    }

    /** Lenient parse of the current text; invalid characters and empty fields fall back to min. */
    private double parse(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return this.min;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.isActive() && this.isFocused()) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                this.commit();
                return true;
            }
            if (this.arrowKeysStep && (event.isUp() || event.isDown())) {
                double next = Math.clamp(this.parse(this.getValue()) + (event.isUp() ? this.step : -this.step),
                        this.min, this.max);
                next = Math.clamp(Math.round(next / this.step) * this.step, this.min, this.max);
                this.value(canonical(next));
                if (this.onChange != null) {
                    this.onChange.accept(next);
                }
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void setFocused(boolean focused) {
        // Focus loss is a commit point: Enter and the arrows are not the only way to finish.
        if (!focused && this.isFocused() && this.dirty) {
            this.commit();
        }
        super.setFocused(focused);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        if (this.valueSupplier != null && !this.isFocused()) {
            Double external = this.valueSupplier.get();
            if (external != null) {
                String canonical = canonical(external);
                if (!canonical.equals(this.getValue())) {
                    this.value(canonical);
                }
            }
        }
        super.extractWidgetRenderState(gfx, mouseX, mouseY, partialTick);
    }

    /**
     * Canonical text for a value: no trailing ".0" for integral values (like
     * {@code StyledSlider.trim}) and no float dust for fractional ones (snapping produces
     * 1.4000000000000001-shaped doubles), using the ROOT locale so the decimal point is a dot.
     */
    protected static String canonical(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1.0e15) {
            return String.valueOf((long) v);
        }
        String s = String.format(Locale.ROOT, "%.6f", v);
        if (s.indexOf('.') >= 0) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }
}
