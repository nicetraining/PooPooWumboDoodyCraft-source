package com.trolmastercard.sexmod.entity;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Compact scene catalogue reconstructed from the original animation state
 * enum. Each phase has a server-side duration so transitions remain identical
 * for every tracking client.
 */
public enum CompanionScene {
    JENNY_WAVE(
            CompanionKind.JENNY, "wave", false,
            phase("animation.jenny.wave", 72),
            loop("animation.jenny.wave_idle", 80)
    ),
    /** The original PAYMENT state: she takes the payment before any paid action, then the chosen scene follows. */
    JENNY_PAYMENT(
            CompanionKind.JENNY, "payment", false,
            phase("animation.jenny.payment", 187)
    ),
    JENNY_STRIP(
            CompanionKind.JENNY, "strip", false,
            phase("animation.jenny.strip", 101)
    ),
    JENNY_BLOWJOB(
            CompanionKind.JENNY, "blowjob", true,
            phase("animation.jenny.blowjobintro", 986),
            loop("animation.jenny.blowjobsuck", 121),
            loop("animation.jenny.blowjobthrust", 70),
            phase("animation.jenny.blowjobcum", 236)
    ),
    JENNY_BOOBJOB(
            CompanionKind.JENNY, "boobjob", true,
            phase("animation.jenny.paizuri_start", 174),
            loop("animation.jenny.paizuri_slow", 108),
            loop("animation.jenny.paizuri_fast", 66),
            phase("animation.jenny.paizuri_cum", 230)
    ),
    /** She has reached the bed and waits for the player (original STARTDOGGY / WAITDOGGY). */
    JENNY_DOGGY_WAIT(
            CompanionKind.JENNY, "doggy_wait", false,
            phase("animation.jenny.doggygoonbed", 131),
            loop("animation.jenny.doggywait", 129)
    ),
    JENNY_DOGGY(
            CompanionKind.JENNY, "doggy", true,
            phase("animation.jenny.doggystart", 199),
            loop("animation.jenny.doggyslow", 93),
            loop("animation.jenny.doggyfast_hard", 65),
            phase("animation.jenny.doggycum", 134)
    ),

    ELLIE_STRIP(
            CompanionKind.ELLIE, "strip", false,
            phase("animation.ellie.strip", 101)
    ),
    /** Horny Ellie dashes at the nearby player (original DASH, 16 ticks). */
    ELLIE_DASH(
            CompanionKind.ELLIE, "dash", false,
            phase("animation.ellie.dash", 16)
    ),
    /** Hug, then wait in the hug idle while the cowgirl / missionary menu is open, then "hug selected". */
    ELLIE_HUG(
            CompanionKind.ELLIE, "hug", true,
            phase("animation.ellie.hug", 150),
            loop("animation.ellie.hugidle", 100),
            phase("animation.ellie.hugselected", 79)
    ),
    /** She has reached the bed and sits down, then waits for the player (original SITDOWN / SITDOWNIDLE). */
    ELLIE_SIT_WAIT(
            CompanionKind.ELLIE, "sit_wait", false,
            phase("animation.ellie.sitdown", 109),
            loop("animation.ellie.sitdownidle", 141)
    ),
    ELLIE_COWGIRL(
            CompanionKind.ELLIE, "cowgirl", true,
            phase("animation.ellie.cowgirlstart", 316),
            loop("animation.ellie.cowgirlslow2", 128),
            loop("animation.ellie.cowgirlfast", 102),
            phase("animation.ellie.cowgirlcum", 308)
    ),
    ELLIE_MISSIONARY(
            CompanionKind.ELLIE, "missionary", true,
            phase("animation.ellie.missionary_start", 65),
            loop("animation.ellie.missionary_slow", 117),
            loop("animation.ellie.missionary_fast", 75),
            phase("animation.ellie.missionary_cum", 289)
    ),
    ELLIE_CARRY(
            CompanionKind.ELLIE, "carry", true,
            phase("animation.ellie.carry_intro", 191),
            loop("animation.ellie.carry_slow2", 100),
            loop("animation.ellie.carry_fast", 80),
            phase("animation.ellie.carry_cum", 150)
    ),

