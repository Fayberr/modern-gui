package net.fayber.faybergui.widget;

import net.minecraft.client.gui.components.AbstractWidget;

import java.util.List;

/**
 * Static layout helpers over plain {@link AbstractWidget}s: rows, columns and grids that set
 * positions and nothing else. There is no layout object to keep alive - the methods run once,
 * mutate the widgets' x/y, and return; re-running them after a resize is the caller's job.
 *
 * <p>Because these only touch positions, they compose with every widget in this toolkit (and
 * with vanilla's): lay a row of {@link FlatButton}s out, put the result in a {@link HFlow} or a
 * {@link HorizontalScrollPanel}, whatever the screen needs.
 *
 * <p>Widgets keep their own size; the helpers never resize anything. Spacing is the distance
 * between the widgets, not padding around the group.
 *
 * <p>Usage example:
 * <pre>{@code
 * List<AbstractWidget> buttons = List.of(
 *         FlatButton.builder(Component.literal("Cancel"), 0, 0, 80, onCancel),
 *         FlatButton.builder(Component.literal("OK"), 0, 0, 80, onOk).theme(...));
 *
 * // Left to right, right-aligned inside a 300px-wide footer at y = 200:
 * Layouts.row(buttons, 0, 200, 300, Layouts.Alignment.RIGHT, 8);
 *
 * // Top to bottom under the row:
 * Layouts.column(fields, 0, 240, 6);
 *
 * // A 3-wide grid of equal cells, each widget centred in its cell:
 * Layouts.grid(chips, 0, 300, 3, 90, 30, 8, 8, Layouts.Alignment.CENTER);
 * }</pre>
 */
public final class Layouts {
    /** How a group of widgets sits inside a larger area (see {@link #row}). */
    public enum Alignment {
        /** Flush against the start (left edge in a horizontal layout). */
        LEFT,
        /** Centred in the area. */
        CENTER,
        /** Flush against the end (right edge in a horizontal layout). */
        RIGHT
    }

    private Layouts() {
    }

    // ------------------------------------------------------------------- rows

    /**
     * Lays the widgets out left to right at a fixed y, preserving each widget's own width and
     * height. The first widget's left edge lands on {@code x}; each next widget starts
     * {@code gap} pixels after the previous one's right edge.
     *
     * @param widgets the widgets to position, in left-to-right order
     * @param x       left edge of the first widget
     * @param y       y for every widget
     * @param gap     horizontal distance between adjacent widgets
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
     * {@code totalWidth}-wide area starting at {@code x}: the occupied width (all widgets plus
     * their gaps) sits flush left, centred, or flush right inside that area.
     *
     * <p>Use this to align a button row to the right edge of a panel regardless of how wide the
     * buttons end up being.
     *
     * @param widgets    the widgets to position, in left-to-right order
     * @param x          left edge of the alignment area
     * @param y          y for every widget
     * @param totalWidth width of the alignment area
     * @param align      where the group sits inside the area
     * @param gap        horizontal distance between adjacent widgets
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

    // ---------------------------------------------------------------- columns

    /**
     * Lays the widgets out top to bottom at a fixed x, preserving each widget's own width and
     * height. The first widget's top edge lands on {@code y}; each next widget starts
     * {@code gap} pixels below the previous one's bottom edge.
     *
     * @param widgets the widgets to position, in top-to-bottom order
     * @param x       x for every widget
     * @param y       top edge of the first widget
     * @param gap     vertical distance between adjacent widgets
     */
    public static void column(List<AbstractWidget> widgets, int x, int y, int gap) {
        int cursor = y;
        for (AbstractWidget w : widgets) {
            w.setPosition(x, cursor);
            cursor += w.getHeight() + gap;
        }
    }

    // ------------------------------------------------------------------ grids

    /**
     * Lays the widgets out in a grid of fixed-size cells, row-major (left to right, then down).
     * Each widget is centred in its cell unless {@code align} says otherwise; the widget keeps
     * its own size, so a widget wider than the cell overflows symmetrically around the align
     * edge.
     *
     * @param widgets    the widgets to position
     * @param x          left edge of the grid
     * @param y          top edge of the grid
     * @param columns    number of columns per row
     * @param cellWidth  width of one cell
     * @param cellHeight height of one cell
     * @param gapX       horizontal distance between cells
     * @param gapY       vertical distance between cells
     * @param align      where each widget sits inside its cell
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

    // ---------------------------------------------------------------- metrics

    /**
     * The width the widgets would occupy when laid out in a row with this gap: the sum of their
     * widths plus one gap between each pair. Use it to size a container before calling
     * {@link #row(List, int, int, int)}.
     *
     * @return the occupied width, or 0 for an empty list
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

    /**
     * The height the widgets would occupy when laid out in a column with this gap: the sum of
     * their heights plus one gap between each pair.
     *
     * @return the occupied height, or 0 for an empty list
     */
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
