package com.fartypoopy.sexmod.entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Original em/cv: the transitionTick of the animation state (fp enum, first constructor argument; mostly 2, some 0, 5, 7
 * or 10). em.a() zeroes GeckoLib's transition on every play call, so nothing is blended: a new animation snaps to its
 * first frame, which is held for this many ticks (cv restores the value after processing) before it plays on. Only the
 * values that differ from the default of 2 are listed.
 */
public final class TransitionTicks {
    private static final Map<String, Integer> TICKS = new HashMap<>();

    static {
        TICKS.put("animation.allie.deepthroat_start", 0);
        TICKS.put("animation.allie.reverse_cowgirl_cum", 0);
        TICKS.put("animation.allie.reverse_cowgirl_start", 0);
        TICKS.put("animation.allie.rich_normal", 0);
        TICKS.put("animation.allie.summon", 0);
        TICKS.put("animation.allie.summon_normal", 0);
        TICKS.put("animation.allie.summon_sand", 0);
        TICKS.put("animation.allie.summon_wait", 0);
        TICKS.put("animation.bia.anal_fast", 0);
        TICKS.put("animation.bia.anal_prepare", 5);
        TICKS.put("animation.bia.anal_start", 0);
        TICKS.put("animation.bia.anal_wait", 0);
        TICKS.put("animation.bia.headpat", 0);
        TICKS.put("animation.bia.prone_doggy_cum", 0);
        TICKS.put("animation.bia.prone_doggy_intro", 0);
        TICKS.put("animation.bia.prone_doggy_soft", 0);
        TICKS.put("animation.bia.sitdownidle", 0);
        TICKS.put("animation.bia.strip", 5);
        TICKS.put("animation.bia.wave", 0);
        TICKS.put("animation.bia.wave_idle", 0);
        TICKS.put("animation.cat.head_pat", 0);
        TICKS.put("animation.cat.payment", 5);
        TICKS.put("animation.cat.touch_boobs_intro", 0);
        TICKS.put("animation.ellie.carry_cum", 0);
        TICKS.put("animation.ellie.carry_fast", 0);
        TICKS.put("animation.ellie.carry_intro", 0);
        TICKS.put("animation.ellie.cowgirlfast", 10);
        TICKS.put("animation.ellie.cowgirlstart", 0);
        TICKS.put("animation.ellie.hugidle", 0);
        TICKS.put("animation.ellie.hugselected", 0);
        TICKS.put("animation.ellie.missionary_start", 0);
        TICKS.put("animation.ellie.sitdownidle", 0);
        TICKS.put("animation.ellie.strip", 5);
        TICKS.put("animation.galath.controlled_flight", 0);
        TICKS.put("animation.galath.corrupt_cum", 0);
        TICKS.put("animation.galath.corrupt_intro", 0);
        TICKS.put("animation.galath.corrupt_slow", 0);
        TICKS.put("animation.galath.give_coin", 0);
        TICKS.put("animation.galath.pussy_licking", 0);
        TICKS.put("animation.galath.rape_cum", 0);
        TICKS.put("animation.galath.rape_intro", 0);
        TICKS.put("animation.goblin.breeding_cum_1", 0);
        TICKS.put("animation.goblin.breeding_intro_1", 0);
        TICKS.put("animation.goblin.nelson_cum", 0);
        TICKS.put("animation.goblin.nelson_intro", 0);
        TICKS.put("animation.goblin.nelson_slow", 0);
        TICKS.put("animation.goblin.paizuri_cum", 0);
        TICKS.put("animation.goblin.paizuri_fast", 0);
        TICKS.put("animation.goblin.paizuri_slow", 0);
        TICKS.put("animation.goblin.paizuri_start", 0);
        TICKS.put("animation.jenny.blowjobcum", 0);
        TICKS.put("animation.jenny.doggystart", 0);
        TICKS.put("animation.jenny.doggywait", 0);
        TICKS.put("animation.jenny.paizuri_cum", 0);
        TICKS.put("animation.jenny.paizuri_fast", 0);
        TICKS.put("animation.jenny.paizuri_slow", 0);
        TICKS.put("animation.jenny.paizuri_start", 0);
        TICKS.put("animation.jenny.payment", 5);
        TICKS.put("animation.jenny.strip", 5);
        TICKS.put("animation.jenny.wave", 0);
        TICKS.put("animation.jenny.wave_idle", 0);
        TICKS.put("animation.kobold.mating_press_hard", 0);
        TICKS.put("animation.kobold.mating_press_soft", 0);
        TICKS.put("animation.kobold.mating_press_start", 0);
        TICKS.put("animation.slime.blowjobcum", 0);
        TICKS.put("animation.slime.doggystart", 0);
        // NULL-state animations on the action controller (fn idle / jumps, f8 riding her mother) and Galath's remaining
        // 0-tick states (FLY, SUMMON_SKELETON, GALATH_DE_SUMMON)
        TICKS.put("animation.galath.desummon_standing", 0);
        TICKS.put("animation.galath.idle_flying", 0);
        TICKS.put("animation.galath.summon_skeleton", 0);
        TICKS.put("animation.galath.summon_skeletonMirrored", 0);
        TICKS.put("animation.slime.idle", 0);
        TICKS.put("animation.slime.jumpstart", 0);
        TICKS.put("animation.slime.jumpair", 0);
        TICKS.put("animation.slime.jumpend", 0);
        TICKS.put("animation.manglelie.running", 5); // f8 RUN
        TICKS.put("animation.manglelie.scared_run", 0); // f8 movement controller
        // em.a(name, loop, event, true): f8's threesome and f_'s morning / sitting animations skip the state
        // transition and play with transitionLengthTicks = 0
        TICKS.put("animation.shared.double_holding_slow", 0);
        TICKS.put("animation.shared.double_holding_soft", 0);
        TICKS.put("animation.shared.double_holding_back", 0);
        TICKS.put("animation.shared.double_holding_cum", 0);
        TICKS.put("animation.shared.bed_slow", 0);
        TICKS.put("animation.shared.bed_fast", 0);
        TICKS.put("animation.galath.masterbating_sitting", 0);
        // Names built from the state's base name plus a variant flag (the state's transitionTick applies to all)
        TICKS.put("animation.ellie.carry_slow1", 0);
        TICKS.put("animation.ellie.carry_slow2", 0);
        TICKS.put("animation.ellie.carry_slow3", 0);
        TICKS.put("animation.ellie.carry_slow4", 0);
        TICKS.put("animation.goblin.breeding_slow_1l", 0);
        TICKS.put("animation.goblin.breeding_slow_1r", 0);
        TICKS.put("animation.goblin.breeding_fast_1s", 0);
        TICKS.put("animation.goblin.breeding_fast_1c", 0);
        TICKS.put("animation.goblin.nelson_slow2", 0);
        TICKS.put("animation.goblin.nelson_fasts", 0);
        TICKS.put("animation.goblin.nelson_fastc", 0);
        TICKS.put("animation.goblin.paizuri_slow2", 0);
        // e5 NULL, f_ MASTERBATE_SITTING, f8 THREESOME_FAST, ec ATTACK / RIDE
        TICKS.put("animation.allie.null", 0);
        TICKS.put("animation.galath.pussy_licking_back", 0);
        TICKS.put("animation.shared.double_holding_hard", 0);
        TICKS.put("animation.slime.attack", 0);
        TICKS.put("animation.slime.ride", 0);
        TICKS.put("animation.manglelie.sit_on_galath", 0);
        // fp.ATTACK (attack0..2)
        TICKS.put("animation.allie.attack0", 0);
        TICKS.put("animation.allie.attack1", 0);
        TICKS.put("animation.allie.attack2", 0);
        TICKS.put("animation.bee.attack0", 0);
        TICKS.put("animation.bee.attack1", 0);
        TICKS.put("animation.bee.attack2", 0);
        TICKS.put("animation.bia.attack0", 0);
        TICKS.put("animation.bia.attack1", 0);
        TICKS.put("animation.bia.attack2", 0);
        TICKS.put("animation.ellie.attack0", 0);
        TICKS.put("animation.ellie.attack1", 0);
        TICKS.put("animation.ellie.attack2", 0);
        TICKS.put("animation.goblin.attack0", 0);
        TICKS.put("animation.goblin.attack1", 0);
        TICKS.put("animation.goblin.attack2", 0);
        TICKS.put("animation.jenny.attack0", 0);
        TICKS.put("animation.jenny.attack1", 0);
        TICKS.put("animation.jenny.attack2", 0);
        TICKS.put("animation.slime.attack0", 0);
        TICKS.put("animation.slime.attack1", 0);
        TICKS.put("animation.slime.attack2", 0);
        // batch 29: the remaining states' transitionTick (fp), mapped through each class's animation predicate
        TICKS.put("animation.allie.deepthroat_prepare", 0);
        TICKS.put("animation.allie.reverse_cowgirl_fastc1", 0);
        TICKS.put("animation.allie.reverse_cowgirl_fastc2", 0);
        TICKS.put("animation.allie.reverse_cowgirl_fastc3", 0);
        TICKS.put("animation.allie.reverse_cowgirl_fasts", 0);
        TICKS.put("animation.allie.reverse_cowgirl_slow1", 0);
        TICKS.put("animation.allie.reverse_cowgirl_slow2", 0);
        TICKS.put("animation.allie.reverse_cowgirl_slow3", 0);
        TICKS.put("animation.allie.rich", 0);
        TICKS.put("animation.bee.sex_fast", 0);
        TICKS.put("animation.bee.sex_slow", 0);
        TICKS.put("animation.bee.sex_start", 0);
        TICKS.put("animation.bee.throw_pearl", 0);
        TICKS.put("animation.bia.downed", 7);
        TICKS.put("animation.bia.prone_doggy_hard1", 0);
        TICKS.put("animation.bia.prone_doggy_hard2", 0);
        TICKS.put("animation.bia.prone_doggy_hard3", 0);
        TICKS.put("animation.bia.ride", 0);
        TICKS.put("animation.bia.sit", 0);
        TICKS.put("animation.bia.talk_horny", 5);
        TICKS.put("animation.bia.talk_horny2", 5);
        TICKS.put("animation.bia.talk_idle", 0);
        TICKS.put("animation.bia.talk_idle2", 0);
        TICKS.put("animation.bia.throwpearl", 0);
        TICKS.put("animation.cat.downed", 7);
        TICKS.put("animation.cat.eat_fishing", 0);
        TICKS.put("animation.cat.idle_fishing", 0);
        TICKS.put("animation.cat.sit", 0);
        TICKS.put("animation.cat.sitting_cum", 5);
        TICKS.put("animation.cat.sitting_fast", 5);
        TICKS.put("animation.cat.sitting_intro", 0);
        TICKS.put("animation.cat.sitting_slow", 5);
        TICKS.put("animation.cat.start_fishing", 5);
        TICKS.put("animation.cat.throw_away", 0);
        TICKS.put("animation.cat.throwpearl", 0);
        TICKS.put("animation.cat.wait", 0);
        TICKS.put("animation.ellie.cowgirlslow2", 10);
        TICKS.put("animation.ellie.downed", 7);
        TICKS.put("animation.ellie.ride", 0);
        TICKS.put("animation.ellie.sit", 0);
        TICKS.put("animation.ellie.throwpearl", 0);
        TICKS.put("animation.galath.attack", 0);
        TICKS.put("animation.galath.boost", 3);
        TICKS.put("animation.galath.corrupt_hard", 0);
        TICKS.put("animation.galath.corrupt_soft", 0);
        TICKS.put("animation.galath.desummon", 0);
        TICKS.put("animation.galath.hug_mang", 0);
        TICKS.put("animation.galath.knocked_out", 3);
        TICKS.put("animation.galath.knocked_out_stand_up", 0);
        TICKS.put("animation.galath.knockout_air", 5);
        TICKS.put("animation.galath.masterbate", 0);
        TICKS.put("animation.galath.masterbating_sitting_cum", 0);
        TICKS.put("animation.galath.pussy_licking_forward", 0);
        TICKS.put("animation.galath.rape0", 0);
        TICKS.put("animation.galath.rape1", 0);
        TICKS.put("animation.galath.rape2", 0);
        TICKS.put("animation.galath.rape_charge", 0);
        TICKS.put("animation.galath.rape_cum_idle", 0);
        TICKS.put("animation.galath.rape_prepare", 0);
        TICKS.put("animation.galath.running", 5);
        TICKS.put("animation.galath.sit", 0);
        TICKS.put("animation.galath.summon", 0);
        TICKS.put("animation.goblin.await_pick_up", 0);
        TICKS.put("animation.goblin.breeding_2", 0);
        TICKS.put("animation.goblin.breeding_cum_2", 0);
        TICKS.put("animation.goblin.breeding_cum_3", 0);
        TICKS.put("animation.goblin.breeding_fast_3", 5);
        TICKS.put("animation.goblin.breeding_intro_2", 0);
        TICKS.put("animation.goblin.breeding_intro_3", 0);
        TICKS.put("animation.goblin.breeding_slow_3", 5);
        TICKS.put("animation.goblin.catch_1person", 0);
        TICKS.put("animation.goblin.catch_1personBj", 0);
        TICKS.put("animation.goblin.catch_1personBj_idle", 0);
        TICKS.put("animation.goblin.catch_3person", 0);
        TICKS.put("animation.goblin.catch_3personBj", 0);
        TICKS.put("animation.goblin.catch_3personBj_idle", 0);
        TICKS.put("animation.goblin.jump_1", 0);
        TICKS.put("animation.goblin.jump_2", 0);
        TICKS.put("animation.goblin.jump_3", 0);
        TICKS.put("animation.goblin.paizuri_fast_countinues", 0);
        TICKS.put("animation.goblin.paizuri_idle", 0);
        TICKS.put("animation.goblin.pick_up_1person", 0);
        TICKS.put("animation.goblin.pick_up_3person", 0);
        TICKS.put("animation.goblin.running", 5);
        TICKS.put("animation.goblin.shoulder_idle", 0);
        TICKS.put("animation.goblin.sit", 0);
        TICKS.put("animation.goblin.stand_up", 0);
        TICKS.put("animation.goblin.throw_1person", 0);
        TICKS.put("animation.goblin.throw_3person", 0);
        TICKS.put("animation.goblin.thrown", 0);
        TICKS.put("animation.jenny.downed", 7);
        TICKS.put("animation.jenny.ride", 0);
        TICKS.put("animation.jenny.sit", 0);
        TICKS.put("animation.jenny.throwpearl", 0);
        TICKS.put("animation.kobold.analHard", 0);
        TICKS.put("animation.kobold.analSoft", 0);
        TICKS.put("animation.kobold.analStart", 0);
        TICKS.put("animation.kobold.attack", 0);
        TICKS.put("animation.kobold.blowjobCum", 0);
        TICKS.put("animation.kobold.fall_tree", 0);
        TICKS.put("animation.kobold.paymentBackpack", 5);
        TICKS.put("animation.kobold.sit", 0);
        TICKS.put("animation.kobold.sleep", 5);
        TICKS.put("animation.shared.bed_back", 0);
        TICKS.put("animation.shared.bed_cum", 0);
        TICKS.put("animation.shared.bed_soft", 0);
        TICKS.put("animation.slime.doggywait", 0);
        TICKS.put("animation.slime.sit", 0);
        TICKS.put("animation.slime.strip", 5);
    }

    private TransitionTicks() {
    }

    /** Movement-controller animations (original em.E): played in the NULL state (transitionTick 0), no hold. */
    private static final java.util.Set<String> MOVEMENT = java.util.Set.of("idle", "idle2", "walk", "fastwalk", "run",
            "fly", "fly2", "backwards_walk", "crouchidle", "crouchwalk", "crouch_idle", "crouch_walk", "idle_armed",
            "run_armed", "tail", "idle_has_chest");

    /** The first-frame hold for an animation: its state's transitionTick, 0 for the movement animations, else 2. */
    public static int forState(String animation) {
        Integer listed = TICKS.get(animation);
        if (listed != null) {
            return listed;
        }
        return MOVEMENT.contains(animation.substring(animation.lastIndexOf('.') + 1)) ? 0 : 2;
    }

}
