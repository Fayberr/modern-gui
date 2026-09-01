package net.fayber.moderngui.widget;

import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Ranged int slider: snaps to {@code step}, writes through the entry's IntConsumer. */
public class IntSlider extends StyledSlider {
    private final IntSupplier getter;
    private final IntConsumer setter;
    /** The value this widget last wrote, so external writes (reset, restore) are detectable. */
    private int lastKnown;

    public IntSlider(int x, int y, int w, Component label, int min, int max, int step,
                     IntSupplier getter, IntConsumer setter) {
        super(x, y, w, label, min, max, step, getter.getAsInt());
        this.getter = getter;
        this.setter = setter;
        this.lastKnown = getter.getAsInt();
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.literal(trim(this.snappedValue())));
    }

    @Override
    protected void applyValue() {
        int v = (int) Math.round(this.snappedValue());
        this.lastKnown = v;
        this.setter.accept(v);
        this.fireChange();
        // No knob re-snap here: the knob follows the raw drag position (StyledSlider) and only
        // the written value snaps, so dragging feels smooth.
    }

    @Override
    protected void syncExternal() {
        int external = this.getter.getAsInt();
        if (external != this.lastKnown) {
            this.lastKnown = external;
            this.externalValue(external);
        }
    }

    @Override
    protected String format(double value) {
        return String.valueOf((long) value);
    }
}
