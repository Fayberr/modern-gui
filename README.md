# Modern GUI

A catalogue of generic, configurable GUI building blocks for Fabric mods (Minecraft 26.1.x,
Java 25): dark rounded cards, anti-aliased shapes drawn at physical pixel resolution, crisp text in
any font and a set of widgets. Vanilla and Fabric API only, zero mixins. LGPL-3.0-or-later.

Depend on it as `net.fayber:modern-gui` (from `publishToMavenLocal`), or bundle it with Loom's
jar-in-jar. `/moderngui showcase` opens every block in game.

## Fonts

Text goes through a `FontFamily`. Inter is the default; any TTF/OTF family can be registered by the
consuming mod and made the default, or passed to individual calls.

```java
// In onInitializeClient. The TTFs are ordinary resources: assets/mymod/font/fancy_regular.ttf, ...
FontFamily fancy = FontFamilies.register(
        Identifier.fromNamespaceAndPath("mymod", "fancy"),
        Identifier.fromNamespaceAndPath("mymod", "fancy_regular.ttf"),
        Identifier.fromNamespaceAndPath("mymod", "fancy_bold.ttf"));

ModernGui.setDefaultFont(fancy);        // Ui.ui / Ui.uiBold / SizedText now use it everywhere
Ui.styled("Hello", fancy, FontWeight.MEDIUM);                          // or per call
SizedText.draw(gfx, text, x, y, 1.5f, FontWeight.BOLD, fancy, color);  // sized text, per call
```

Optional weights: `FontFamily.builder(id).regular(..).bold(..).medium(..).semibold(..)`; a missing
optional weight falls back to the nearest required one. `Builder.sizes(...)`, `maxScale(...)` and
`baseline(...)` tune the generated fonts (below).

**Crispness.** Minecraft rasterises a TTF glyph at `round(size * oversample)` pixels and samples it
NEAREST, so a font is only sharp when `oversample` equals the GUI scale. A family therefore needs
one font per GUI scale, weight and text size. Nobody writes those by hand: on registration Modern
GUI generates a TTF provider with `size = 9 * factor` and `oversample = guiScale` for every
combination (default sizes 75, 100, 125, 150 and 200 percent, scales 1 to 6, so 60 definitions for
two weights) and loads them as a built-in resource pack. Glyphs a TTF lacks fall back to vanilla's
default and unifont providers, as before.

**How the pack gets in without a mixin.** Fabric API has no in-memory resource pack. The jsons are
written to `<game dir>/moderngui/generated/` at startup (rewritten each launch, safe to delete) and
a built-in pack is registered through `ResourceLoader.registerBuiltinPack` with a thin wrapper
around Modern GUI's own `ModContainer` whose root is that directory. Register families during client
init, before the packs are discovered; a family added later appears after the next resource reload
(`FontFamilies.reloadResources()`). Each generated font opens its own copy of the TTF, so a family
costs `weights x sizes x scales` copies of the file in native memory; trim with `sizes(...)` and
`maxScale(...)`. Screens bake the font into the components they build (`Ui.ui(...)`), so rebuild
them (`rebuildWidgets()`) after `setDefaultFont`.

**Vertical metrics.** Vanilla pins every font's baseline 7 GUI pixels below the draw position. The
baseline of a registered family is measured from its regular TTF's cap height so capitals centre
where Inter's do, and applied as the provider `shift`, so text does not jump when the family is
switched. Override with `Builder.baseline(px)` (7 is Inter's; raise it if text looks too high).

**Fonts chosen by players.** TTF/OTF files in `config/moderngui/fonts/` are registered at startup
as families `moderngui:user/<name>` (`Name-Regular.ttf` plus `Name-Bold.ttf` form one family; a
single file is used for both weights). `FontFamilies.userFonts()` lists them for a picker.

Bundled: Inter and Inter Display (SIL OFL 1.1, see `INTER-OFL.txt`).

## Sized text, icons and motion

| Block | Use |
| --- | --- |
| `SizedText` | `SizedText.draw(gfx, text, x, y, 1.5f, true, color)`: glyphs rasterised at 0.75x to 2x at the real pixel size; `width`, `lineHeight`, `wrap` and `ellipsize` agree with it |
| `ItemIcon` | `ItemIcon.of(stack).size(40).rotation(-20, 10, 0).draw(gfx, x, y)`: item models rendered into a texture of their own physical size (sharp 3D blocks); `drawInTile(...)` crops to a rounded tile |
| `IconCycler` | `IconCycler.items(x, y, 48, stacks).interval(2500).runOnlyWhenHovered()`: slides one icon out and the next in |
| `ScrollState` | `state.setSizes(contentH, viewH); state.wheel(delta); state.update(); offset = state.snappedOffset(scale)`: momentum or eased scroll, clamping, scrollbar geometry |
| `ScrollView` | `new ScrollView(x, y, w, h, contentH, (gfx, px, py, pw, mx, my) -> ...)`: clipped, smoothly scrolling custom-drawn content with a slim scrollbar |
| `EasedProgress`, `Easing` | `anim.animateTo(1f); float v = anim.update();`: time-based eased 0..1 with the usual curves |
| `PillToggle` | `.animation(300, Easing.EASE_OUT_BACK)` and `knobProgress()`: the sliding knob uses `EasedProgress` |
| `SlideTransition`, `SlideSwitcher` | `slide.start(Direction.LEFT)` then `slide.draw(gfx, x, y, w, h, outgoing, incoming)`: old content slides out, new slides in, clipped |

Items are drawn by a picture-in-picture renderer registered through Fabric API
(`PictureInPictureRendererRegistry`); queueing its element reads two private fields of
`GuiGraphicsExtractor` by reflection, and falls back to magnified vanilla drawing if they are ever
unavailable.

## Dev

`./gradlew runClient -PmgPreview=true` opens the showcase; add `-PmgPreviewShot=/tmp/a.png`
`-PmgPreviewTab=3` `-PmgPreviewFont=inter_display` to save a screenshot of a tab and quit.
`./gradlew clean build --offline` is the gate (includes the unit tests).

## Build from a fresh checkout

Use JDK 25 and the included Gradle wrapper. Select the same Minecraft branch in both
[Modern GUI](https://github.com/Fayberr/modern-gui) and
[Modern Config](https://github.com/Fayberr/modern-config): `fabric/26.1.x`, `fabric/26.2` or `fabric/26.3`.

1. In Modern GUI, run `./gradlew build publishToMavenLocal`.
2. In Modern Config, run `./gradlew test build publishToMavenLocal`.

On Windows, use `gradlew.bat`. Artifacts are written to `build/libs`. The 26.2 and 26.3
Maven coordinates include the Minecraft version suffix; see `gradle.properties` and
the `publishing` block in `build.gradle`. The jar's mod version has no Minecraft suffix.
