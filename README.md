# FapCraft — NeoForge 1.21.1 port

> [!WARNING]
> **swag poop masters only.** This project contains awesome.

The staged release JAR and its SHA-256 checksum are available under [`dist/`](dist/). For normal public distribution, prefer attaching these files to a tagged release rather than repeatedly committing binaries.

This project is a compatibility layer reconstructed from the Minecraft 1.12.2
`FapCraft.jar`. It builds and runs on Minecraft 1.21.1 with Java 21, NeoForge,
and GeckoLib 4.

It is not yet a 1:1 conversion of every original feature. Implemented systems
and remaining differences are documented in
[PORTING_STATUS.md](PORTING_STATUS.md).

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.242
- GeckoLib 4.9.2 for NeoForge 1.21.1
- Java 21

## Installation

1. Install NeoForge 21.1.242 for Minecraft 1.21.1.
2. Copy GeckoLib 4.9.2 and `sexmod-1.1.0-neoforge.1.jar` into the `mods` folder.
3. Do not keep the old Forge 1.12.2 JAR in the same instance.
4. Back up your world before testing. Legacy IDs are registered, but complex
   1.12.2 NBT data currently has only partial migration support.

## Survival progression

The mod is self-contained in a new Survival world:

1. Explore plains, forests, meadows, and taigas for rare encounters with Jenny,
   Ellie, Bia, or Luna. Slime and Goblin spawn in wet areas, Bee in flower
   biomes, and Manglelie in the Nether. Wild encounters remain non-persistent
   until recruited, and no more than two can exist in the same 128-block area.
2. Search newly generated Overworld chunks for modern 1.21.1 shelters belonging
   to Jenny, Bia, Ellie, Luna, or Goblin. Their structure set uses 48-chunk
   spacing.
3. Offer gold, an emerald, or a diamond to a wild companion to bond with them.
4. Allie, Galath, and Kobolds do not spawn randomly. Obtain them through
   Allie's Lamp, Galath's Coin, a Kobold Egg, or a Tribe Egg.
5. Special items can be crafted from advanced materials. Recipes use amethyst
   and gold for the egg, four eggs and an emerald block for the tribe, and a
   Nether Star with gold blocks plus an Ender Pearl or Echo Shard for the lamp
   and coin. Luna's Fishing Rod is also craftable.
6. Dungeons and mineshafts may contain a Kobold Egg (3%); woodland mansions and
   bastions a Tribe Egg (1%); desert pyramids and End Cities a lamp (0.75%);
   Ancient Cities and bastions a Galath Coin (0.5%).

Structures appear only in chunks generated after installing the mod. Natural
spawns can also occur in previously explored biomes.

## Special items

- **Allie's Lamp:** right-click to summon Allie; it has three uses.
- **Dragon Staff:** sneak and use the staff to select follow, stay, guard,
  return home, or wander; use it normally to apply that order to nearby
  Kobolds.
- **Tribe Egg:** use it on a block to create four randomly varied Kobolds.
- **Galath's Coin:** right-click to summon or dismiss Galath; every successful
  use applies a 10-second cooldown.
- **Girl Wand:** craft it with one gold ingot and two sticks. Use it on a
  companion you own to open the combined scene, equipment, and appearance
  panel.
- **Companion inventory and controls:** sneak-interact with an empty hand, or
  use the wand, to open the same panel. It provides two hand slots, four armor
  slots, one storage slot, and controls for outfit, variant, scale, AI mode,
  and home position. Equipment slots accept one item and their contents drop
  when the companion dies. Equipped armor, weapons, modifiers, and
  enchantments work, although the current GeckoLib renderer does not display
  vanilla worn armor or held items on the companion model.
- **Kobold Egg:** use it on a block to place a persistent egg. It animates while
  incubating and hatches after 10 minutes into a Kobold bonded to the player;
  the timer advances only while its chunk is loaded.
- **Arousal Potion:** brew an Awkward Potion with a poppy, blue orchid, allium,
  azure bluet, one of the four tulips, or an oxeye daisy. It lasts three
  minutes and also applies the original version's brief nausea effect.
- **Winchester:** a scene prop; the original JAR did not implement a firing
  action either.
- **Luna's Fishing Rod:** works as a normal fishing rod.

## Scenes in Survival

1. Recruit a companion with a gift and remain within eight blocks.
2. Use the wand on the companion, or sneak-interact with an empty hand, select
   a scene with the panel arrows, and press **Start**.
3. Intros advance automatically. During looped phases, use **Slow** or **Fast**;
   **Finish** moves to the ending and **Stop** safely interrupts the scene.
   Every phase has an automatic safety timeout when no input is provided.
4. `JENNY_DOGGY`, `SLIME_DOGGY`, and `MANGLELIE_BED` require a bed within four
   blocks. Every scene requires privacy: no other non-spectator player may be
   within twelve blocks.
5. The server validates distance, ownership, environmental requirements, and
   button debounce. Completion restores the previous position and AI mode.
   The player embedded in the scene model replaces the duplicate vanilla body
   and hands; first-person scenes use a temporary clipped camera view.
6. A completed scene grants a small themed reward at most once per Minecraft
   day for each companion kind. `ALLIE_RICH` retains its special one-time
   reward limit per player.

Transitions display localized phase and dialogue feedback and emit a
server-side sound cue. Active scenes are never persisted: stopping, death,
disconnection, or entity unloading cannot leave the player invisible or
locked in place.

## Survival balance

- Unowned companions require a valuable gift before accepting commands. Gifts
  cannot steal another player's companion. Scenes require the actual owner or
  an operator.
- Galath has 60 health, 7 attack damage, 4 armor, no XP reward, and attacks at
  range every three seconds. Energy balls deal 4 damage and do not ignite
  entities or blocks.
- The `ALLIE_RICH` reward is one diamond and four gold ingots. It can be
  obtained only once per player, in addition to the per-Allie limit.
- Passive out-of-combat regeneration heals one health point every eight
  seconds.
- Each owner may keep at most eight companions: one of each kind, except for a
  maximum of four Kobolds. This server-persistent limit includes incubating
  Kobold eggs. Slots are released on death and cannot be bypassed by unloading
  chunks.

## Commands

Commands accept an entity selector as their target and enforce companion
ownership:

```text
/fapcraft mode <target> <wander|follow|stay|guard|home>
/fapcraft home <target>
/fapcraft scene <target> <scene_name>
/fapcraft stopscene <target>
```

## Building

From PowerShell with Java 21:

```powershell
.\gradlew.bat build
```

The JAR is generated under `build/libs`.

## Adult-content notice

This mod contains material intended for adults. Use it only if you meet the
minimum age required by applicable law.
