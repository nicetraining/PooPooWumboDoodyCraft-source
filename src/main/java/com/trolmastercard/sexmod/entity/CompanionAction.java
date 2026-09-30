package com.trolmastercard.sexmod.entity;

import java.util.Locale;

/**
 * What a companion is doing right now, outside of scripted scenes. Mirrors the base states of the
 * original (its attack, bow, pearl throw, downed, ride and sit actions); each one selects an animation.
 */
public enum CompanionAction {
    NONE,
    ATTACK,
    BOW,
    THROW_PEARL,
    DOWNED,
    RIDE,
    SIT,
    GALATH_SUMMON,
    GALATH_DESUMMON;

    public String animationSuffix(int attackVariant) {
        return switch (this) {
            case ATTACK -> "attack" + Math.floorMod(attackVariant, 3);
            case BOW -> "bowcharge";
            case THROW_PEARL -> "throwpearl";
            case DOWNED -> "downed";
            case RIDE -> "ride";
            case SIT -> "sit";
            case GALATH_SUMMON -> "summon";
            case GALATH_DESUMMON -> "desummon";
            case NONE -> "idle";
        };
    }

    public static CompanionAction byName(String value) {
        if (value != null) {
            for (CompanionAction action : values()) {
                if (action.name().equals(value.toUpperCase(Locale.ROOT))) {
                    return action;
                }
            }
        }
        return NONE;
    }
}
