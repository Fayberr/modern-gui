# Fayber GUI - Modrinth page copy

Source of truth for the text on https://modrinth.com/mod/fayber-gui.
Update here first, then run:

    modrinth copy fayber-gui modrinth-page.md

The `## description` and `## body` sections are what gets uploaded.
The `#` title line and the parenthetical note below are local only.
Voice: short, concrete, no config-key dumps, no "and more". No em-dashes.

## description (short summary line)

A widget library for Minecraft mods: dark rounded cards, anti-aliased shapes and a bundled Inter font.

## body (full page)

# Fayber GUI

A widget library that gives Minecraft mods one shared look: dark rounded cards, shapes drawn at physical pixel resolution, and a bundled Inter font. Players usually meet it as a dependency of mods that use it. On its own it adds one command, `/faybergui`, which opens a showcase screen with every widget in the catalog.

## Rendering

- Rounded rectangles, pills, circles and shadows are anti-aliased at physical pixel resolution, so edges stay crisp at every GUI scale.
- Inter is bundled per GUI scale, with a bold variant for labels and active states.
- A single Theme palette drives every widget; hover states are derived from it.

## Widgets

- Buttons, pill toggles, checkboxes, radio groups and sliders.
- Dropdowns, text and number fields, search fields, multi-line text areas and keybind capture fields.
- Tabs, cards, scrolling lists with momentum wheel and slim scrollbars, horizontal scroll panels.
- Modal dialogs, popups and toasts.

## For mod developers

- Maven dependency `net.fayber:fayber-gui`. Depend on it directly, or bundle it with Loom's jar-in-jar include, whichever fits.
- Zero mixins; vanilla plus Fabric API only, safe to bundle.
- LGPL-3.0-or-later. Inter is bundled under the SIL Open Font License; icons are derived from Lucide.
