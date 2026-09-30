# NeoForge 1.21.1 porting status

Version: `1.1.0-neoforge.1`

This release is a functional and buildable compatibility layer, not a complete
replica of the Forge 1.12.2 JAR's behavior.

## Implemented

- Java 21, Minecraft 1.21.1, and NeoForge 21.1.242 environment;
- GeckoLib 4.9.2 dependency;
- all 11 principal IDs: `jenny`, `ellie`, `slime`, `bia`, `bee`, `luna`,
  `allie`, `kobold`, `goblin`, `galath`, and `manglelie`;
- GeckoLib rendering with the original models, textures, and animations;
- spawn eggs for all 11 entities;
- self-contained Survival progression: Jenny, Ellie, Bia, Luna, Slime,
  Goblin, Bee, and Manglelie have rare natural spawns with a local population
  cap; five modern 1.21.1 shelters containing their respective companion are
  connected to a rare Overworld structure, while legacy NBT templates remain
  preserved only for compatibility; Allie's Lamp, Galath's Coin, Tribe Egg,
  Kobold Egg, and Luna's Fishing Rod have Survival recipes; dungeons,
  mineshafts, woodland mansions, bastions, desert pyramids, End Cities, and
  Ancient Cities provide rare loot shortcuts ranging from 0.5% to 3%;
- a persistent cross-dimensional ownership index with a limit of eight
  companions per owner, one of each kind and up to four Kobolds; incubating
  eggs reserve a slot, hatching transfers that reservation, and death or
  dismissal releases it;
- baseline AI, movement, persistence, player bonding, and five operating
  modes: wander, follow, stay, guard, and return home;
- a 29-scene catalog and server-authoritative state machine with GeckoLib
  transitions and Survival access through the combined panel; intros and
  endings advance automatically, loops accept slow/fast/finish controls with
  debounce and safety timeouts, and stop/disconnect/unload restores the player
  without persisting active sessions;
- a unified graphical panel opened with the wand or by sneak-interacting with
  an empty hand: scene selection/start/stop, phase state, pacing controls,
  validated outfits, variant, scale, AI mode, home, and equipment; every action
  is revalidated server-side for ownership and distance;
- scene environmental requirements with a 12-block privacy radius and a bed
  within four blocks for `JENNY_DOGGY`, `SLIME_DOGGY`, and `MANGLELIE_BED`;
  localized phase feedback and dialogue, server-emitted sound cues, moderate
  rewards once per Minecraft day, and the unchanged special `ALLIE_RICH`
  limit;
- the partner embedded in scene models replaces the vanilla player in first
  and third person, avoiding duplicate bodies and hands; the embedded rig uses
  the player's actual skin and the correct wide or slim arm geometry, while a
  temporary clipped camera makes first-person scenes visible;
- corrected models and icons for `allies_lamp`, `dragon_staff`, `tribe_egg`,
  `galath_coin`, `npc_editor_wand`, `kobold_egg_item`, `winchester`, and
  `luna_rod`;
- English and Italian tooltips explaining every special item's use;
- special-item behavior: the lamp summons Allie up to three times; the staff
  selects and applies follow, stay, guard, return-home, and wander orders to
  nearby Kobolds; the Tribe Egg creates four randomly varied Kobolds; the coin
  summons or dismisses Galath; the Kobold Egg places a persistent animated
  entity that hatches after 12,000 ticks and transfers its owner and variant;
  Luna's rod works as a fishing rod; the Winchester remains the non-firing
  scene prop it was in the original JAR;
- a graphical companion inventory with two hand slots, four validated armor
  slots, one persistent storage slot, player inventory and hotbar, quick move,
  and server-side distance/owner validation; it opens through empty-hand
  sneak-interaction and drops its contents exactly once on death; equipment is
  synchronized to vanilla slots, so armor, weapons, modifiers, and
  enchantments function even though vanilla held-item and worn-armor layers
  are not yet rendered on the GeckoLib model;
- server-authoritative Survival balance: unowned companions require a valuable
  gift, gifts cannot steal another player's bond, scenes require the owner or
  an operator, the `ALLIE_RICH` reward is reduced and unique per player, and
  coin-summoned Galath has 60 health, 7 damage, 4 armor, no XP reward, and a
  10-second coin cooldown;
- the `sexmod:horny_potion` effect and potion with the original duration and
  nausea, plus an Awkward Potion brewing recipe using any of the nine 1.21
  variants represented by the old `red_flower` block;
- melee combat, owner defense, flight for supported companions, and Bee/Galath
  ranged attacks through `sexmod:energy_ball`; the projectile deals 4 damage,
  cannot ignite entities or blocks, and has a 60-tick cooldown;
- compatibility registration for all 16 original secondary IDs:
  `kobold_egg`, `custom_model`, the ten `player_*` IDs, `friendly_slime`,
  `luna_hook`, `energy_ball`, and `pyrocinical`. `kobold_egg` supports
  incubation and migration of `tribeID`, `egg_color`, and `eggAge`; scene
  proxies are inert and IDs without a safe equivalent use validated fallbacks;
- 719 sound events and 719 valid OGG files. The source file
  `girls/bia/breath/breath3.ogg` was empty and was replaced with a valid copy
  of `breath2.ogg`;
- conversion of all 15 `.lang` translations to UTF-8 JSON;
- migration of seven NBT structures, four loot tables, and two recipes to the
  data paths used by Minecraft 1.21.1;
- loading verified on a dedicated NeoForge server.

The Winchester model was rebuilt as a vanilla item model. The 1.12.2 JAR
registered the item but did not contain the geo, texture, and animation files
referenced by its renderer.

## Not yet implemented

- 1:1 parity for every original scene timeline. The port provides shared
  server-authoritative dialogue, cues, conditions, controls, and rewards, but
  does not blindly reconstruct every minigame, line, or obfuscated trigger
  from the 1.12.2 JAR;
- player-model transformations. The graphical control panel is operational,
  and `/fapcraft mode`, `/fapcraft home`, `/fapcraft scene`, and
  `/fapcraft stopscene` are available, but not every textual alias or command
  from the original JAR;
- wire compatibility with the approximately 46 payloads used by the 1.12.2
  JAR. The port uses vanilla synchronized state, vanilla menus, and
  server-authoritative actions, so those packets are unnecessary for the
  implemented gameplay and a 1.12.2 client is not compatible;
- exact visual and logical parity for all seven original structures. Five
  modern Survival shelters generate reliably, while the seven legacy NBT
  files (`alex`, `bia`, `ellie`, `goblin`, `jenny`, `luna`, and `ssa`) remain
  as compatibility resources and are not used by world generation;
- exact projectile and flight behavior for every companion;
- automatic migration of complex NBT state from 1.12.2 worlds beyond the
  compatible owner, home, inventory, name, and egg tags already supported;
- 1:1 behavior for `custom_model`, `friendly_slime`, `pyrocinical`, and the ten
  `player_*` IDs. Their IDs are preserved with safe fallbacks, but their old
  implementations are not blindly recreated.

Legacy IDs are no longer discarded as unknown. Test the port on a copy of your
world, because unrecognized complex tags may be normalized by the 1.21.1
fallback implementation.

## Intentional security differences

- the original routine that could create a script, delete JARs from the `mods`
  folder, and terminate Java was not transferred;
- custom-model loading with unvalidated names and dimensions was not
  transferred.

These features require a safe redesign rather than direct transcription of
decompiled code.

## Texture note

Some original textures are 4096×4096. They are preserved without quality loss,
but may consume substantial video memory when many different companions are
visible at the same time.
