# Modern GUI

> Work in progress: this library is still in development. Expect API changes and unfinished widgets.

A client-side UI library for Fabric mods on Minecraft 26.3. It provides buttons,
rounded panels, scrolling card lists, popups and text rendering with the Inter font.

## Installation

Install Modern GUI and Fabric API when another mod requires them. The command
`/moderngui` opens the included widget showcase.

## Development

The local Maven coordinate for this branch is
`net.fayber:modern-gui:1.0.14+26.3`. The Minecraft suffix keeps it separate from
builds for other game versions. Consumers should declare the library as a Fabric
dependency rather than copying its classes.

Public APIs live under `net.fayber.moderngui`. The widget showcase provides usage
examples. For the standalone preview, run `./gradlew runClient -PmgPreview=true`
with a display. On a headless machine, prefix the command with `xvfb-run -a`.

## License and credits

LGPL-3.0-or-later. Modern GUI includes Inter and Lucide assets with their license
notices. See the license files included in this repository and the built jar.

## Build from a fresh checkout

Use JDK 25 and the included Gradle wrapper. Run `./gradlew build`; the jars are
written to `build/libs/`. Use `./gradlew publishToMavenLocal` to make the
library available to neighboring mod projects. Select the same Minecraft branch in both
[Modern GUI](https://github.com/Fayberr/modern-gui) and
[Modern Config](https://github.com/Fayberr/modern-config): `fabric/26.1.x`, `fabric/26.2` or `fabric/26.3`.

1. In Modern GUI, run `./gradlew build publishToMavenLocal`.
2. In Modern Config, run `./gradlew test build publishToMavenLocal`.

On Windows, use `gradlew.bat`. Artifacts are written to `build/libs`. The 26.2 and 26.3
Maven coordinates include the Minecraft version suffix; see `gradle.properties` and
the `publishing` block in `build.gradle`. The jar's mod version has no Minecraft suffix.
