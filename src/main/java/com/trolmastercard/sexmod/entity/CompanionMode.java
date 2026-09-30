package com.trolmastercard.sexmod.entity;

import java.util.Locale;

/**
 * Server-authoritative movement modes shared by companions and kobold tribes.
 */
public enum CompanionMode {
    WANDER,
    FOLLOW,
    STAY,
    GUARD,
    HOME;

    public String serializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static CompanionMode byName(String value) {
        if (value != null) {
            for (CompanionMode mode : values()) {
                if (mode.name().equalsIgnoreCase(value)
                        || mode.serializedName().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
        }

        return STAY;
    }

    public CompanionMode next() {
        CompanionMode[] modes = values();
        return modes[(this.ordinal() + 1) % modes.length];
    }
}
