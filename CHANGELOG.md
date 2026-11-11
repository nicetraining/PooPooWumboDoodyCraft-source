# Changelog

Newest first. The download is on [nicetraining.github.io](https://nicetraining.github.io/) (changelog page: [nicetraining.github.io/changelog.html](https://nicetraining.github.io/changelog.html)). Join the [Discord](https://discord.gg/za6UejgY4y) for news and help.

## 2026-11-11: v1.1.0, NeoJenny: smoother textures and jiggle physics

- Smoother textures: the blocky pixel shading on hair and clothes becomes soft gradients, while faces, outlines and colour edges stay sharp. The girls no longer flicker and look jagged from a distance.
- Jiggle physics for every girl: chests and butts move like soft flesh (each side on its own), tails and ponytails swing. On during scenes; outside scenes it is off by default (`jiggleOutsideScenes`, 0-1, turns it on to a chosen share). Where a scene's animation already moves the chest or butt by hand, the physics hands it over to the animation instead of adding to it; on hard, fast scenes the bounce is evened out so nothing twitches or snaps.
- Both can be turned off, and the jiggle made stronger or weaker, in `config/sexmod-client.toml` (`smoothTextures`, `jigglePhysics`, `jiggleStrength` 0-3). Changes apply as soon as the file is saved.
- The "loading up custom models" messages no longer appear in chat every time the game starts (they go to the log; errors still show in chat).
- A small link to the [Discord](https://discord.gg/za6UejgY4y) in the corner of the title screen (hide it with `showDiscordLink = false` in the same file).
- The mod is now called NeoJenny.
- The girls' houses no longer sit on a one-block ledge of dirt on gently uneven ground: they stand at the height most of the ground under them has.
- Trees no longer grow through the girls' houses: world generation keeps them a few blocks away.

## 2026-11-06: Galath riding and goblin fixes

- Galath no longer repeats her kill line over and over after she kills you.
- Riding Galath when she has Manglelie: you can steer from the start (no boost needed first), and she no longer fights mobs while you ride her.
- Boosting into the ground no longer kills you with fall damage.
- No more camera flicker while flying on Galath, and Manglelie no longer pops in and out during a boost.
- A thrown goblin now flies off from your hands instead of hanging in front of you and then teleporting to where she landed.

## 2026-11-05: Dragon Staff

- Beds and chests can be assigned to Kobold tribes with the Dragon Staff again.

## 2026-11-02: Dragon Staff

- The staff no longer stays bound to a tribe whose Kobolds have all died, so you can start a new tribe.

## 2026-11-01: First public release

- A full rewrite of FapCraft 1.12.2 for Minecraft 1.21.1 on NeoForge: all eleven companions with their AI, menus, scenes, dialogue and sounds; Kobold tribes, goblin lairs and the Galath boss fight; player-as-girl forms, custom models and clothing, Allie's lamp and the Dragon Staff; the original houses and lairs as real 1.21 structures.