    SLIME_BLOWJOB(
            CompanionKind.SLIME, "blowjob", true,
            loop("animation.slime.blowjobsuck", 120),
            loop("animation.slime.blowjobthrust", 85),
            phase("animation.slime.blowjobcum", 232)
    ),
    SLIME_DOGGY(
            CompanionKind.SLIME, "doggy", true,
            phase("animation.slime.doggygoonbed", 34),
            phase("animation.slime.doggystart", 196),
            loop("animation.slime.doggyslow", 91),
            loop("animation.slime.doggyfast", 65),
            phase("animation.slime.doggycum", 112)
    ),

    BIA_WAVE(
            CompanionKind.BIA, "wave", false,
            phase("animation.bia.wave", 76),
            loop("animation.bia.wave_idle", 80)
    ),
    BIA_STRIP(
            CompanionKind.BIA, "strip", false,
            phase("animation.bia.strip", 101)
    ),
    BIA_TALK(
            CompanionKind.BIA, "talk", false,
            phase("animation.bia.talk_horny2", 47),
            loop("animation.bia.talk_idle2", 300),
            phase("animation.bia.talk_response", 68)
    ),
    BIA_HEADPAT(
            CompanionKind.BIA, "headpat", true,
            phase("animation.bia.headpat", 155)
    ),
    /** She has reached the bed and waits for the player to step up to her (original ANAL_PREPARE / ANAL_WAIT). */
    BIA_ANAL_WAIT(
            CompanionKind.BIA, "anal_wait", false,
            phase("animation.bia.anal_prepare", 111),
            loop("animation.bia.anal_wait", 92)
    ),
    /** Same for the doggy route (original SITDOWN / SITDOWNIDLE). */
    BIA_SIT_WAIT(
            CompanionKind.BIA, "sit_wait", false,
            phase("animation.bia.sitdown", 111),
            loop("animation.bia.sitdownidle", 81)
    ),
    BIA_ANAL(
            CompanionKind.BIA, "anal", true,
            phase("animation.bia.anal_start", 57),
            loop("animation.bia.anal_slow", 90),
            loop("animation.bia.anal_fast", 74),
            phase("animation.bia.anal_cum", 76)
    ),
    BIA_PRONE(
            CompanionKind.BIA, "prone", true,
            phase("animation.bia.prone_doggy_intro", 38),
            phase("animation.bia.prone_doggy_insert", 43),
            loop("animation.bia.prone_doggy_soft", 88),
            loop("animation.bia.prone_doggy_hard1", 82),
            phase("animation.bia.prone_doggy_cum", 58)
    ),

    BEE_SEX(
            CompanionKind.BEE, "sex", true,
            phase("animation.bee.sex_start", 165),
            loop("animation.bee.sex_slow", 120),
            loop("animation.bee.sex_fast", 68),
            phase("animation.bee.sex_cum", 51)
    ),

    /** Luna takes the fish before touch boobs / sex (original PAYMENT). */
    LUNA_PAYMENT(
            CompanionKind.LUNA, "payment", false,
            phase("animation.cat.payment", 186)
    ),
    /** She waits beside the bed (original WAIT_CAT). */
    LUNA_SIT_WAIT(
            CompanionKind.LUNA, "sit_wait", false,
            loop("animation.cat.wait", 399)
    ),
    LUNA_HEADPAT(
            CompanionKind.LUNA, "headpat", true,
            phase("animation.cat.head_pat", 163)
    ),
    LUNA_TOUCH(
            CompanionKind.LUNA, "touchboobs", true,
            phase("animation.cat.touch_boobs_intro", 215),
            loop("animation.cat.touch_boobs_slow", 170),
            loop("animation.cat.touch_boobs_fast", 85),
            phase("animation.cat.touch_boobs_cum", 106)
    ),
    LUNA_SITTING(
            CompanionKind.LUNA, "sitting", true,
            phase("animation.cat.sitting_intro", 119),
            loop("animation.cat.sitting_slow", 100),
            loop("animation.cat.sitting_fast", 70),
            phase("animation.cat.sitting_cum", 105)
    ),

