package com.trolmastercard.sexmod.entity;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.Locale;

/**
 * Stable character catalogue reconstructed from the original entity registry.
 * Registry paths intentionally match the 1.12.2 JAR so commands, assets, and
 * existing saves have the best possible migration path.
 */
public enum CompanionKind {
    JENNY(
            "jenny", "Jenny",
            "geo/jenny/jennydressed.geo.json",
            "textures/entity/jenny/jenny.png",
            "animations/jenny/jenny.animation.json",
            "animation.jenny.idle", "animation.jenny.walk",
            0.49F, 1.95F, 20.0D, 0.30D, 0x322600, 0xC11605
    ),
    ELLIE(
            "ellie", "Ellie",
            "geo/ellie/dressed.geo.json",
            "textures/entity/ellie/ellie.png",
            "animations/ellie/ellie.animation.json",
            "animation.ellie.idle", "animation.ellie.walk",
            0.60F, 1.95F, 20.0D, 0.30D, 0x161616, 0x980000
    ),
    SLIME(
            "slime", "Slime",
            "geo/slime/dressed.geo.json",
            "textures/entity/slime/slime.png",
            "animations/slime/slime.animation.json",
            "animation.slime.idle", "animation.slime.walk",
            0.70F, 1.80F, 20.0D, 0.28D, 0xC8EE24, 0x7DC42A
    ),
    BIA(
            "bia", "Bia",
            "geo/bia/biadressed.geo.json",
            "textures/entity/bia/bia.png",
            "animations/bia/bia.animation.json",
            "animation.bia.idle", "animation.bia.walk",
            0.49F, 1.65F, 20.0D, 0.30D, 0x724D70, 0x6EB24B
    ),
    BEE(
            "bee", "Bee",
            "geo/bee/bee.geo.json",
            "textures/entity/bee/bee.png",
            "animations/bee/bee.animation.json",
            "animation.bee.idle", "animation.bee.idle",
            0.30F, 1.50F, 12.0D, 0.30D, 0xFED268, 0x432ADB
    ),
    LUNA(
            "luna", "Luna",
            "geo/cat/cat.geo.json",
            "textures/entity/cat/cat.png",
            "animations/cat/cat.animation.json",
            "animation.cat.idle", "animation.cat.walk",
            0.60F, 1.80F, 20.0D, 0.32D, 0x784A7B, 0x792946
    ),
    ALLIE(
            "allie", "Allie",
            "geo/allie/allie.geo.json",
            "textures/entity/allie/allie.png",
            "animations/allie/allie.animation.json",
            "animation.allie.null", "animation.allie.null",
            0.50F, 1.00F, 20.0D, 0.28D, 0x5FD5AF, 0xD8B899
    ),
    KOBOLD(
            "kobold", "Kobold",
            "geo/kobold/kobold.geo.json",
            "textures/entity/kobold/kobold.png",
            "animations/kobold/kobold.animation.json",
            "animation.kobold.idle", "animation.kobold.walk",
            0.50F, 0.99F, 20.0D, 0.34D, 0x783F69, 0xE7B36E
    ),
    GOBLIN(
            "goblin", "Goblin",
            "geo/goblin/goblin.geo.json",
            "textures/entity/goblin/goblin.png",
            "animations/goblin/goblin.animation.json",
            "animation.goblin.idle", "animation.goblin.walk",
            0.50F, 0.99F, 20.0D, 0.34D, 0x009A00, 0x004C00
    ),
    GALATH(
            "galath", "Galath",
            "geo/galath/galath.geo.json",
            "textures/entity/galath/galath.png",
            "animations/galath/galath.animation.json",
            "animation.galath.idle", "animation.galath.walk",
            1.20F, 2.40F, 60.0D, 0.30D, 0xFF0000, 0xFF0000
    ),
    MANGLELIE(
            "manglelie", "Manglelie",
            "geo/manglelie/manglelie.geo.json",
            "textures/entity/manglelie/manglelie.png",
            "animations/manglelie/manglelie.animation.json",
            "animation.manglelie.idle", "animation.manglelie.walk",
            0.90F, 2.20F, 60.0D, 0.32D, 0xF9F9F9, 0x817316
    );

    private final String registryPath;
    private final String displayName;
    private final String modelPath;
    private final String texturePath;
    private final String animationPath;
    private final RawAnimation idleAnimation;
    private final RawAnimation walkAnimation;
    private final String animationPrefix;
    private final java.util.Map<String, RawAnimation> stateAnimations = new java.util.concurrent.ConcurrentHashMap<>();
    private final float width;
    private final float height;
    private final double maxHealth;
    private final double movementSpeed;
    private final int primaryEggColor;
    private final int secondaryEggColor;

    CompanionKind(
            String registryPath,
            String displayName,
            String modelPath,
            String texturePath,
            String animationPath,
            String idleAnimation,
            String walkAnimation,
            float width,
            float height,
            double maxHealth,
            double movementSpeed,
            int primaryEggColor,
            int secondaryEggColor
    ) {
        this.registryPath = registryPath;
        this.displayName = displayName;
        this.modelPath = modelPath;
        this.texturePath = texturePath;
        this.animationPath = animationPath;
        this.idleAnimation = RawAnimation.begin().thenLoop(idleAnimation);
        this.walkAnimation = RawAnimation.begin().thenLoop(walkAnimation);
        this.animationPrefix = idleAnimation.substring(0, idleAnimation.lastIndexOf('.') + 1);
        this.width = width;
        this.height = height;
        this.maxHealth = maxHealth;
        this.movementSpeed = movementSpeed;
        this.primaryEggColor = primaryEggColor;
        this.secondaryEggColor = secondaryEggColor;
    }

