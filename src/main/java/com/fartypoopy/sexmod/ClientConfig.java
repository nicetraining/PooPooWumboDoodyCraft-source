package com.fartypoopy.sexmod;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only look options (config/sexmod-client.toml); NeoForge reloads the file when it changes, so edits apply live. */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SMOOTH_TEXTURES = BUILDER
            .comment("Smooth the girls' textures: the blocky pixel-art shading becomes soft gradients (line art and colour",
                    "edges stay sharp), and the textures are filtered with mipmaps so they no longer flicker and jag at a distance")
            .define("smoothTextures", true);

    public static final ModConfigSpec.BooleanValue JIGGLE = BUILDER
            .comment("Jiggle physics: chests, butts, tails and ponytails swing and bounce with the girls' motion")
            .define("jigglePhysics", true);

    public static final ModConfigSpec.DoubleValue JIGGLE_STRENGTH = BUILDER
            .comment("How strongly they jiggle during scenes (1 = default, 0 = none)")
            .defineInRange("jiggleStrength", 1.0D, 0.0D, 3.0D);

    public static final ModConfigSpec.DoubleValue JIGGLE_OUTSIDE_SCENES = BUILDER
            .comment("How much of that they jiggle outside scenes, walking and standing around dressed (0 = not at all,",
                    "1 = as much as in scenes)")
            .defineInRange("jiggleOutsideScenes", 0.0D, 0.0D, 1.0D);

    public static final ModConfigSpec.BooleanValue SHOW_DISCORD_LINK = BUILDER
            .comment("Show the link to the mod's Discord in the title screen's top-left corner")
            .define("showDiscordLink", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }

    /** False until the config is loaded (the defaults then apply). */
    public static boolean smoothTextures() {
        return !SPEC.isLoaded() || SMOOTH_TEXTURES.get();
    }

    public static boolean showDiscordLink() {
        return !SPEC.isLoaded() || SHOW_DISCORD_LINK.get();
    }

    public static float jiggleStrength() {
        if (!SPEC.isLoaded()) {
            return 1.0F;
        }
        return JIGGLE.get() ? JIGGLE_STRENGTH.get().floatValue() : 0.0F;
    }

    /** Share of {@link #jiggleStrength()} used outside scenes. */
    public static float jiggleOutsideScenes() {
        return SPEC.isLoaded() ? JIGGLE_OUTSIDE_SCENES.get().floatValue() : 0.0F;
    }
}
