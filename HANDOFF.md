# Handoff: sexmod 1.12.2 (Forge) -> 1.21.1 (NeoForge) remake

Read this first, then `PORTING_STATUS.md`, `../TEST_CHECKLIST.md`, `../RECOVERED.md`.
(`../` = the folder holding this repo: `C:\Users\Jeffy Eptien\Documents\modproject\`.)

## Goal
Remake the private Forge 1.12.2 mod `brappcraft.jar` (package `com.trolmastercard.sexmod`, mod id `sexmod`) for
MC 1.21.1 / NeoForge 21.1.242 / GeckoLib 4.9.2 / Java 21 with **1:1 parity as a player experiences it** (no extras,
no invented content). Not code parity. This repo is the remake. The original is only ever *read*.

## Hard rules
- Never write explicit sexual content. Work on engine, state machines, GUI, rendering, audio/keyframe plumbing only.
- Never run the original jar. Answer "what did the original do" from decompiled sources and bytecode.
- Player-as-girl system is parked until the very end. Kobold tribes need not be bug-for-bug.
- If something might need in-game testing, first confirm another way (bytecode, GameTest). Only list in
  `TEST_CHECKLIST.md` what the game is the ONLY way to verify (camera positions are not one).
- The user's last explicit instruction: finish everything, **do the full second verification pass over every original
  class (mapped to its remake equivalent) LAST**.
- Never recursive-grep all of `modproject` (huge decompiled trees); never `pkill -f gradle`; beware huge single-line
  files (`sounds.json`).

## Where things are
| What | Where |
|---|---|
| Remake repo (git) | `modproject\jurblet` (this folder) |
| Original jar | `..\brappcraft.jar` |
| Decompiles | `..\decompiled_procyon` and `..\decompiled_cfr2` (best), `..\decompiled`, `..\decompiled_cfr`, `..\decompiled_merged` (older), `..\extracted` (jar resources) |
| Undecompilable-method recovery notes | `..\RECOVERED.md` (14 methods fail everywhere: use `javap -c -p`) |
| Early spec/audit reports | `..\specs\*.md` (**often stale or wrong; verify against bytecode**) |
| Game-only test list | `..\TEST_CHECKLIST.md` |
| Claude project docs (claude.ai Project "Minecraft Mod port...") | `remake-notes.md` (decisions, original facts), `audit-log.md` (per-batch history + open list), `user-test-feedback.md`, `later-investigations.md` |

Original class names are obfuscated (`ce` = Manglelie, `am`/`dy`/`ci` = goblin carry, `fp` = scene/animation state enum,
`ck.a` rotation helper, `be.a` look-angle, `gc.*` math, `b6.*` easing). `remake-notes.md` has the key facts;
`javap -c -p -cp <classes dir> com.trolmastercard.sexmod.<class>` is the source of truth. If you need a classes dir,
unpack `brappcraft.jar` (`extracted`) and point `-cp` at it. Procyon often mangles control flow; trust javap.
javap switch pitfall: the real range is in the `tableswitch { // lo to hi` header.

## Build / test
```
cd modproject\jurblet
gradlew.bat compileJava --offline
gradlew.bat runGameTestServer --offline --no-configuration-cache   # expect "All 40 required tests passed"
gradlew.bat runClient                                              # for the checklist
```
(Cloud used `export JAVA_TOOL_OPTIONS=` and `--no-daemon`; on Windows plain `gradlew.bat` is fine.) Some AI GameTests
flake when an arena does not entity-tick (tree felling, Luna, Galath hunt/reflect/ambush/adopt, go-home pearl): rerun.
GameTest notes: use `forceArena(helper)` + `floor`/`skyFloor`; arenas ~6 blocks apart; y above ~190 does not tick;
FakePlayers are not in the PlayerList so scenes end next tick. `python tools/audit_resources.py` must report 0 problems
after touching resources.
Commit trailers used so far: `Co-Authored-By: Claude ...` and `Claude-Session: ...` (optional going forward).

## State at handoff (2026-10-02)
- Builds, 40/40 GameTests. Parity estimate roughly 88 % (+/- 8). Git history on the device is a few squashed
  commits (cloud per-batch commits were squashed when syncing), so `git log` there is coarse.
- Architecture in one paragraph: `entity/CompanionEntity` is the single base for all 11 kinds (large; a split is a
  deferred nicety), behaviour classes per kind (`GoblinBehavior`, Kobold tribe AI, Galath boss/tamed AI, ...);
  `CompanionScene` + per-scene keyframe JSON drive scenes server-side (phases, transitions, controls, camera
  `view` events); `client/CompanionModel` (GeckoLib model + per-kind bone rules `goblinPose`/`galathPose`/...),
  `client/CompanionRenderer` (layers, procedural meshes, carried goblin, name-tag hiding), `client/ClientHooks`,
  `ManglelieRide`, `GalathMeshLayer`, `ClientGameplayEvents` (boyCam, flight HUD).
- Recently finished (last session): Galath corrupt first-person alpha, Kobold payment chat, Manglelie riding pose
  on Galath's head (seat/arm IK/eased head look), name tags hidden in flagged states, first-person boyCam for
  flight/boost, goblin carry (camera-attached hold, shoulder placement + sway, throw hide rules and tumble).

## Open work (in priority order)
1. Goblin: the `steve` bone (player skin model) should use the carrier's skin during PICK_UP/THROWING (extend the
   `steve` render-type hook in `CompanionRenderer.renderRecursively`, which currently requires `scene.hasPartner()`);
   finish original `ci.a(em,bone)` sit-look and `d(em)` details.
2. Galath: `usesBoyCam` for GALATH_SUMMON (15 ticks); wild paralysis message repeat; catch exclusions; recall/binding/
   menu/edit-hint/morning dosage checks; `cb` renderer details; relog ride re-attach.
3. Baby slime custom model (`c_`): 4 extra tail boxes behind the body (z 3.0-4.05, uv (10,11), sizes 2x2x1, 3x1x1,
   5x1x1, 7x2x1; scale 0.999; outer vanilla layer).
4. `aq` morning scene: camera 500 ms (10-tick) delay and teleport to the bed position.
5. Re-verify audit areas not yet re-read: `specs` render/gui/misc/pkgC, msb/goblin/kobold leftovers; unaudited
   Jenny/Ellie/Allie/e2 interaction flows; Ellie eye height; item-handler capability; lamp chest loot weights
   (unverifiable); Kobold approach lerp (40 ticks) and `hardReady` restart (accepted deviations).
6. Optional/known deferred: age-gate screen, `/reloadcustommodels`, wand editor (custom-model system, parked).
7. Add GameTest coverage for the goblin carry rendering if feasible (client-only, so probably checklist).
8. **LAST: full second verification pass**, every original class vs its remake equivalent (list classes from the
   jar; tick them off in `audit-log.md`). Then player-as-girl (parked), then extras cleanup after user testing.
9. At the very end ask the user about the Kobold day/night modulo decision (original has no modulo; remake keeps one).

## Working style that has worked
- Batch: read original bytecode -> port -> compile -> GameTest -> commit -> update `audit-log.md` -> add only
  game-only items to `TEST_CHECKLIST.md`. The user tests in bulk and reports back into `user-test-feedback.md`.
- Audit reports can be wrong: always verify a claim in bytecode before changing behaviour.
- GeckoLib 4.9.2 gotchas: `bone.setTrackingMatrices(true)`; `getWorldPosition()` ignores the entity yaw (the
  remake corrects with a hand rotation of `180 - yBodyRot`); a poseStack translate before `super.render` is
  cancelled out of bone world positions.
