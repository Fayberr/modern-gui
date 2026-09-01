package net.fayber.faybergui.widget;

import net.minecraft.client.gui.components.AbstractWidget;

import java.util.List;

/**
 * Static layout helpers over plain {@link AbstractWidget}s: rows, columns and grids that set
 * positions and nothing else. There's no layout object to keep alive; the methods run once,
 * mutate the widgets' x/y, and return, so re-running them after a resize is the caller's job.
 *
 * <p>Widgets keep their own size, the helpers never resize anything. Spacing is the distance
 * between the widgets, not padding around the group.
 *
 * <pre>{@code
 * // Left to right, right-aligned inside a 300px-wide footer at y = 200:
 * Layouts.row(buttons, 0, 200, 300, Layouts.Alignment.RIGHT, 8);
 *
 * // A 3-wide grid of equal cells, each widget centred in its cell:
 * Layouts.grid(chips, 0, 300, 3, 90, 30, 8, 8, Layouts.Alignment.CENTER);
 * }</pre>
 */
public final class Layouts {
    /** How a group of widgets sits inside a larger area (see {@link #row}). */
    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }

    private Layouts() {
    }

    /**
     * Lays the widgets out left to right at a fixed y, preserving each widget's own width and
     * height. The first widget's left edge lands on {@code x}; each next widget starts
     * {@code gap} pixels after the previous one's right edge.
     */
    public static void row(List<AbstractWidget> widgets, int x, int y, int gap) {
        int cursor = x;
        for (AbstractWidget w : widgets) {
            w.setPosition(cursor, y);
            cursor += w.getWidth() + gap;
        }
    }

    /**
     * Same as {@link #row(List, int, int, int)}, but the group as a whole is aligned inside a
     * {@code totalWidth}-wide area starting at {@code x}: the occupied width sits flush left,
     * centred, or flush right inside that area. Use this to align a button row to the right edge
     * of a panel regardless of how wide the buttons end up being.
     */
    public static void row(List<AbstractWidget> widgets, int x, int y, int totalWidth,
                           Alignment align, int gap) {
        int occupied = totalWidth(widgets, gap);
        int start = switch (align) {
            case LEFT -> x;
            case CENTER -> x + (totalWidth - occupied) / 2;
            case RIGHT -> x + totalWidth - occupied;
        };
        row(widgets, start, y, gap);
    }

    /**
     * Lays the widgets out top to bottom at a fixed x, preserving each widget's own width and
     * height. The first widget's top edge lands on {@code y}; each next widget starts
     * {@code gap} pixels below the previous one's bottom edge.
     */
    public static void column(List<AbstractWidget> widgets, int x, int y, int gap) {
        int cursor = y;
        for (AbstractWidget w : widgets) {
            w.setPosition(x, cursor);
            cursor += w.getHeight() + gap;
        }
    }

    /**
     * Lays the widgets out in a grid of fixed-size cells, row-major (left to right, then down).
     * Each widget is centred in its cell unless {@code align} says otherwise; a widget wider than
     * the cell overflows symmetrically around the align edge.
     */
    public static void grid(List<AbstractWidget> widgets, int x, int y, int columns,
                            int cellWidth, int cellHeight, int gapX, int gapY, Alignment align) {
        for (int i = 0; i < widgets.size(); i++) {
            AbstractWidget w = widgets.get(i);
            int col = i % columns;
            int row = i / columns;
            int cellX = x + col * (cellWidth + gapX);
            int cellY = y + row * (cellHeight + gapY);
            int wx = switch (align) {
                case LEFT -> cellX;
                case CENTER -> cellX + (cellWidth - w.getWidth()) / 2;
                case RIGHT -> cellX + cellWidth - w.getWidth();
            };
            w.setPosition(wx, cellY + (cellHeight - w.getHeight()) / 2);
        }
    }

    /**
     * The width the widgets would occupy when laid out in a row with this gap. Use it to size a
     * container before calling {@link #row(List, int, int, int)}.
     */
    public static int totalWidth(List<AbstractWidget> widgets, int gap) {
        if (widgets.isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (AbstractWidget w : widgets) {
            sum += w.getWidth();
        }
        return sum + gap * (widgets.size() - 1);
    }

    /** The height the widgets would occupy when laid out in a column with this gap. */
    public static int totalHeight(List<AbstractWidget> widgets, int gap) {
        if (widgets.isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (AbstractWidget w : widgets) {
            sum += w.getHeight();
        }
        return sum + gap * (widgets.size() - 1);
    }
}