    public String registryPath() {
        return this.registryPath;
    }

    public String displayName() {
        return this.displayName;
    }

    public ResourceLocation modelResource() {
        return ResourceLocation.fromNamespaceAndPath("sexmod", this.modelPath);
    }

    public ResourceLocation modelResource(OutfitMode outfit) {
        String selectedPath = switch (outfit) {
            case ALTERNATE -> switch (this) {
                case JENNY -> "geo/jenny/jennynude.geo.json";
                case ELLIE -> "geo/ellie/nude.geo.json";
                case SLIME -> "geo/slime/nude.geo.json";
                case BIA -> "geo/bia/bianude.geo.json";
                default -> this.modelPath;
            };
            case ARMORED -> switch (this) {
                case ALLIE -> "geo/allie/armored.geo.json";
                case BEE -> "geo/bee/armored.geo.json";
                case SLIME -> "geo/slime/armored.geo.json";
                case KOBOLD -> "geo/kobold/armored.geo.json";
                case GOBLIN -> "geo/goblin/armored.geo.json";
                default -> this.modelPath;
            };
            default -> this.modelPath;
        };

        return ResourceLocation.fromNamespaceAndPath("sexmod", selectedPath);
    }

    public ResourceLocation textureResource() {
        return ResourceLocation.fromNamespaceAndPath("sexmod", this.texturePath);
    }

    public ResourceLocation animationResource() {
        return ResourceLocation.fromNamespaceAndPath("sexmod", this.animationPath);
    }

    public RawAnimation idleAnimation() {
        return this.idleAnimation;
    }

    public RawAnimation walkAnimation() {
        return this.walkAnimation;
    }

    public float width() {
        return this.width;
    }

    public float height() {
        return this.height;
    }

    public double maxHealth() {
        return this.maxHealth;
    }

    public double movementSpeed() {
        return this.movementSpeed;
    }

    public int primaryEggColor() {
        return this.primaryEggColor;
    }

    public int secondaryEggColor() {
        return this.secondaryEggColor;
    }

    public String spawnEggPath() {
        return this.registryPath + "_spawn_egg";
    }

    public boolean supportsOutfit(OutfitMode outfit) {
        return switch (outfit) {
            case DEFAULT -> true;
            case ALTERNATE -> this == JENNY
                    || this == ELLIE
                    || this == SLIME
                    || this == BIA;
            case ARMORED -> this == ALLIE
                    || this == BEE
                    || this == SLIME
                    || this == KOBOLD
                    || this == GOBLIN;
        };
    }

    public int appearanceVariants() {
        return switch (this) {
            case KOBOLD -> 8;
            case GOBLIN -> 8;
            case GALATH, MANGLELIE -> 2;
            default -> 1;
        };
    }

    /** Luna's equipment screen has an extra fishing-rod slot (original 7-slot menu). */
    public boolean hasFishingRodSlot() {
        return this == LUNA;
    }

    /** Kinds with the original 27-slot storage chest (Kobold and Bee). */
    public boolean hasStorage() {
        return this == KOBOLD || this == BEE;
    }

    /**
     * Jenny, Ellie, Bia and Luna share the original's master logic: follow, fight, get downed, heal,
     * go home. The other kinds have their own AI in the original and get it when they are ported.
     */
    public boolean hasMasterAI() {
        return this == JENNY || this == ELLIE || this == BIA || this == LUNA;
    }

    /** Translation key of the line said when she is following somebody else (Luna is never busy). */
    public String busyDialogue() {
        return switch (this) {
            case JENNY -> "jenny.dialogue.busy";
            case ELLIE -> "ellie.dialogue.busy";
            case BIA -> "bia.dialogue.busy";
            default -> null;
        };
    }

    /** Looping animation of this kind for a base state such as "downed" or "fastwalk". */
    public RawAnimation stateAnimation(String suffix) {
        return this.stateAnimations.computeIfAbsent(
                suffix, key -> RawAnimation.begin().thenLoop(this.animationPrefix + key));
    }

    /** The original let any player open a Kobold's (paid) action menu, owned or not. */
    public boolean opensMenuToStrangers() {
        return this == KOBOLD;
    }

    public boolean canFly() {
        return this == BEE || this == GALATH || this == MANGLELIE;
    }

    public boolean usesRangedMagic() {
        return this == BEE || this == GALATH;
    }

    public static CompanionKind byName(String value) {
        if (value == null || value.isBlank()) {
            return JENNY;
        }

        String normalized = value.toLowerCase(Locale.ROOT);
        for (CompanionKind kind : values()) {
            if (kind.registryPath.equals(normalized) || kind.name().equalsIgnoreCase(value)) {
                return kind;
            }
        }

        return JENNY;
    }
}