    ALLIE_RICH(
            CompanionKind.ALLIE, "rich", false,
            phase("animation.allie.rich", 100)
    ),
    ALLIE_DEEPTHROAT(
            CompanionKind.ALLIE, "deepthroat", true,
            phase("animation.allie.deepthroat_prepare", 69),
            phase("animation.allie.deepthroat_start", 64),
            loop("animation.allie.deepthroat_slow", 119),
            loop("animation.allie.deepthroat_fast", 72),
            phase("animation.allie.deepthroat_cum", 46)
    ),
    ALLIE_REVERSE_COWGIRL(
            CompanionKind.ALLIE, "reverse_cowgirl", true,
            phase("animation.allie.reverse_cowgirl_start", 89),
            loop("animation.allie.reverse_cowgirl_slow1", 95),
            loop("animation.allie.reverse_cowgirl_fasts", 67),
            phase("animation.allie.reverse_cowgirl_cum", 49)
    ),

    KOBOLD_ANAL(
            CompanionKind.KOBOLD, "anal", true,
            phase("animation.kobold.analStart", 43),
            loop("animation.kobold.analSoft", 85),
            loop("animation.kobold.analHard", 70),
            phase("animation.kobold.analCum", 102)
    ),
    KOBOLD_BLOWJOB(
            CompanionKind.KOBOLD, "blowjob", true,
            phase("animation.kobold.blowjobStart", 226),
            loop("animation.kobold.blowjobSlowR", 100),
            loop("animation.kobold.blowjobFast", 60),
            phase("animation.kobold.blowjobCum", 84)
    ),
    KOBOLD_MATING_PRESS(
            CompanionKind.KOBOLD, "mating_press", true,
            phase("animation.kobold.mating_press_start", 55),
            loop("animation.kobold.mating_press_soft", 100),
            loop("animation.kobold.mating_press_hard", 75),
            phase("animation.kobold.mating_press_cum", 145)
    ),

    GOBLIN_PAIZURI(
            CompanionKind.GOBLIN, "paizuri", true,
            phase("animation.goblin.paizuri_start", 100),
            loop("animation.goblin.paizuri_slow", 134),
            loop("animation.goblin.paizuri_fast", 75),
            phase("animation.goblin.paizuri_cum", 50)
    ),
    GOBLIN_BREEDING(
            CompanionKind.GOBLIN, "breeding", true,
            phase("animation.goblin.breeding_intro_1", 245),
            loop("animation.goblin.breeding_slow_1l", 90),
            loop("animation.goblin.breeding_fast_1s", 65),
            phase("animation.goblin.breeding_cum_1", 170)
    ),
    GOBLIN_NELSON(
            CompanionKind.GOBLIN, "nelson", true,
            phase("animation.goblin.nelson_intro", 41),
            loop("animation.goblin.nelson_slow", 93),
            loop("animation.goblin.nelson_fasts", 75),
            phase("animation.goblin.nelson_cum", 35)
    ),

    /** Not a real scene: the "ride" entry of Galath's menu (mounts her and starts controlled flight). */
    GALATH_RIDE(
            CompanionKind.GALATH, "ride", false,
            loop("animation.galath.controlled_flight", 29)
    ),
    /** Tamed "cowgirl": original RAPE_INTRO -> RAPE_ON_GOING -> RAPE_CUM (no damage, no minigame when tamed). */
    GALATH_COWGIRL(
            CompanionKind.GALATH, "cowgirl", true,
            phase("animation.galath.rape_intro", 46),
            loop("animation.galath.rape0", 19),
            phase("animation.galath.rape_cum", 35),
            phase("animation.galath.rape_cum_idle", 79)
    ),
    /** Tamed "anal": starts straight in CORRUPT_SLOW (the intro is skipped). */
    GALATH_ANAL(
            CompanionKind.GALATH, "anal", true,
            loop("animation.galath.corrupt_slow", 88),
            loop("animation.galath.corrupt_hard", 75),
            phase("animation.galath.corrupt_cum", 57)
    ),
    GALATH_CORRUPT(
            CompanionKind.GALATH, "corrupt", true,
            phase("animation.galath.corrupt_intro", 30),
            loop("animation.galath.corrupt_slow", 88),
            loop("animation.galath.corrupt_hard", 75),
            phase("animation.galath.corrupt_cum", 57)
    ),

