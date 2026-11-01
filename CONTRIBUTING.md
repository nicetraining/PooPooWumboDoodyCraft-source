# Contributing

Contributions should target Minecraft 1.21.1, NeoForge 21.1.242, Java 21, and GeckoLib 4.9.3 (bundled through jarJar).

Before opening a pull request:

1. Build with `./gradlew --no-daemon clean build` (Linux/macOS) or `gradlew.bat --no-daemon clean build` (Windows).
2. Keep scene transitions and gameplay decisions server-authoritative.
3. Do not add destructive routines, arbitrary model loading, secrets, world saves, build caches, or generated runtime files.
4. Do not submit assets copied from another project unless you have documented permission to redistribute them.
5. Do not submit illegal, non-consensual, exploitative, or underage sexual content.
6. Explain gameplay, compatibility, persistence, and migration effects in the pull request.

Submitting a contribution does not grant the maintainers rights to third-party material that the contributor did not have authority to license.
