# PooPooWumboDoodyCraft — NeoForge 1.21.1

> [!WARNING]
> **ADULT ONLY !!!! 18 PLUS !!!!!!!!**

A port of the Minecraft 1.12.2 Forge mod FapCraft to Minecraft 1.21.1 with NeoForge and
GeckoLib 4.

Download: [nicetraining.github.io](https://nicetraining.github.io/) · Community: [Discord](https://discord.gg/za6UejgY4y) ·
What's new: [CHANGELOG.md](CHANGELOG.md)

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.242 or newer 21.1.x
- Java 21

GeckoLib 4.9.3 is bundled inside the jar; do not install a different GeckoLib next to it.

## Installation

1. Install NeoForge for Minecraft 1.21.1.
2. Download the jar from the [website](https://nicetraining.github.io/) and put it into
   the `mods` folder.
3. Do not keep the old Forge 1.12.2 jar in the same instance; 1.12 worlds are not migrated.

## What is in it

Everything the original had, as the original had it, including:

- the eleven girls (Jenny, Ellie, Bia, Luna, Slime, Bee, Allie, Kobold, Goblin, Galath, Manglelie) with their own AI,
  menus, prices, scenes, dialogue and sounds; Kobold tribes and their work; the goblin lairs; the Galath boss fight;
- the player-as-girl forms (horny potion), custom models / clothing (`sexmod_custom_models`, the girl wand,
  `/setmodelcode`, `/whitelistserver`, `/reloadcustommodels`), `/locatenearestgoblinlair` and `/futa`;
- the original items: Allie's lamp (also found in dungeon, mineshaft and woodland mansion chests), Dragon Staff and
  girl wand (craftable, as in 1.12), tribe egg, Kobold egg, Galath coin, Luna's rod and the Winchester prop;
- the original world generation (the girls' houses and goblin lairs, now as 1.21 structures) and natural spawns
  (Slime, Bee, Manglelie, Pyrocinical).

A PooPooWumboDoodyCraft creative tab holds every item and spawn egg (not in the original, kept for convenience); the lamp, tribe
egg, girl wand and staff are also in Tools & Utilities and the spawn eggs with the other spawn eggs, as in 1.12.

`/fapcraft home|scene|stopscene` is not from the original; it is an operator-only test aid.

## Differences from the original

- The girls' houses and the goblin lairs are 1.21 worldgen structures (`/locate structure sexmod:jenny`, `ellie`,
  `bia`, `luna`, `goblin_lair`; `/place structure` works too). Every goblin lair sits above the deepslate and opens
  onto a cave.
- Quality-of-life fixes: no wall suffocation while a girl walks up to you or after a scene, a free spot beside the scene
  instead of the roof when you end up inside a wall, girls give up on an unreachable bed after a second, goblins no
  longer shove you, and a few first-person views (tamed Galath's anal, Bia's anal start) face the scene.
- Security: the original's routine that could write a script, delete jars from the `mods` folder and stop Java is not
  ported. Custom models are loaded with their names and sizes validated, and files sent by a server are capped at 1 MB.
- 1.12 network packets are replaced by 1.21 payloads; a 1.12 client cannot join, and 1.12 worlds are not migrated.
- Some original textures are 4096×4096. They are kept without quality loss, but use a lot of video memory when many
  different girls are visible at once.

## Building

With Java 21:

```sh
./gradlew build                                         # jar in build/libs
./gradlew runGameTestServer --no-configuration-cache    # automated tests
```

On Windows use `gradlew.bat`.

## First launch

The first start shows an age warning. "helo I am 18 or more year" dismisses it for good (`sexmod/dontAskAgain` in the
game folder). "helo i am not 18 or more year" closes the game and locks the mod (`sexmod/locked`): every later start
only shows a quit button until the mod is removed.

## Credits

By nicetraining: a full rewrite of 1.12.2's FapCraft, which was created by ShnurriTV and later carried on by
Trolmastercard.

## Adult-content notice

This mod contains material intended for adults. Use it only if you meet the
minimum age required by applicable law.
