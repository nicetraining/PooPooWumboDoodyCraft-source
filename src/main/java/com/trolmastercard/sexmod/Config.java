package com.trolmastercard.sexmod;

import net.neoforged.neoforge.common.ModConfigSpec;

/** The original's config/sexmod.json option {@code shouldGenBuildings}, as a NeoForge common config. */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHOULD_GEN_BUILDINGS = BUILDER
            .comment("Generate the mod's structures, goblin halls and wild Kobold tribes in new chunks")
            .define("shouldGenBuildings", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
