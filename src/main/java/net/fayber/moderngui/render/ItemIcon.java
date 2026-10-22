package net.fayber.moderngui.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;

/**
 * Draws an {@link ItemStack} at any pixel size so block items render as sharp 3D models rather
 * than a magnified 16px bitmap. Reusable and fluent; one instance can draw many frames.
 *
 * <pre>{@code
 * ItemIcon pumpkin = ItemIcon.of(new ItemStack(Items.CARVED_PUMPKIN)).size(40).rotation(-18, 0, 0);
 * pumpkin.draw(gfx, x, y);                                   // 40x40 box at (x, y)
 * pumpkin.drawInTile(gfx, x, y, 56, 56, 8, theme.card);      // cropped to a rounded tile
 * }</pre>
 *
 * <p>How it works: 26.1 draws GUI items into a fixed {@code 16 * guiScale} atlas slot and blits
 * that, so scaling the matrix stack around {@code gfx.item(...)} only stretches baked pixels.
 * This class instead queues a picture-in-picture element ({@link ItemIconRenderer}) whose
 * texture is the icon's own physical size, and the model is rasterised straight into it. The
 * box therefore must not be scaled by the matrix stack (translate is fine): ask for the size you
 * want with {@link #size}. Edges are device-resolution but not multisampled.
 *
 * <p>If the renderer is unavailable (not registered, or a future game version hides the hooks
 * it needs) the icon falls back to vanilla item drawing scaled up, soft but correct.
 */
public final class ItemIcon implements IconPainter {
    private ItemStack stack;
    private float size;
    private float yaw;
    private float pitch;
    private float roll;
    private float nudgeX;
    private float nudgeY;

    public ItemIcon(ItemStack stack) {
        this.stack = stack;
    }

    public static ItemIcon of(ItemStack stack) {
        return new ItemIcon(stack);
    }

    /** One-shot draw of {@code stack} into a {@code size} box at {@code (x, y)}. */
    public static void draw(GuiGraphicsExtractor gfx, ItemStack stack, float x, float y, float size) {
        new ItemIcon(stack).size(size).draw(gfx, x, y);
    }

    public ItemIcon stack(ItemStack stack) {
        this.stack = stack;
        return this;
    }

    /**
     * Side of the square box the icon fills, in GUI pixels (a normal 16px slot item is 16). Left
     * at 0 the icon fills the largest square that fits the rectangle {@link #paint} gives it.
     */
    public ItemIcon size(float size) {
        this.size = size;
        return this;
    }

    /**
     * Turns the item for a nicer angle, in degrees, applied on top of the usual inventory pose:
     * yaw about the vertical screen axis, pitch about the horizontal one, roll about the view axis.
     * A small yaw/pitch (say -20 and 10) shows a block's top and side differently from the
     * stock isometric view.
     */
    public ItemIcon rotation(float yaw, float pitch, float roll) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        return this;
    }

    /** Shifts the icon inside its box/tile by GUI pixels, e.g. to crop it off-centre in a tile. */
    public ItemIcon offset(float dx, float dy) {
        this.nudgeX = dx;
        this.nudgeY = dy;
        return this;
    }

    /** Draws the icon with its box's top-left at {@code (x, y)}. Needs {@link #size} set. */
    public void draw(GuiGraphicsExtractor gfx, float x, float y) {
        if (this.size <= 0.0f) {
            throw new IllegalStateException("ItemIcon.size(...) must be set before draw(gfx, x, y)");
        }
        this.render(gfx, x + this.nudgeX, y + this.nudgeY, this.size);
    }

    /** Draws centred on {@code (cx, cy)}. */
    public void drawCentered(GuiGraphicsExtractor gfx, float cx, float cy) {
        this.draw(gfx, cx - this.size / 2.0f, cy - this.size / 2.0f);
    }

    /** {@link IconPainter}: centres the icon in the rectangle, at {@link #size} or the largest fit. */
    @Override
    public void paint(GuiGraphicsExtractor gfx, float x, float y, float width, float height) {
        float side = this.size > 0.0f ? this.size : Math.min(width, height);
        this.render(gfx, x + (width - side) / 2.0f + this.nudgeX, y + (height - side) / 2.0f + this.nudgeY, side);
    }

    /**
     * Draws the icon centred in a tile and clips it to the tile with rounded corners, so an icon
     * larger than the tile reads as a cropped, full-bleed picture. The rectangle is clipped with
     * a scissor; the rounded corners are then cut with {@link Ui#cornerCut}, which paints the
     * outside of each arc in {@code backdrop}. That colour must therefore match whatever the tile
     * sits on (there is no erasing in the GUI pipeline).
     */
    public void drawInTile(GuiGraphicsExtractor gfx, float tileX, float tileY, float tileW, float tileH,
                           float cornerRadius, int backdrop) {
        int x0 = Math.round(tileX);
        int y0 = Math.round(tileY);
        int x1 = Math.round(tileX + tileW);
        int y1 = Math.round(tileY + tileH);
        gfx.enableScissor(x0, y0, x1, y1);
        this.paint(gfx, tileX, tileY, tileW, tileH);
        gfx.disableScissor();
        if (cornerRadius > 0.0f) {
            Ui.cornerCut(gfx, tileX, tileY, tileW, tileH, cornerRadius, backdrop);
        }
    }

    private void render(GuiGraphicsExtractor gfx, float x, float y, float side) {
        if (this.stack == null || this.stack.isEmpty() || side < 1.0f) {
            return;
        }
        int px = Math.round(x);
        int py = Math.round(y);
        int s = Math.round(side);
        Minecraft mc = Minecraft.getInstance();
        GuiRenderState renderState = ItemIconRenderer.isRegistered() ? GuiInternals.renderState(gfx) : null;
        if (renderState == null) {
            this.renderFallback(gfx, px, py, s);
            return;
        }
        TrackingItemStackRenderState item = new TrackingItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(item, this.stack, ItemDisplayContext.GUI, mc.level, null, 0);
        if (item.isEmpty()) {
            return;
        }
        ScreenRectangle scissor = GuiInternals.scissor(gfx);
        renderState.addPicturesInPictureState(ItemIconRenderState.of(item, px, py, px + s, py + s,
                this.yaw, this.pitch, this.roll, new Matrix3x2f(gfx.pose()), scissor));
    }

    /** Vanilla item drawing magnified to the box: soft, but works everywhere. */
    private void renderFallback(GuiGraphicsExtractor gfx, int x, int y, int side) {
        gfx.pose().pushMatrix();
        gfx.pose().translate(x, y);
        gfx.pose().scale(side / 16.0f, side / 16.0f);
        gfx.item(this.stack, 0, 0);
        gfx.pose().popMatrix();
    }
}
