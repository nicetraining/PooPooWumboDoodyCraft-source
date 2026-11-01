package com.fartypoopy.sexmod;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The original's config/sexmod.json options as a NeoForge common config. {@code shouldLoadOtherSkins} was written but never
 * read by the original, so it is left out.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHOULD_GEN_BUILDINGS = BUILDER
            .comment("Generate the mod's structures, goblin halls and wild Kobold tribes in new chunks")
            .define("shouldGenBuildings", true);

    public static final ModConfigSpec.BooleanValue ALLOW_FLYING = BUILDER
            .comment("Let the Bee, Allie and Galath player-girl forms fly (original ei.ag)")
            .define("allowFlying", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
