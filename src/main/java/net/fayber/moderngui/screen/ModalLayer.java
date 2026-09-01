package net.fayber.moderngui.screen;

import net.fayber.moderngui.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * What a {@link PopupHost} can host as its modal layer: a full-screen interaction mode that
 * blocks the widgets underneath. {@link Modal} (title, body, buttons) is the plain-text
 * flavour; {@link ColorPickerModal} is the interactive one.
 *
 * <p>While a layer is open the host routes every click, drag, release, key and typed character
 * to it first. Layers must swallow what they do not use, so nothing underneath reacts.
 */
public interface ModalLayer {
    /** A click anywhere on the screen; return true when consumed (a modal always does). */
    boolean handleClick(PopupHost host, MouseButtonEvent event, boolean doubleClick);

    /** A mouse drag (only fires while a button is held); default: not a drag target. */
    default boolean handleDrag(PopupHost host, MouseButtonEvent event, double deltaX, double deltaY) {
        return false;
    }

    /** A mouse release; default: nothing to stop. */
    default boolean handleRelease(PopupHost host, MouseButtonEvent event) {
        return false;
    }

    /** A key press; ESC and ENTER conventions belong to the layer. */
    boolean handleKey(PopupHost host, KeyEvent event);

    /** A typed character; only layers with text fields react, the default consumes nothing. */
    default boolean handleChar(PopupHost host, CharacterEvent event) {
        return false;
    }

    /** Draws over the whole screen, on top of every widget and popup. */
    void extract(GuiGraphicsExtractor gfx, Theme theme, int mouseX, int mouseY, float partialTick);
}
