package com.fartypoopy.sexmod.entity;

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
            phase("animation.jenny.payment", 183)
    ),
    JENNY_STRIP(
            CompanionKind.JENNY, "strip", false,
            phase("animation.jenny.strip", 101)
    ),
    JENNY_BLOWJOB(
            CompanionKind.JENNY, "blowjob", true,
            phase("animation.jenny.blowjobintro", 986),
            loop("animation.jenny.blowjobsuck", 121),
            loop("animation.jenny.blowjobthrust", 35),
            phase("animation.jenny.blowjobcum", 205)
    ),
    JENNY_BOOBJOB(
            CompanionKind.JENNY, "boobjob", true,
            phase("animation.jenny.paizuri_start", 174),
            loop("animation.jenny.paizuri_slow", 68),
            loop("animation.jenny.paizuri_fast", 22),
            phase("animation.jenny.paizuri_cum", 203) // ends at bjcDone (10.16 s) like the blowjob, 1.04 s after the wipe
    ),
    /** She has reached the bed and waits for the player (original STARTDOGGY / WAITDOGGY). */
    JENNY_DOGGY_WAIT(
            CompanionKind.JENNY, "doggy_wait", false,
            phase("animation.jenny.doggygoonbed", 131),
            loop("animation.jenny.doggywait", 129)
    ),
    JENNY_DOGGY(
            CompanionKind.JENNY, "doggy", true,
            phase("animation.jenny.doggystart", 195),
            loop("animation.jenny.doggyslow", 46),
            loop("animation.jenny.doggyfast_soft", 22), // ex.aa false: the fast loop starts soft
            phase("animation.jenny.doggycum", 112),
            loop("animation.jenny.doggyfast_hard", 22) // doggyfastReady with the key held: hard, restarted
    ),

    ELLIE_STRIP(
            CompanionKind.ELLIE, "strip", false,
            phase("animation.ellie.strip", 97)
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
            phase("animation.ellie.cowgirlstart", 310),
            loop("animation.ellie.cowgirlslow2", 64),
            loop("animation.ellie.cowgirlfast", 68),
            phase("animation.ellie.cowgirlcum", 298)
    ),
    ELLIE_MISSIONARY(
            CompanionKind.ELLIE, "missionary", true,
            phase("animation.ellie.missionary_start", 65),
            loop("animation.ellie.missionary_slow", 58),
            loop("animation.ellie.missionary_fast", 38),
            phase("animation.ellie.missionary_cum", 278)
    ),
    ELLIE_CARRY(
            CompanionKind.ELLIE, "carry", true,
            phase("animation.ellie.carry_intro", 191),
            loop("animation.ellie.carry_slow1", 63), // el.aa starts at 1
            loop("animation.ellie.carry_fast", 15),
            phase("animation.ellie.carry_cum", 62),
            loop("animation.ellie.carry_slow2", 63), // carry_slowDone: a new slow{1-4}, never the same twice
            loop("animation.ellie.carry_slow3", 63),
            loop("animation.ellie.carry_slow4", 32)
    ),

    SLIME_BLOWJOB(
            CompanionKind.SLIME, "blowjob", true,
            loop("animation.slime.blowjobsuck", 120),
            loop("animation.slime.blowjobthrust", 35), // fn bjtDone at 1.72 s: back to SUCKBLOWJOB
            phase("animation.slime.blowjobcum", 205)
    ),
    SLIME_DOGGY(
            CompanionKind.SLIME, "doggy", true,
            phase("animation.slime.doggystart", 189),
            loop("animation.slime.doggyslow", 46),
            loop("animation.slime.doggyfast", 22),
            phase("animation.slime.doggycum", 108)
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
            loop("animation.bia.talk_idle2", 130),
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
            loop("animation.bia.anal_slow", 30),
            loop("animation.bia.anal_fast", 18),
            phase("animation.bia.anal_cum", 72)
    ),
    BIA_PRONE(
            CompanionKind.BIA, "prone", true,
            phase("animation.bia.prone_doggy_intro", 38),
            phase("animation.bia.prone_doggy_insert", 42), // fp PRONE_DOGGY_INSERT: 42 ticks -> soft
            loop("animation.bia.prone_doggy_soft", 29),
            loop("animation.bia.prone_doggy_hard1", 34),
            phase("animation.bia.prone_doggy_cum", 58),
            loop("animation.bia.prone_doggy_hard2", 34),
            loop("animation.bia.prone_doggy_hard3", 34)
    ),

    BEE_SEX(
            CompanionKind.BEE, "sex", true,
            phase("animation.bee.sex_start", 165),
            loop("animation.bee.sex_slow", 60),
            loop("animation.bee.sex_fast", 34),
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
            loop("animation.cat.touch_boobs_slow", 85),
            loop("animation.cat.touch_boobs_fast", 28),
            phase("animation.cat.touch_boobs_cum", 106),
            loop("animation.cat.touch_boobs_slow1", 40) // touch_boobs_slowDone: 50 % after slow, never twice
    ),
    LUNA_SITTING(
            CompanionKind.LUNA, "sitting", true,
            phase("animation.cat.sitting_intro", 119),
            loop("animation.cat.sitting_slow", 40),
            loop("animation.cat.sitting_fast", 29),
            phase("animation.cat.sitting_cum", 102)
    ),

    /** First wish: the long summon speech (original SUMMON -> SUMMON_WAIT). The wish menu opens near the end and cannot be dismissed. */
    ALLIE_SUMMON(
            CompanionKind.ALLIE, "summon", false,
            phase("animation.allie.summon", 244),
            loop("animation.allie.summon_wait", 48)
    ),
    /** Later wishes: the short summon (original SUMMON_NORMAL -> SUMMON_NORMAL_WAIT). */
    ALLIE_SUMMON_NORMAL(
            CompanionKind.ALLIE, "summon_normal", false,
            phase("animation.allie.summon_normal", 125),
            loop("animation.allie.summon_normal_wait", 33)
    ),
    /** Summoned on sand: she panics and leaves (original SUMMON_SAND). */
    ALLIE_SUMMON_SAND(
            CompanionKind.ALLIE, "summon_sand", false,
            phase("animation.allie.summon_sand", 244)
    ),
    ALLIE_RICH(
            CompanionKind.ALLIE, "rich", false,
            phase("animation.allie.rich", 100)
    ),
    ALLIE_RICH_NORMAL(
            CompanionKind.ALLIE, "rich_normal", false,
            phase("animation.allie.rich_normal", 106)
    ),
    /** Before the deepthroat / reverse cowgirl choice plays out (first wish). */
    ALLIE_PREPARE(
            CompanionKind.ALLIE, "prepare", false,
            phase("animation.allie.deepthroat_prepare", 69)
    ),
    /** Same, for later wishes. */
    ALLIE_PREPARE_NORMAL(
            CompanionKind.ALLIE, "prepare_normal", false,
            phase("animation.allie.deepthroat_normal_prepare", 33)
    ),
    ALLIE_DEEPTHROAT(
            CompanionKind.ALLIE, "deepthroat", true,
            phase("animation.allie.deepthroat_start", 64),
            loop("animation.allie.deepthroat_slow", 59),
            loop("animation.allie.deepthroat_fast", 24),
            phase("animation.allie.deepthroat_cum", 46)
    ),
    ALLIE_REVERSE_COWGIRL(
            CompanionKind.ALLIE, "reverse_cowgirl", true,
            phase("animation.allie.reverse_cowgirl_start", 88), // fp REVERSE_COWGIRL_START: 88 ticks -> slow
            loop("animation.allie.reverse_cowgirl_slow1", 48),
            loop("animation.allie.reverse_cowgirl_fasts", 34), // FAST_START: 34 ticks -> slow
            phase("animation.allie.reverse_cowgirl_cum", 49),
            loop("animation.allie.reverse_cowgirl_slow2", 48), // ev.T: a new slow variant every loop (cowgirlSlowDone)
            loop("animation.allie.reverse_cowgirl_slow3", 48),
            loop("animation.allie.reverse_cowgirl_fastc1", 39), // FAST_CONTINUES (ev.L): 39 ticks -> slow
            loop("animation.allie.reverse_cowgirl_fastc2", 39),
            loop("animation.allie.reverse_cowgirl_fastc3", 39)
    ),

    /** Original PAYMENT for a Kobold: the backpack rummage before a stranger's scene (no owner, no horny effect). */
    KOBOLD_PAYMENT(
            CompanionKind.KOBOLD, "payment", false,
            phase("animation.kobold.paymentBackpack", 144)
    ),
    KOBOLD_ANAL(
            CompanionKind.KOBOLD, "anal", true,
            phase("animation.kobold.analStart", 42),
            loop("animation.kobold.analSoft", 28),
            loop("animation.kobold.analHard", 28),
            phase("animation.kobold.analCum", 81)
    ),
    KOBOLD_BLOWJOB(
            CompanionKind.KOBOLD, "blowjob", true,
            phase("animation.kobold.blowjobStart", 225),
            loop("animation.kobold.blowjobSlowR", 50),
            loop("animation.kobold.blowjobFast", 20),
            phase("animation.kobold.blowjobCum", 76),
            loop("animation.kobold.blowjobSlowL", 50), // variant of the slow loop (side flag a4)
            phase("animation.kobold.blowjobSlowLSwitch", 50), // slow L -> slow R
            phase("animation.kobold.blowjobSlowRSwitch", 50) // slow R -> slow L
    ),
    KOBOLD_MATING_PRESS(
            CompanionKind.KOBOLD, "mating_press", true,
            phase("animation.kobold.mating_press_start", 54),
            loop("animation.kobold.mating_press_soft", 33),
            loop("animation.kobold.mating_press_hard", 31),
            phase("animation.kobold.mating_press_cum", 144)
    ),

    GOBLIN_PAIZURI(
            CompanionKind.GOBLIN, "paizuri", true,
            phase("animation.goblin.paizuri_start", 98),
            loop("animation.goblin.paizuri_idle", 120), // waits until the fast key is held (original PAIZURI_IDLE)
            loop("animation.goblin.paizuri_slow", 67),
            loop("animation.goblin.paizuri_fast", 25),
            phase("animation.goblin.paizuri_cum", 39),
            loop("animation.goblin.paizuri_slow2", 67), // variant of the slow loop (paizuriSwitch, 50 %)
            loop("animation.goblin.paizuri_fast_countinues", 25) // variant of the fast loop while the key stays held
    ),
    GOBLIN_BREEDING(
            CompanionKind.GOBLIN, "breeding", true,
            phase("animation.goblin.breeding_intro_1", 195), // breedingCam2 (9.71 s) falls through into breedingIntroDone: slow
            loop("animation.goblin.breeding_slow_1l", 38),
            loop("animation.goblin.breeding_fast_1s", 25),
            phase("animation.goblin.breeding_cum_1", 100),
            loop("animation.goblin.breeding_slow_1r", 38), // variant of the slow loop (breeding_slow1Done, 50 %)
            loop("animation.goblin.breeding_fast_1c", 25) // variant of the fast loop while the key stays held
    ),
    GOBLIN_NELSON(
            CompanionKind.GOBLIN, "nelson", true,
            phase("animation.goblin.nelson_intro", 38),
            loop("animation.goblin.nelson_slow", 31),
            loop("animation.goblin.nelson_fasts", 38),
            phase("animation.goblin.nelson_cum", 35),
            loop("animation.goblin.nelson_slow2", 31), // variant of the slow loop (nelson_slowDone, 50 %)
            loop("animation.goblin.nelson_fastc", 30) // variant of the fast loop while the key stays held
    ),

    /** Not real scenes: the two entries of the goblin catch menu ("use her" -> paizuri, "take ur stuff back"). */
    GOBLIN_USE_HER(CompanionKind.GOBLIN, "use_her", false, loop("animation.goblin.idle", 20)),
    GOBLIN_TAKE_BACK(CompanionKind.GOBLIN, "take_back", false, loop("animation.goblin.idle", 20)),

    /** Not a real scene: the "ride" entry of Galath's menu (mounts her and starts controlled flight). */
    GALATH_RIDE(
            CompanionKind.GALATH, "ride", false,
            loop("animation.galath.controlled_flight", 29)
    ),
    /** Tamed "cowgirl": original RAPE_INTRO -> RAPE_ON_GOING -> RAPE_CUM (no damage, no minigame when tamed). */
    GALATH_COWGIRL(
            CompanionKind.GALATH, "cowgirl", true,
            phase("animation.galath.rape_intro", 43), // original rapeIntroDone at 2.125 s
            loop("animation.galath.rape0", 15), // rape_switch at 0.75 s starts the next loop
            phase("animation.galath.rape_cum", 28),
            loop("animation.galath.rape1", 15), // original rape_switch picks a different one of rape0..2 each loop
            loop("animation.galath.rape2", 15)
    ),
    /** Tamed "anal" and the Galath form's "mating press": start straight in CORRUPT_SLOW (the intro is skipped). */
    GALATH_ANAL(
            CompanionKind.GALATH, "anal", true,
            loop("animation.galath.corrupt_slow", 28),
            loop("animation.galath.corrupt_soft", 24), // the fast loop starts soft; held past 0.5 s it turns hard
            phase("animation.galath.corrupt_cum", 30), // original f_.Y(): the scene ends 30 ticks in
            loop("animation.galath.corrupt_hard", 24)
    ),
    /** Original wild Galath catch (h8 rape): she drains the player with the succubus damage until he escapes or dies. */
    GALATH_WILD_RAPE(
            CompanionKind.GALATH, "wild_rape", true,
            phase("animation.galath.rape_intro", 43),
            loop("animation.galath.rape0", 15),
            loop("animation.galath.rape1", 15),
            loop("animation.galath.rape2", 15),
            phase("animation.galath.rape_cum", 28)
    ),
    /** Original corrupt scene on a paralysed Galath: ends with the coin she gives to her new master. */
    GALATH_WILD_CORRUPT(
            CompanionKind.GALATH, "wild_corrupt", true,
            phase("animation.galath.corrupt_intro", 29), // fp CORRUPT_INTRO: 29 ticks -> slow
            loop("animation.galath.corrupt_slow", 28),
            loop("animation.galath.corrupt_soft", 24),
            phase("animation.galath.corrupt_cum", 30),
            phase("animation.galath.give_coin", 140),
            loop("animation.galath.corrupt_hard", 24)
    ),

    /**
     * Original THREESOME_*: started from Galath's menu while she carries Manglelie. Both stand at the player's position;
     * the player controls Manglelie's scene, Galath loops her own animation (original PUSSY_LICKING) until it ends.
     */
    GALATH_THREESOME(
            CompanionKind.GALATH, "threesome", false,
            loop("animation.galath.pussy_licking", 20)
    ),
    MANGLELIE_THREESOME(
            CompanionKind.MANGLELIE, "threesome", true,
            loop("animation.shared.double_holding_slow", 20),
            loop("animation.shared.double_holding_hard", 10),
            phase("animation.shared.double_holding_cum", 64),
            phase("animation.shared.double_holding_soft", 20), // slow -> hard transition
            phase("animation.shared.double_holding_back", 15) // hard -> slow transition
    ),

    /**
     * The bed animations are authored for the combined Galath and Manglelie
     * model, which is the only geometry that contains both skeletons. Played on
     * Manglelie's own model the keyframes that exist there are static, so the
     * scene looked frozen. The scene therefore renders with that combined model
     * and Galath's animation file, where all four phases exist.
     */
    GALATH_MORNING(
            CompanionKind.GALATH, "morning", true,
            loop("animation.shared.bed_slow", 40),
            loop("animation.shared.bed_fast", 12),
            phase("animation.shared.bed_cum", 78),
            phase("animation.shared.bed_soft", 44), // slow -> fast transition
            phase("animation.shared.bed_back", 15) // fast -> slow transition
    ),
    /** Her daughter during the morning scene: she is part of the combined model, so this entity is not drawn. */
    MANGLELIE_BED(
            CompanionKind.MANGLELIE, "bed", false,
            loop("animation.shared.bed_slow", 40)
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
        return this.usesBoyCam(0);
    }

    /** Original fp.useBoyCam per state: first person sits on the girl's boyCam bone in exactly these states. */
    public boolean usesBoyCam(int phase) {
        return switch (this) {
            case ELLIE_CARRY, BIA_PRONE, ALLIE_DEEPTHROAT, ALLIE_REVERSE_COWGIRL, GALATH_WILD_RAPE, GALATH_COWGIRL,
                    GALATH_MORNING -> true; // GALATH_COWGIRL: the tamed scene plays the RAPE_* states too
            // not in the original (CORRUPT_* have no boyCam, the view stays on his real eye half a block off the floor,
            // inside her wing): the tamed anal views from the kneeling partner's head, the boyCam bone under Head2
            case GALATH_ANAL -> true;
            case GALATH_WILD_CORRUPT -> phase == 0 || phase == 4; // CORRUPT_INTRO and GIVE_COIN
            default -> false;
        };
    }

    /** The daughter entity is not drawn while the combined Galath and Manglelie model plays the morning scene. */
    public boolean hidesEntity() {
        return this == MANGLELIE_BED;
    }

    /** Model used instead of the companion's own one, or {@code null}. */
    public ResourceLocation modelOverride() {
        // original ce.a(em): THREESOME_SLOW / FAST / CUM draw the daughter with the combined model (c[2]) too; her own
        // model has none of the double_holding bones (body2, head3, ...), so she stood still in her idle pose
        return this == GALATH_MORNING || this == MANGLELIE_THREESOME
                ? ResourceLocation.fromNamespaceAndPath("sexmod", "geo/galath/galath_con_mang.geo.json")
                : null;
    }

    /**
     * True when the pose is authored as already lying on the mattress, so the
     * scene has to be anchored onto the bed. The doggy scenes instead open with
     * their own "go on bed" animation and keep their own starting position.
     */
    public boolean anchorsOnBed() {
        return false;
    }

    /** The strip / dress up scenes: they swap the model at the animation's 3 s keyframe. */
    public boolean isClothingToggle() {
        return this == JENNY_STRIP || this == ELLIE_STRIP || this == BIA_STRIP;
    }

    public boolean requiresBed() {
        return this == JENNY_DOGGY
                || this == BIA_ANAL || this == BIA_PRONE || this == ELLIE_COWGIRL || this == ELLIE_MISSIONARY || this == LUNA_SITTING;
    }

    /** The payment scenes that lead into the paid scene. */
    public boolean isPayment() {
        return this == JENNY_PAYMENT || this == LUNA_PAYMENT || this == KOBOLD_PAYMENT;
    }

    public boolean isPrelude() {
        return this.isClothingToggle() || this.isAlliePrelude() || this == BIA_TALK || this == JENNY_PAYMENT || this == LUNA_PAYMENT || this == KOBOLD_PAYMENT || this == ELLIE_DASH || this == ELLIE_HUG || this.isWaitScene();
    }

    /** Allie's wish scenes that lead into the next one. */
    public boolean isAlliePrelude() {
        return this == ALLIE_SUMMON || this == ALLIE_SUMMON_NORMAL || this == ALLIE_SUMMON_SAND
                || this == ALLIE_PREPARE || this == ALLIE_PREPARE_NORMAL
                || this == ALLIE_RICH || this == ALLIE_RICH_NORMAL;
    }

    /** True for the phase where Allie waits for the player to pick a wish (the menu cannot be dismissed). */
    public boolean isMenuWaitPhase(int phaseIndex) {
        return (this == ALLIE_SUMMON || this == ALLIE_SUMMON_NORMAL) && phaseIndex == 1;
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

    /** The scripted catch: no controls at all, not even the meter. */
    public boolean isWild() {
        return this == GALATH_WILD_RAPE;
    }

    public boolean isWildCorrupt() {
        return this == GALATH_WILD_CORRUPT;
    }

    /** Scenes during which the local player's movement keys are locked without attaching them (original d3.a(false)). */
    public boolean locksControls() {
        return this.isWild() || this == BIA_TALK || this.isPayment() || this == ELLIE_DASH || this == ELLIE_HUG || this.isAlliePrelude();
    }

    /**
     * The label the original passed to its menu (m: I18n.format(label)). Some were raw strings without a lang entry
     * (Galath's, the Kobold's, Bia's "doggy", Allie's "Reverse cowgirl", Ellie's "Face fuck"); they show as written.
     */
    public String menuLabelKey() {
        return switch (this) {
            case JENNY_BLOWJOB -> "action.names.blowjob"; // ex
            case JENNY_BOOBJOB -> "action.names.boobjob";
            case JENNY_DOGGY -> "action.names.doggy";
            case JENNY_STRIP, BIA_STRIP, ELLIE_STRIP -> "action.names.strip";
            case LUNA_SITTING -> "action.names.sex"; // eb
            case LUNA_TOUCH -> "action.names.touchboobs";
            case LUNA_HEADPAT, BIA_HEADPAT -> "action.names.headpat";
            case BIA_ANAL -> "action.names.anal"; // e0
            case BIA_PRONE -> "doggy";
            case BIA_TALK -> "action.names.talk";
            case ELLIE_CARRY -> "Face fuck"; // el
            case ELLIE_COWGIRL -> "action.names.cowgirl";
            case ELLIE_MISSIONARY -> "action.names.missionary";
            case GALATH_COWGIRL -> "cowgirl"; // f_
            case GALATH_ANAL -> "anal";
            case GALATH_THREESOME -> "threesome";
            case GALATH_RIDE -> "ride";
            case KOBOLD_ANAL -> "anal"; // ff
            case KOBOLD_BLOWJOB -> "oral";
            case KOBOLD_MATING_PRESS -> "mating";
            case ALLIE_RICH -> "action.names.makemerichallie"; // ev
            case ALLIE_DEEPTHROAT -> "action.names.deepthroat";
            case ALLIE_REVERSE_COWGIRL -> "Reverse cowgirl";
            case GOBLIN_USE_HER -> "action.names.goblin_use_her"; // e3 catch menu
            case GOBLIN_TAKE_BACK -> "action.names.goblin_take_back";
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
        if (this.transitionTarget(index) >= 0) {
            return PhaseRole.AUTO;
        }
        return index > this.lastLoopPhase() ? PhaseRole.FINALE : PhaseRole.AUTO;
    }

    /**
     * Original fp.hasPlayer: the girl's model draws the partner (steve bone) and the real player is not rendered. True
     * for every partner scene, and also for PAYMENT and Bia's TALK_* states.
     */
    public boolean showsPartnerModel() {
        return this.hasPartner || this.isPayment() || this == BIA_TALK;
    }

    public boolean hasInteractivePhases() {
        return this.hasPartner && !this.isWild() && this.firstLoopPhase() >= 0;
    }

    /** Loop phases that only vary a slow loop (the Kobold's left side); navigation treats them as their base loop. */
    public boolean isVariantPhase(int index) {
        return this.variantBase(index) >= 0;
    }

    /** The loop a variant phase stands in for, or -1. */
    private int variantBase(int index) {
        return switch (this) {
            case KOBOLD_BLOWJOB, LUNA_TOUCH -> index == 4 ? 1 : -1;
            case ELLIE_CARRY -> index >= 4 && index <= 6 ? 1 : -1;
            case GOBLIN_PAIZURI -> index == 5 ? 2 : index == 6 ? 3 : -1;
            case GOBLIN_BREEDING, GOBLIN_NELSON -> index == 4 ? 1 : index == 5 ? 2 : -1;
            case GALATH_COWGIRL -> index == 3 || index == 4 ? 1 : -1;
            case GALATH_ANAL -> index == 3 ? 1 : -1;
            case GALATH_WILD_CORRUPT -> index == 5 ? 2 : -1;
            case BIA_PRONE -> index == 5 || index == 6 ? 3 : -1;
            case ALLIE_REVERSE_COWGIRL -> index == 4 || index == 5 ? 1 : index >= 6 ? 2 : -1;
            case JENNY_DOGGY -> index == 4 ? 2 : -1;
            default -> -1;
        };
    }

    public int canonicalPhase(int index) {
        int base = this.variantBase(index);
        return base >= 0 ? base : index;
    }

    /**
     * Original per-loop random/held switches for the goblin scenes: when a slow loop ends she plays either
     * variant (50 %), and a fast loop whose key is still held continues with the "continues" animation.
     * Returns the phase to enter, or -1 to simply repeat the current one.
     */
    public int loopEndVariant(int current, boolean fastHeld, net.minecraft.util.RandomSource random) {
        if (this == GALATH_COWGIRL) {
            // original rape_switch: a random one of the three loops, never the same twice in a row
            int[] loops = {1, 3, 4};
            int pick;
            do {
                pick = loops[random.nextInt(3)];
            } while (pick == current);
            return pick;
        }
        if (this == ELLIE_CARRY && !fastHeld && this.canonicalPhase(current) == 1) {
            // el / ee carry_slowDone: aa = 1 + nextInt(4), redrawn until it differs
            int[] slows = {1, 4, 5, 6};
            int pick;
            do {
                pick = slows[random.nextInt(4)];
            } while (pick == current);
            return pick;
        }
        if (this == LUNA_TOUCH && !fastHeld && current == 4) {
            // eb / ed touch_boobs_slowDone in slow1 (its end): ae was on, so back to slow; the roll from slow is in
            // keyframeSwitch (its slowDone comes 7 ticks before the loop end)
            return 1;
        }
        int slow;
        int slowVariant;
        int fast;
        int fastVariant;
        switch (this) {
            case GOBLIN_PAIZURI -> { slow = 2; slowVariant = 5; fast = 3; fastVariant = 6; }
            case GOBLIN_BREEDING, GOBLIN_NELSON -> { slow = 1; slowVariant = 4; fast = 2; fastVariant = 5; }
            default -> { return -1; }
        }
        int base = this.canonicalPhase(current);
        if (base == slow && !fastHeld) {
            int pick = random.nextBoolean() ? slowVariant : slow;
            return pick == current ? -1 : pick;
        }
        return -1;
    }

    /** The slow loop and its flag-selected variants ({slow, variant...}), or null. */
    public int[] slowVariantPair() {
        return switch (this) {
            case GOBLIN_PAIZURI -> new int[] {2, 5};
            case GOBLIN_BREEDING, GOBLIN_NELSON, KOBOLD_BLOWJOB, LUNA_TOUCH -> new int[] {1, 4};
            case ELLIE_CARRY -> new int[] {1, 4, 5, 6};
            default -> null;
        };
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
            if (this.phases.get(index).loop() && !this.isVariantPhase(index)) {
                return index;
            }
        }
        return -1;
    }

    public int previousLoopPhase(int current) {
        if (this == GOBLIN_PAIZURI && this.canonicalPhase(current) == 2) {
            return -1; // the idle loop is only the first wait
        }
        for (int index = Math.min(this.canonicalPhase(current) - 1, this.phases.size() - 1); index >= 0; index--) {
            if (this.phases.get(index).loop() && !this.isVariantPhase(index)) {
                return index;
            }
        }
        return -1;
    }

    public int nextLoopPhase(int current) {
        if (this == GOBLIN_PAIZURI && this.canonicalPhase(current) == 1) {
            return 3; // original c(): the idle loop goes straight to the fast loop
        }
        for (int index = Math.max(0, this.canonicalPhase(current) + 1); index < this.phases.size(); index++) {
            if (this.phases.get(index).loop() && !this.isVariantPhase(index)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Original state machine: moving between the slow and the fast loop plays a one-shot transition animation
     * first (soft going up, back going down). Returns the phase index of that transition, or -1 when the scene
     * has none for this pair.
     */
    public int transitionVia(int from, int to) {
        if (this != GALATH_MORNING && this != MANGLELIE_THREESOME) {
            return -1;
        }
        if (from == 0 && to == 1) {
            return 3;
        }
        return from == 1 && to == 0 ? 4 : -1;
    }

    /**
     * Original per-keyframe TeleportPlayer (a8) for the partner: where the player's eye sits relative to the girl (before
     * the scene yaw + 180 rotation), the yaw added to the scene yaw and the pitch.
     */
    public record PartnerView(double x, double eyeY, double z, float yawAdd, float pitch, boolean feet) {
        public PartnerView(double x, double eyeY, double z, float yawAdd, float pitch) {
            this(x, eyeY, z, yawAdd, pitch, false);
        }

        /** A plain setPosition of the original: the player keeps their rotation. */
        public static PartnerView place(double x, double eyeY, double z) {
            return new PartnerView(x, eyeY, z, Float.NaN, Float.NaN, false);
        }

        public boolean keepsRotation() {
            return Float.isNaN(this.yawAdd);
        }
    }

    public PartnerView partnerView(int phase, int ticks) {
        return switch (this) {
            case KOBOLD_BLOWJOB -> phase != 0 ? null
                    : ticks == 2 || ticks == 203 ? new PartnerView(0.0D, 0.625D, -1.0D, 180.0F, 0.0F)
                    : ticks == 83 ? new PartnerView(0.5D, 0.5D, -0.6875D, 140.0F, 0.0F) : null;
            case KOBOLD_ANAL -> phase == 0 && ticks == 1 ? new PartnerView(0.0D, 0.5625D, 0.5625D, 0.0F, 0.0F) : null;
            case KOBOLD_MATING_PRESS -> phase == 0 && ticks == 1 ? new PartnerView(0.0D, 0.4375D, -0.6875D, 180.0F, 10.0F)
                    : phase == 3 && ticks == 11 ? new PartnerView(0.0D, 1.1875D, 0.125D, 180.0F, 70.0F) : null;
            // original e3 (start of the breeding intro): the player is moved, not turned (e3.w() 205 ticks later is
            // in CompanionEntity, since the intro is over by then)
            case GOBLIN_BREEDING -> phase == 0 && ticks == 1 ? PartnerView.place(0.0D, 0.44375D, -0.7875D) : null;
            // original e3.z(): the goblin takes the player's spot, the player sinks 0.5 and looks 70 degrees down
            // original el.U() (the partner arrives at her bed): missionary - he is turned to her yaw and put 0.1
            // behind her; cowgirl - turned to her yaw + 180 at (0, 1 - eye, -1.8125); the pitch is left alone
            // original e0 (the partner reaches her bed): anal - put at ck.a((-0.3, -1, -0.5), yaw) from her feet, not
            // turned; doggy - she moves 1 ahead (the anchor already is) and he goes to (0, 1.1875 - eye, 0.5) from her
            // old spot, turned to her yaw and 60 down
            // (not turned in the original: coming at her from the side left the first view on a wall; he now faces
            // her back, the pitch is his own)
            case BIA_ANAL -> phase == 0 && ticks == 1 ? new PartnerView(0.3D, -1.0D, 0.5D, 0.0F, Float.NaN, true) : null;
            case BIA_PRONE -> phase == 0 && ticks == 1 ? new PartnerView(0.0D, 1.1875D, 0.5D, 0.0F, 60.0F) : null;
            // original fn (wild slime): the player is turned to her yaw and put ck.a((0, 0, 0.65), yaw) from her
            case SLIME_DOGGY, SLIME_BLOWJOB -> phase == 0 && ticks == 1
                    ? new PartnerView(0.0D, 0.0D, -0.65D, 0.0F, Float.NaN, true) : null;
            case ELLIE_MISSIONARY -> phase == 0 && ticks == 1 ? new PartnerView(0.0D, 0.0D, -0.1D, 0.0F, Float.NaN, true) : null;
            case ELLIE_COWGIRL -> phase == 0 && ticks == 1 ? new PartnerView(0.0D, 1.0D, -1.8125D, 180.0F, Float.NaN) : null;
            case GOBLIN_PAIZURI -> phase == 0 && ticks == 1 ? new PartnerView(0.0D, -0.5D, 0.0D, 180.0F, 70.0F, true) : null;
            // original f_ (KNOCK_OUT_GROUND click): the player is put on her spot; H() on CORRUPT_INTRO turns him to her
            // yaw + 180, 80 down; setCamCorrupt (1.04 s) moves him to ck.a((0.5, 0.5 - eye, 0.4), yaw), yaw + 220, 15
            case GALATH_WILD_CORRUPT -> phase != 0 ? null
                    : ticks == 1 ? new PartnerView(0.0D, 0.0D, 0.0D, 180.0F, 80.0F, true)
                    : ticks == 21 ? new PartnerView(-0.5D, 0.5D, -0.4D, 220.0F, 15.0F) : null;
            default -> null;
        };
    }

    /** Original corruptSwitch / corrupt_hard_end: Galath's corrupt scenes change speed only when a loop ends. */
    public boolean switchesAtLoopEnd() {
        return this == GALATH_ANAL || this == GALATH_WILD_CORRUPT || this == BIA_PRONE;
    }

    /**
     * Original e0 keyframes, with the fast key held: doggySwitch (0.375 s into every soft loop of 1.4583 s) turns soft
     * into hard; doggyReset (0.625 s into every hard loop of 2.0417 s) restarts the hard loop as a different one of the
     * three variants (ag(): ah = a new random 1-3). Returns the phase to enter, or -1.
     */
    public int keyframeSwitch(int current, int phaseTicks, boolean fastHeld, net.minecraft.util.RandomSource random) {
        if (this == LUNA_TOUCH && current == 1 && phaseTicks == 79) {
            // eb / ed touch_boobs_slowDone at 3.92 s: half the time ae turns on and the name becomes slow1 right away
            return random.nextBoolean() ? 4 : -1;
        }
        if (this != BIA_PRONE || !fastHeld) {
            return -1;
        }
        if (current == 2 && crosses(phaseTicks, 7.5D, 29.1667D)) {
            return 3;
        }
        if (this.canonicalPhase(current) == 3 && crosses(phaseTicks, 12.5D, 40.8333D)) {
            int[] hard = {3, 5, 6};
            int pick;
            do {
                pick = hard[random.nextInt(3)];
            } while (pick == current);
            return pick;
        }
        return -1;
    }

    /**
     * Original "...Ready" keyframes: with the fast key held the fast animation is restarted there (N()), or switched to
     * its harder / continuing variant, so a held key never plays it to its end. Returns the phase to (re)enter - the
     * current one means a restart - or -1.
     */
    public int readySwitch(int current, int phaseTicks, boolean fastHeld) {
        if (!fastHeld) {
            return -1;
        }
        return switch (this) {
            case JENNY_BLOWJOB -> current == 2 && phaseTicks == 25 ? 2 : -1; // bjtReady 1.24 s
            case JENNY_BOOBJOB -> current == 2 && phaseTicks == 16 ? 2 : -1; // paizuriReady 0.8 s
            case JENNY_DOGGY -> (current == 2 || current == 4) && phaseTicks == 11 ? 4 : -1; // doggyfastReady 0.52 s
            case SLIME_BLOWJOB -> current == 1 && phaseTicks == 25 ? 1 : -1;
            case SLIME_DOGGY -> current == 2 && phaseTicks == 11 ? 2 : -1;
            case BEE_SEX -> current == 2 && phaseTicks == 27 ? 2 : -1; // sex_fastReady 1.32 s
            case KOBOLD_MATING_PRESS -> current == 1 && phaseTicks == 10 ? 2 // mating_press_softReady 0.5 s: hard
                    : current == 2 && phaseTicks == 19 ? 2 : -1; // mating_press_hardReady 0.92 s
            case GOBLIN_PAIZURI -> (current == 3 || current == 6) && phaseTicks == 15 ? 6 : -1; // paizuriFast(Continues)Ready
            // breeding_fast1Ready 0.75 s (fast_1s -> 1c, 1c restarts); also in breeding_cum_1 at 0.71 s, restarting it
            case GOBLIN_BREEDING -> (current == 2 || current == 5) && phaseTicks == 15 ? 5
                    : current == 3 && phaseTicks == 15 ? 3 : -1;
            case GOBLIN_NELSON -> current == 2 && phaseTicks == 17 ? 5 // neslon_fastSwitch 0.83 s
                    : current == 5 && phaseTicks == 9 ? 5 : -1; // neslon_fastBackSwitch 0.42 s
            case GALATH_ANAL -> current == 3 && phaseTicks == 10 ? 3 : -1; // corrupt_hard on the hard loop
            case GALATH_WILD_CORRUPT -> current == 5 && phaseTicks == 10 ? 5 : -1;
            default -> -1;
        };
    }

    /** True on the tick that passes {@code offset + k * period} (a keyframe of a looping animation). */
    private static boolean crosses(int ticks, double offset, double period) {
        return ticks >= 1 && Math.floor((ticks - offset) / period) > Math.floor((ticks - 1 - offset) / period);
    }

    /**
     * Original corrupt_hard_end: the fast loop always falls back to the slow one when it ends (even with the key held,
     * which then starts the fast loop again at the end of the slow one).
     */
    public boolean fastAlwaysEndsSlow() {
        // fp PRONE_DOGGY_HARD (34 ticks) / REVERSE_COWGIRL_FAST_* (34 / 39) fall back to the slow state unless a
        // keyframe restarted them
        return this.switchesAtLoopEnd() || this == ALLIE_REVERSE_COWGIRL;
    }

    /**
     * Original corrupt_hard keyframe (0.5 s into the fast loop): a fast key still held turns the soft loop into the hard
     * one. Returns the phase to switch to, or -1.
     */
    public int loopMidVariant(int current, int phaseTicks, boolean fastHeld) {
        if (!fastHeld || phaseTicks != 10) {
            return -1;
        }
        return switch (this) {
            case GALATH_ANAL -> current == 1 ? 3 : -1;
            case GALATH_WILD_CORRUPT -> current == 2 ? 5 : -1;
            default -> -1;
        };
    }

    /** The loop a one-shot transition phase leads into, or -1 if the phase is not a transition. */
    public int transitionTarget(int phaseIndex) {
        if (this == KOBOLD_BLOWJOB) {
            // blowjobSlow{a4 ? R : L}Switch ends on the other side (endSwitch flips a4)
            return phaseIndex == 5 ? 1 : phaseIndex == 6 ? 4 : -1;
        }
        if (this != GALATH_MORNING && this != MANGLELIE_THREESOME) {
            return -1;
        }
        return phaseIndex == 3 ? 1 : phaseIndex == 4 ? 0 : -1;
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
        /**
         * Original em.a(name, loop, event): the class passes LOOP or HOLD_ON_LAST_FRAME explicitly, which overrides the
         * animation file. Most scene animations, intros and finishes included, are LOOP (the scene moves on from their
         * keyframes); the ones below are held on their last frame. The Bia form's anal wait and the Luna form's
         * sitting finish loop where the NPCs hold.
         */
        public RawAnimation rawAnimation(boolean playerForm) {
            boolean hold = HOLD_ON_LAST_FRAME.contains(this.animation)
                    && !(playerForm && FORM_LOOPS.contains(this.animation));
            return hold
                    ? RawAnimation.begin().thenPlayAndHold(this.animation)
                    : RawAnimation.begin().thenLoop(this.animation);
        }
    }

    private static final java.util.Set<String> HOLD_ON_LAST_FRAME = java.util.Set.of(
            "animation.allie.deepthroat_cum", "animation.allie.deepthroat_normal_prepare",
            "animation.allie.deepthroat_prepare", "animation.allie.deepthroat_start", "animation.allie.rich",
            "animation.allie.rich_normal", "animation.allie.summon", "animation.allie.summon_normal",
            "animation.allie.summon_sand", "animation.bee.sex_cum", "animation.bee.sex_start", "animation.bia.anal_cum",
            "animation.bia.anal_prepare", "animation.bia.anal_wait", "animation.bia.headpat", "animation.bia.sitdown",
            "animation.bia.strip", "animation.cat.payment", "animation.cat.sitting_cum", "animation.cat.sitting_intro",
            "animation.cat.touch_boobs_cum", "animation.cat.touch_boobs_intro", "animation.cat.wait",
            "animation.ellie.carry_intro", "animation.ellie.cowgirlstart", "animation.ellie.dash", "animation.ellie.hug",
            "animation.ellie.hugselected", "animation.ellie.missionary_cum", "animation.ellie.missionary_start",
            "animation.ellie.sitdown", "animation.ellie.strip", "animation.jenny.blowjobcum",
            "animation.jenny.blowjobintro", "animation.jenny.doggycum", "animation.jenny.doggygoonbed",
            "animation.jenny.doggystart", "animation.jenny.paizuri_cum", "animation.jenny.paizuri_start",
            "animation.jenny.payment", "animation.jenny.strip", "animation.kobold.analStart",
            "animation.kobold.blowjobCum", "animation.kobold.blowjobStart", "animation.kobold.mating_press_start",
            "animation.slime.blowjobcum", "animation.slime.doggycum", "animation.slime.doggystart");

    private static final java.util.Set<String> FORM_LOOPS = java.util.Set.of(
            "animation.bia.anal_wait", "animation.cat.sitting_cum");
}
