package com.trolmastercard.sexmod.entity;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Original e3/e4 "model string": ten dash-terminated two-digit fields that pick the goblin's ears, hair, colours and crown.
 * Fields: 0 ear group, 1 left ear sub-group, 2 right ear sub-group, 3 left accessory subset, 4 right accessory subset,
 * 5 hair style, 6 hair colour (g5), 7 body colour (by), 8 eye colour (eh), 9 crown (queen).
 */
public final class GoblinModel {
    public static final int[] OPTIONS = {4, 3, 3, 16, 16, 6, 8, 5, 6};

    /** Original by enum (body colours). */
    public static final int[][] BODY = {{213, 239, 150}, {189, 165, 91}, {160, 183, 135}, {234, 176, 102}, {187, 203, 252}};
    /** Original g5 enum (hair colours). */
    public static final int[][] HAIR = {{103, 39, 123}, {251, 153, 56}, {30, 33, 38}, {88, 83, 186}, {63, 35, 34},
            {247, 102, 109}, {241, 69, 49}, {75, 143, 106}};
    /** Original eh enum (eye colours). */
    public static final int[][] EYES = {{255, 0, 0}, {132, 30, 156}, {243, 247, 0}, {105, 60, 9}, {0, 206, 217}, {0, 0, 255}};

    private GoblinModel() {
    }

    private static void field(StringBuilder sb, int value) {
        if (value < 10) {
            sb.append(0);
        }
        sb.append(value).append('-');
    }

    /** Original e3.a(StringBuilder): a random wild goblin (accessory subsets 0..8, no crown). */
    public static String random(RandomSource random) {
        StringBuilder sb = new StringBuilder();
        field(sb, random.nextInt(3 + 1));
        field(sb, random.nextInt(2 + 1));
        field(sb, random.nextInt(2 + 1));
        field(sb, random.nextInt(8 + 1));
        field(sb, random.nextInt(8 + 1));
        field(sb, random.nextInt(5 + 1));
        field(sb, random.nextInt(HAIR.length));
        field(sb, random.nextInt(BODY.length));
        field(sb, random.nextInt(EYES.length));
        field(sb, 0);
        return sb.toString();
    }

    /** Original e3.b(StringBuilder): the queen (fixed accessories 7 and 7, crown). */
    public static String queen(RandomSource random) {
        StringBuilder sb = new StringBuilder();
        field(sb, random.nextInt(3 + 1));
        field(sb, random.nextInt(2 + 1));
        field(sb, random.nextInt(2 + 1));
        field(sb, 7);
        field(sb, 7);
        field(sb, random.nextInt(5 + 1));
        field(sb, random.nextInt(HAIR.length));
        field(sb, random.nextInt(BODY.length));
        field(sb, random.nextInt(EYES.length));
        field(sb, 1);
        return sb.toString();
    }

    /** Original e3.a(StringBuilder, int): a robber or guard of a queen, same body colour as the queen. */
    public static String withBodyColor(RandomSource random, int bodyColor) {
        StringBuilder sb = new StringBuilder();
        field(sb, random.nextInt(3 + 1));
        field(sb, random.nextInt(2 + 1));
        field(sb, random.nextInt(2 + 1));
        field(sb, random.nextInt(7 + 1));
        field(sb, random.nextInt(7 + 1));
        field(sb, random.nextInt(5 + 1));
        field(sb, random.nextInt(HAIR.length));
        field(sb, bodyColor);
        field(sb, random.nextInt(EYES.length));
        field(sb, 0);
        return sb.toString();
    }

    /** The ten fields; anything malformed gives an empty array. */
    public static int[] parse(String model) {
        if (model == null || model.isEmpty()) {
            return new int[0];
        }
        String[] parts = model.split("-");
        int[] result = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                result[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException e) {
            return new int[0];
        }
        return result;
    }

    public static int bodyColorIndex(String model) {
        int[] fields = parse(model);
        return fields.length > 7 ? fields[7] : 2;
    }

    /** Original dy.a(int size, String value): the value-th subset of the {0..size-1} children (element 0 is the high bit). */
    public static List<Integer> subset(int size, int value) {
        List<List<Integer>> all = new ArrayList<>();
        collect(0, new ArrayList<>(), size - 1, all);
        if (all.isEmpty()) {
            return List.of();
        }
        int index = value;
        while (index >= all.size()) {
            index -= all.size();
        }
        return all.get(index);
    }

    private static void collect(int i, List<Integer> current, int last, List<List<Integer>> out) {
        if (i > last) {
            out.add(current);
            return;
        }
        List<Integer> with = new ArrayList<>(current);
        collect(i + 1, current, last, out);
        with.add(i);
        collect(i + 1, with, last, out);
    }

    /** Colour (rgb) of a bone by its name, or -1 for "no tint" (original dy.a(String)). */
    public static int tint(String bone, int[] fields, java.util.Set<String> skinBones, java.util.Set<String> hairLikeBones) {
        if (fields.length < 8) {
            return -1;
        }
        if (bone.contains("band")) {
            return -1;
        }
        if (bone.contains("eyeColor")) {
            return rgb(EYES, fields.length > 8 ? fields[8] : 0);
        }
        if (bone.contains("variant") || bone.contains("boob")) {
            return rgb(BODY, fields[7]);
        }
        if (bone.contains("hair")) {
            return rgb(HAIR, fields[6]);
        }
        if (skinBones.contains(bone)) {
            return rgb(BODY, fields[7]);
        }
        if (hairLikeBones.contains(bone)) {
            return rgb(HAIR, fields[6]);
        }
        return -1;
    }

    private static int rgb(int[][] table, int index) {
        int[] c = table[Math.floorMod(index, table.length)];
        return (c[0] << 16) | (c[1] << 8) | c[2];
    }
}