    /**
     * The bed animations are authored for the combined Galath and Manglelie
     * model, which is the only geometry that contains both skeletons. Played on
     * Manglelie's own model the keyframes that exist there are static, so the
     * scene looked frozen. The scene therefore renders with that combined model
     * and Galath's animation file, where all four phases exist.
     */
    MANGLELIE_BED(
            CompanionKind.MANGLELIE, "bed", true,
            phase("animation.shared.bed_back", 15),
            loop("animation.shared.bed_slow", 100),
            loop("animation.shared.bed_fast", 60),
            phase("animation.shared.bed_cum", 78)
    );

    private final CompanionKind kind;
    private final String path;
    private final boolean hasPartner;
    private final List<Phase> phases;

    CompanionScene(
            CompanionKind kind,
            String path,
            boolean hasPartner,
            Phase... phases
    ) {
        this.kind = kind;
        this.path = path;
        this.hasPartner = hasPartner;
        this.phases = Collections.unmodifiableList(Arrays.asList(phases));
    }

    public CompanionKind kind() {
        return this.kind;
    }

    public String path() {
        return this.path;
    }

    public String serializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "scene.sexmod." + this.serializedName();
    }

    public boolean hasPartner() {
        return this.hasPartner;
    }

    /**
     * The original's per-state {@code useBoyCam} flag: in first person the view sits on the animated
     * {@code boyCam} bone of the girl's model. Only the states that flag it (carry, prone doggy,
     * Allie's deepthroat and reverse cowgirl) use it; others keep the player's own position.
     */
    public boolean usesBoyCam() {
        return switch (this) {
            case ELLIE_CARRY, BIA_PRONE, ALLIE_DEEPTHROAT, ALLIE_REVERSE_COWGIRL -> true;
            default -> false;
        };
    }

    /**
     * Kind whose texture, animation file and cosmetic bone rules this scene
     * borrows, or {@code null} when the companion's own assets are used.
     */
    public CompanionKind renderAsKind() {
        return this == MANGLELIE_BED ? CompanionKind.GALATH : null;
    }

    /** Model used instead of the companion's own one, or {@code null}. */
    public ResourceLocation modelOverride() {
        return this == MANGLELIE_BED
                ? ResourceLocation.fromNamespaceAndPath("sexmod", "geo/galath/galath_con_mang.geo.json")
                : null;
    }

    /**
     * True when the pose is authored as already lying on the mattress, so the
     * scene has to be anchored onto the bed. The doggy scenes instead open with
     * their own "go on bed" animation and keep their own starting position.
     */
    public boolean anchorsOnBed() {
        return this == MANGLELIE_BED;
    }

    /** The strip / dress up scenes: they swap the model at the animation's 3 s keyframe. */
    public boolean isClothingToggle() {
        return this == JENNY_STRIP || this == ELLIE_STRIP || this == BIA_STRIP;
    }

    public boolean requiresBed() {
        return this == JENNY_DOGGY || this == SLIME_DOGGY || this == MANGLELIE_BED
                || this == BIA_ANAL || this == BIA_PRONE || this == ELLIE_COWGIRL || this == ELLIE_MISSIONARY || this == LUNA_SITTING;
    }

    /** Scenes that only lead into something else: no reward, no afterglow, no completion message. */
    public boolean isPrelude() {
        return this.isClothingToggle() || this == BIA_TALK || this == JENNY_PAYMENT || this == LUNA_PAYMENT || this == ELLIE_DASH || this == ELLIE_HUG || this.isWaitScene();
    }

    /** Scenes that only wait beside the bed until the player steps up, then hand over to the chosen scene. */
    public boolean isWaitScene() {
        return this == BIA_ANAL_WAIT || this == BIA_SIT_WAIT || this == JENNY_DOGGY_WAIT || this == ELLIE_SIT_WAIT || this == LUNA_SIT_WAIT;
    }

    /** The wait scene played on arrival at the bed for a bed scene, or null. */
    public CompanionScene waitScene() {
        return switch (this) {
            case BIA_ANAL -> BIA_ANAL_WAIT;
            case BIA_PRONE -> BIA_SIT_WAIT;
            case JENNY_DOGGY -> JENNY_DOGGY_WAIT;
            case ELLIE_COWGIRL, ELLIE_MISSIONARY -> ELLIE_SIT_WAIT;
            case LUNA_SITTING -> LUNA_SIT_WAIT;
            default -> null;
        };
    }

    /** Scenes during which the local player's movement keys are locked without attaching them (original d3.a(false)). */
    public boolean locksControls() {
        return this == BIA_TALK || this == ELLIE_DASH || this == ELLIE_HUG;
    }

    /** Label on the action panel; Bia's second menu uses the original's plain "Anal" / "Doggy". */
    public String menuLabelKey() {
        return switch (this) {
            case BIA_ANAL -> "action.names.anal";
            case BIA_PRONE -> "action.names.doggy";
            case BIA_TALK -> "action.names.talk";
            case ELLIE_CARRY -> "action.names.facefuck";
            case ELLIE_COWGIRL, GALATH_COWGIRL -> "action.names.cowgirl";
            case GALATH_ANAL -> "action.names.anal";
            case GALATH_RIDE -> "action.names.ride";
            case ELLIE_MISSIONARY -> "action.names.missionary";
            default -> this.translationKey();
        };
    }

    public PhaseRole phaseRole(int index) {
        Phase phase = this.phase(index);
        if (phase == null || !this.hasInteractivePhases()) {
            return PhaseRole.AUTO;
        }
        if (phase.loop()) {
            return PhaseRole.INTENSITY;
        }
        return index > this.lastLoopPhase() ? PhaseRole.FINALE : PhaseRole.AUTO;
    }

    public boolean hasInteractivePhases() {
        return this.hasPartner && this.firstLoopPhase() >= 0;
    }

    public int firstLoopPhase() {
        for (int index = 0; index < this.phases.size(); index++) {
            if (this.phases.get(index).loop()) {
                return index;
            }
        }
        return -1;
    }

    public int lastLoopPhase() {
        for (int index = this.phases.size() - 1; index >= 0; index--) {
            if (this.phases.get(index).loop()) {
                return index;
            }
        }
        return -1;
    }

    public int previousLoopPhase(int current) {
        for (int index = Math.min(current - 1, this.phases.size() - 1); index >= 0; index--) {
            if (this.phases.get(index).loop()) {
                return index;
            }
        }
        return -1;
    }

    public int nextLoopPhase(int current) {
        for (int index = Math.max(0, current + 1); index < this.phases.size(); index++) {
            if (this.phases.get(index).loop()) {
                return index;
            }
        }
        return -1;
    }

    public int finalePhase() {
        int lastLoop = this.lastLoopPhase();
        if (lastLoop < 0) {
            return -1;
        }
        for (int index = lastLoop + 1; index < this.phases.size(); index++) {
            if (!this.phases.get(index).loop()) {
                return index;
            }
        }
        return -1;
    }

    public List<Phase> phases() {
        return this.phases;
    }

    public Phase phase(int index) {
        if (index < 0 || index >= this.phases.size()) {
            return null;
        }

        return this.phases.get(index);
    }

    public static CompanionScene byName(String value) {
        if (value != null) {
            for (CompanionScene scene : values()) {
                if (scene.name().equalsIgnoreCase(value)
                        || scene.serializedName().equalsIgnoreCase(value)) {
                    return scene;
                }
            }
        }

        return null;
    }

    public static List<CompanionScene> forKind(CompanionKind kind) {
        List<CompanionScene> result = new ArrayList<>();
        for (CompanionScene scene : values()) {
            if (scene.kind == kind) {
                result.add(scene);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static Phase phase(String animation, int ticks) {
        return new Phase(animation, Math.max(1, ticks), false);
    }

    private static Phase loop(String animation, int ticks) {
        return new Phase(animation, Math.max(1, ticks), true);
    }

    public enum PhaseRole {
        AUTO,
        INTENSITY,
        FINALE
    }

    public record Phase(String animation, int durationTicks, boolean loop) {
        public RawAnimation rawAnimation() {
            return this.loop
                    ? RawAnimation.begin().thenLoop(this.animation)
                    : RawAnimation.begin().thenPlay(this.animation);
        }
    }
}
