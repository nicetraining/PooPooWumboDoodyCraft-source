package com.fartypoopy.sexmod.custommodel;

/**
 * Original gw: where a custom model is worn. Each slot is attached to a bone that every girl model carries
 * ({@code customHead}, {@code customShoeL}, ...); CUSTOM_BONE models name their own bone in the cfg. GIRL_SPECIFIC is not
 * a wear slot: it marks the per-girl appearance rows (Kobold / Goblin) of the wardrobe. {@code iconX} is the column of
 * the slot's icon in {@code textures/gui/clothing_icons.png}.
 */
public enum WearType {
    GIRL_SPECIFIC(0, null),
    HEAD(0, "customHead"),
    FOOT_L(60, "customShoeL"),
    FOOT_R(80, "customShoeR"),
    HAND_L(100, "customHandL"),
    HAND_R(120, "customHandR"),
    CUSTOM_BONE(140, null);

    /** Original gw.SEPARATOR: separates the worn models inside a model code. */
    public static final String SEPARATOR = "#";

    public final int iconX;
    public final String boneName;

    WearType(int iconX, String boneName) {
        this.iconX = iconX;
        this.boneName = boneName;
    }
}
