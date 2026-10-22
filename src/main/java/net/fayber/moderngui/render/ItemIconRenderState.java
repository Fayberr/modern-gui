package net.fayber.moderngui.render;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * One item icon queued for {@link ItemIconRenderer}: the item's baked render state, the GUI
 * rectangle it fills, and the view rotation. {@code scale} is GUI pixels per model unit, i.e. the
 * side of the box a 1x1x1 model fills, which is the rectangle's width.
 *
 * <p>The rectangle is in the coordinates of {@code pose}, like every other GUI element; the
 * texture the icon is drawn into is sized {@code (x1 - x0) * guiScale} so it maps one texel to
 * one physical pixel as long as the pose does not scale.
 */
record ItemIconRenderState(TrackingItemStackRenderState item, int x0, int y0, int x1, int y1,
                           float scale, float yaw, float pitch, float roll, Matrix3x2f pose,
                           @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
        implements PictureInPictureRenderState {

    /** What the cached texture depends on: anything different here needs a re-render. */
    record Key(Object model, int width, int height, float yaw, float pitch, float roll) {
    }

    static ItemIconRenderState of(TrackingItemStackRenderState item, int x0, int y0, int x1, int y1,
                                  float yaw, float pitch, float roll, Matrix3x2f pose,
                                  @Nullable ScreenRectangle scissor) {
        ScreenRectangle local = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);
        ScreenRectangle bounds = local.transformMaxBounds(pose);
        if (scissor != null) {
            bounds = scissor.intersection(bounds);
        }
        return new ItemIconRenderState(item, x0, y0, x1, y1, x1 - x0, yaw, pitch, roll, pose, scissor, bounds);
    }

    Key key() {
        return new Key(this.item.getModelIdentity(), this.x1 - this.x0, this.y1 - this.y0,
                this.yaw, this.pitch, this.roll);
    }
}
