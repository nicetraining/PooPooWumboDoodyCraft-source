# FapCraft NeoForge 1.21.1 — 1.1.0-neoforge.1

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.242
- GeckoLib 4.9.2
- Java 21

## Scene stability fixes

- First person now views the scene from the animated eye position of the embedded player proxy, with normal mouse control.
- The camera is kept just in front of the proxy face with a minimum clearance, so the whole player skin stays rendered and uncut while the view remains clear.
- Third person keeps the vanilla orbit camera, which frames the scene and stays out of blocks.
- The partner is kept inside the companion model, where the embedded player proxy is rendered, instead of standing beside it.
- Scenes anchor to the ground and lock companion movement, AI, collisions, and rotation while active.
- Removed internal phase and dialogue placeholders from chat.
- Disabled animation blending between scene phases to avoid floating transition poses.

## Manglelie bed scene

The bed scene referenced two animations that exist only in Galath's file, and the keyframes available on Manglelie's own model were static, so the scene appeared frozen. It now renders with the combined Galath and Manglelie model and Galath's animation file, which contain every bone and phase the animation needs, including a proper intro and finale. The scene is also anchored onto the mattress of the nearby bed and aligned with the bed axis, because the pose is authored as already lying on it.

## Afterglow effects

Completing a partner scene now applies a short set of vanilla effects: hunger, slowness II, weakness and a brief levitation. Slow falling is applied alongside levitation and outlasts it, so the effect cannot cause fall damage or inventory loss in survival.

## Highlights

- Eleven principal companions with GeckoLib models and animations.
- Survival recruitment, ownership limits, natural encounters, structures, recipes, and rare loot.
- Companion inventory, equipment, AI modes, melee/ranged combat, incubation, brewing, and special items.
- Twenty-nine server-authoritative scenes with graphical controls, environmental checks, rewards, player-skin rendering, and first/third-person camera handling.
- English and Italian UI additions plus legacy language resources.

This is a functional modern reconstruction, not a byte-for-byte or behaviorally exact 1.12.2 replica. See `PORTING_STATUS.md` for the current implementation status.
