package net.fayber.moderngui.widget;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Ranged double slider: snaps to {@code step}, writes through the entry's Consumer<Double>. */
public class DoubleSlider extends StyledSlider {
    private final Supplier<Double> getter;
    private final Consumer<Double> setter;
    /** The value this widget last wrote, so external writes (reset, restore) are detectable. */
    private double lastKnown;

    public DoubleSlider(int x, int y, int w, Component label, double min, double max, double step,
                        Supplier<Double> getter, Consumer<Double> setter) {
        super(x, y, w, label, min, max, step, getter.get());
        this.getter = getter;
        this.setter = setter;
        this.lastKnown = getter.get();
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.literal(format(this.snappedValue())));
    }

    @Override
    protected void applyValue() {
        double v = this.snappedValue();
        this.lastKnown = v;
        this.setter.accept(v);
        this.fireChange();
        // No knob re-snap here: the knob follows the raw drag position (StyledSlider) and only
        // the written value snaps, so dragging feels smooth.
    }

    @Override
    protected void syncExternal() {
        double external = this.getter.get();
        if (external != this.lastKnown) {
            this.lastKnown = external;
            this.externalValue(external);
        }
    }

    @Override
    protected String format(double value) {
        return String.format("%.2f", value);
    }
}
