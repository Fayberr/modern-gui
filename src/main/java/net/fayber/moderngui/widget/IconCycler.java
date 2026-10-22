package net.fayber.moderngui.widget;

import net.fayber.moderngui.anim.EasedProgress;
import net.fayber.moderngui.anim.Easing;
import net.fayber.moderngui.anim.SlideTransition;
import net.fayber.moderngui.render.IconPainter;
import net.fayber.moderngui.render.ItemIcon;
import net.fayber.moderngui.render.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows a list of icons, one at a time, and every {@code interval} slides the current icon out and
 * the next one in. Useful as a category thumbnail that hints at what is inside (pumpkin, melon,
 * ...) without taking more room.
 *
 * <p>Icons are {@link IconPainter}s, so a vanilla item ({@link ItemIcon}, drawn sharp at the
 * widget's size), a vector glyph or any draw lambda mix freely; {@link #items} is the shortcut for
 * a list of stacks. The cycle is configurable in every dimension: {@link #interval} between
 * slides, {@link #duration} and {@link #easing} of the slide, and its {@link #direction}. When it
 * runs is configurable too: {@link #alwaysOn} (the default) cycles continuously, while
 * {@link #runOnlyWhenHovered} keeps the first icon still until the pointer is over the widget
 * (and stops on the icon it reached when the pointer leaves); {@link #paused} is a manual override
 * on top of either. A slide that has started always finishes, so the widget never freezes between
 * two icons.
 *
 * <p>Optionally {@link #roundedTile} clips the icons to the widget's rounded rectangle.
 * Pure display: takes no clicks and narrates nothing.
 */
public class IconCycler extends AbstractWidget {
    private final List<IconPainter> icons = new ArrayList<>();
    private final SlideTransition slide = new SlideTransition().duration(450L).easing(Easing.EASE_OUT_CUBIC);

    private long intervalMs = 2500L;
    private SlideTransition.Direction direction = SlideTransition.Direction.LEFT;
    private boolean onlyWhenHovered;
    private boolean paused;
    private float cornerRadius;
    private int backdrop;

    private int index;
    private int previous;
    /** Time the current icon has been showing while running. */
    private long shownMs;
    private long lastTickMs = -1L;

    public IconCycler(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.active = false;
    }

    /** A cycler over vanilla items, each drawn as a sharp {@link ItemIcon} filling the widget. */
    public static IconCycler items(int x, int y, int size, List<ItemStack> stacks) {
        IconCycler cycler = new IconCycler(x, y, size, size);
        for (ItemStack stack : stacks) {
            cycler.add(ItemIcon.of(stack));
        }
        return cycler;
    }

    /** Appends an icon (any draw lambda, or an {@link ItemIcon}). */
    public IconCycler add(IconPainter icon) {
        this.icons.add(icon);
        return this;
    }

    /** Time each icon stays before the next slides in, in milliseconds. */
    public IconCycler interval(long millis) {
        this.intervalMs = Math.max(0L, millis);
        return this;
    }

    /** Duration of one slide in milliseconds. */
    public IconCycler duration(long millis) {
        this.slide.duration(millis);
        return this;
    }

    public IconCycler easing(Easing easing) {
        this.slide.easing(easing);
        return this;
    }

    /** Which way the icons travel; {@link SlideTransition.Direction#LEFT} brings each new icon in from the right. */
    public IconCycler direction(SlideTransition.Direction direction) {
        this.direction = direction;
        return this;
    }

    /** Cycle continuously (the default). */
    public IconCycler alwaysOn() {
        this.onlyWhenHovered = false;
        return this;
    }

    /** Hold still until the pointer is over the widget; cycle only while it is. */
    public IconCycler runOnlyWhenHovered() {
        this.onlyWhenHovered = true;
        return this;
    }

    /** Manual pause, independent of the hover mode. */
    public IconCycler paused(boolean paused) {
        this.paused = paused;
        return this;
    }

    /** Clips the icons to the widget rectangle with rounded corners; see {@link ItemIcon#drawInTile}. */
    public IconCycler roundedTile(float cornerRadius, int backdrop) {
        this.cornerRadius = cornerRadius;
        this.backdrop = backdrop;
        return this;
    }

    /** Index of the icon currently shown (or sliding in). */
    public int currentIndex() {
        return this.index;
    }

    /** Jumps to an icon with no animation and restarts its interval. */
    public IconCycler select(int icon) {
        if (!this.icons.isEmpty()) {
            this.index = Math.floorMod(icon, this.icons.size());
            this.previous = this.index;
            this.slide.finish();
            this.shownMs = 0L;
        }
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        if (this.icons.isEmpty()) {
            return;
        }
        this.tick();

        boolean clip = this.cornerRadius > 0.0f;
        if (clip) {
            gfx.enableScissor(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight());
        }
        IconPainter outgoing = this.icons.get(this.previous);
        IconPainter incoming = this.icons.get(this.index);
        this.slide.draw(gfx, this.getX(), this.getY(), this.getWidth(), this.getHeight(),
                (g, x, y, w, h) -> outgoing.paint(g, x, y, w, h),
                (g, x, y, w, h) -> incoming.paint(g, x, y, w, h));
        if (clip) {
            gfx.disableScissor();
            Ui.cornerCut(gfx, this.getX(), this.getY(), this.getWidth(), this.getHeight(),
                    this.cornerRadius, this.backdrop);
        }
    }

    /** Advances the interval clock and starts the next slide when it is due. */
    private void tick() {
        long now = EasedProgress.nowMs();
        // Capped: a widget that was not drawn for a while must not fire a slide the instant it returns.
        long dt = this.lastTickMs < 0L ? 0L : Math.min(now - this.lastTickMs, 100L);
        this.lastTickMs = now;
        if (this.icons.size() < 2 || this.slide.isRunning()) {
            return;
        }
        boolean running = !this.paused && (!this.onlyWhenHovered || this.isHovered());
        if (!running) {
            return;
        }
        this.shownMs += dt;
        if (this.shownMs >= this.intervalMs) {
            this.shownMs = 0L;
            this.previous = this.index;
            this.index = (this.index + 1) % this.icons.size();
            this.slide.start(this.direction, now);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Pure display; nothing to narrate.
    }
}
