# Modern GUI

A client-side widget library for Fabric mods on Minecraft 26.2. Includes rounded panels,
buttons, scrolling lists, popups and Inter text rendering. Requires Java 25 and Fabric API.

LGPL-3.0-or-later. Bundled Inter and Lucide license notices are included.

## Build from a fresh checkout

Use JDK 25 and the included Gradle wrapper. Select the same Minecraft branch in both
[Modern GUI](https://github.com/Fayberr/modern-gui) and
[Modern Config](https://github.com/Fayberr/modern-config): `fabric/26.1.x`, `fabric/26.2` or `fabric/26.3`.

1. In Modern GUI, run `./gradlew build publishToMavenLocal`.
2. In Modern Config, run `./gradlew test build publishToMavenLocal`.

On Windows, use `gradlew.bat`. Artifacts are written to `build/libs`. The 26.2 and 26.3
Maven coordinates include the Minecraft version suffix; see `gradle.properties` and
the `publishing` block in `build.gradle`. The jar's mod version has no Minecraft suffix.
