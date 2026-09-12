package net.fayber.moderngui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fayber.moderngui.ModernGuiClient;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The custom GUI render pipeline behind every rounded shape in Modern GUI.
 *
 * <p>Vanilla's GUI can only fill axis-aligned rectangles, which is why "rounded" corners built out
 * of stacked fill bands look like staircases. This pipeline is vanilla's textured GUI pipeline
 * with one thing swapped: a fragment shader that computes circular coverage from the UVs and
 * anti-aliases it with {@code fwidth}, so corners are smooth at any GUI scale and any radius.
 *
 * <p>Everything except the fragment shader comes from {@link RenderPipelines#GUI_TEXTURED_SNIPPET}
 * at runtime rather than hard-coded, so blend mode, depth state, vertex format and bind group
 * layout automatically track whatever the current Minecraft version does. Blaze3D compiles
 * pipelines lazily on first use ({@code GlDevice#getOrCompilePipeline}), so no registration is
 * needed; the shader source is loaded from this mod's assets by the normal resource manager.
 *
 * <p>Construction is lazy and failure-tolerant: if anything about the vanilla pipeline changes in a
 * way this cannot mirror, {@link #roundCorner()} returns null and {@link Ui} falls back to drawing
 * corners as pixel spans. A screen that looks slightly harder is better than a crash.
 */
public final class GuiPipelines {
    private static final Logger LOGGER = ModernGuiClient.LOGGER;

    /** Flat white 4x4 texture: the shader samples it so the declared sampler is never optimised out. */
    public static final Identifier WHITE = Identifier.fromNamespaceAndPath(ModernGuiClient.MOD_ID, "textures/gui/white.png");

    @Nullable
    private static RenderPipeline roundCorner;
    private static boolean built;

    private GuiPipelines() {
    }

    /** The anti-aliased corner pipeline, or null if it could not be assembled on this version. */
    @Nullable
    public static RenderPipeline roundCorner() {
        if (!built) {
            built = true;
            try {
                roundCorner = buildRoundCorner();
            } catch (Throwable t) {
                LOGGER.error("Could not build the rounded-corner pipeline; falling back to plain corners", t);
                roundCorner = null;
            }
        }
        return roundCorner;
    }

    private static RenderPipeline buildRoundCorner() {
        // GUI_TEXTURED_SNIPPET already carries vertex shader, vertex format, bind group layout
        // (samplers/uniforms), blend/depth state, polygon mode and cull from the vanilla textured
        // GUI pipeline; passing it to builder() seeds all of that so only the location and the
        // fragment shader need to be overridden.
        return RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(ModernGuiClient.MOD_ID, "pipeline/round_corner"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(ModernGuiClient.MOD_ID, "core/round_corner"))
                .build();
    }
}
