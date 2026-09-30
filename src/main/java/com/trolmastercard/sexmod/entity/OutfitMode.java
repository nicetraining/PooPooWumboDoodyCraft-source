package com.trolmastercard.sexmod.entity;

import java.util.Locale;

/**
 * Safe, bounded replacement for the arbitrary custom-model paths accepted by
 * the legacy editor.
 */
public enum OutfitMode {
    DEFAULT,
    ALTERNATE,
    ARMORED;

    public String serializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static OutfitMode byName(String value) {
        if (value != null) {
            for (OutfitMode mode : values()) {
                if (mode.name().equalsIgnoreCase(value)
                        || mode.serializedName().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
        }

        return DEFAULT;
    }

    public OutfitMode next() {
        OutfitMode[] modes = values();
        return modes[(this.ordinal() + 1) % modes.length];
    }
}
