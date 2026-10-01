package com.trolmastercard.sexmod.entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Original em/cv: the main animation controller blends between states with the transitionTick of the animation state
 * (fp enum, first constructor argument): 5 ticks without a state, otherwise per state (mostly 2, some 0, 5, 7 or 10).
 * Only the values that differ from the default of 2 are listed.
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
    }

    private TransitionTicks() {
    }

    public static int forAnimation(String animation) {
        return TICKS.getOrDefault(animation, 2);
    }
}
