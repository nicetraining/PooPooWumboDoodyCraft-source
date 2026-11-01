package com.fartypoopy.sexmod.tribe;

import net.minecraft.ChatFormatting;

/** The original EyeAndKoboldColor table: body/eye colour, staff-compass colour id and the chat colour of a tribe. */
public enum KoboldColor {
    GREEN(69, 141, 113, 91, 167, 128, 9, ChatFormatting.DARK_GREEN),
    YELLOW(241, 177, 77, 255, 226, 170, 4, ChatFormatting.YELLOW),
    RED(230, 27, 57, 253, 232, 239, 14, ChatFormatting.RED),
    PURPLE(196, 148, 207, 246, 188, 96, 10, ChatFormatting.DARK_PURPLE),
    LIGHT_GREEN(170, 208, 47, 230, 214, 104, 5, ChatFormatting.GREEN),
    OLD_BLUE(173, 138, 128, 118, 151, 180, 2, ChatFormatting.LIGHT_PURPLE),
    DARK_GREY(92, 92, 110, 198, 193, 165, 7, ChatFormatting.DARK_GRAY),
    BROWN(200, 145, 112, 253, 228, 198, 12, ChatFormatting.GOLD),
    DARK_BLUE(65, 84, 116, 104, 137, 146, 11, ChatFormatting.DARK_BLUE),
    LIGHT_BLUE(100, 163, 206, 138, 235, 242, 3, ChatFormatting.DARK_AQUA),
    SILVER(136, 136, 134, 255, 255, 255, 0, ChatFormatting.GRAY);

    private final int main;
    private final int secondary;
    private final int woolMeta;
    private final ChatFormatting chat;

    KoboldColor(int r, int g, int b, int r2, int g2, int b2, int woolMeta, ChatFormatting chat) {
        this.main = (r << 16) | (g << 8) | b;
        this.secondary = (r2 << 16) | (g2 << 8) | b2;
        this.woolMeta = woolMeta;
        this.chat = chat;
    }

    public int mainRgb() {
        return this.main;
    }

    public int secondaryRgb() {
        return this.secondary;
    }

    public int woolMeta() {
        return this.woolMeta;
    }

    public ChatFormatting chat() {
        return this.chat;
    }

    /** Original safeValueOf: anything unknown is purple. */
    public static KoboldColor byName(String name) {
        if (name != null) {
            for (KoboldColor color : values()) {
                if (color.name().equalsIgnoreCase(name.strip())) {
                    return color;
                }
            }
        }
        return PURPLE;
    }

    public static KoboldColor byWoolMeta(int meta) {
        for (KoboldColor color : values()) {
            if (color.woolMeta == meta) {
                return color;
            }
        }
        return PURPLE;
    }
}
