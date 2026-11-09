package com.fartypoopy.sexmod.util;

/**
 * Texture smoothing for the girls' skins. Their 4096 textures sit on a 64 x 64 UV layout: part of the art is painted at full
 * resolution (outlines, faces, curves), part is pixel art blown up into flat 64 x 64 texel cells (hair, clothes, shading),
 * which is what makes them look blocky up close.
 * <p>
 * {@link #smoothCells} blends each flat cell with its similar flat neighbours (bilinear between cell centres), so pixel-art
 * shading turns into soft gradients. Painted cells, see-through cells and strong colour edges (hair against skin, a dark
 * line next to a light one) are left as they were, which keeps the line art and the cube faces' outlines sharp. Flatness is
 * judged on a cell's interior: some skins were blown up slightly off the grid, leaving a strip of the neighbouring colour
 * along a cell's edge, which the smoothing replaces too.
 * <p>
 * {@link #mipChain} builds mipmaps for distance viewing. Colours are averaged by alpha (a see-through texel's white does not
 * pale its neighbours) and a texel is as opaque as the most opaque texel it covers: the faces of a model are separate
 * patches of the texture, and averaging their edges with the empty space around them would open cracks between faces.
 * <p>
 * Pixels are packed like {@code NativeImage}: ABGR, red in the lowest byte.
 */
public final class TextureSmoothing {
    /** Grid of the girls' UV layout (texture_width / texture_height in every girl's geo file). */
    public static final int GRID = 64;
    /** Highest channel spread (0-255) a cell's interior may have and still count as one flat pixel-art pixel. */
    static final int FLAT_SPREAD = 8;
    /** Lowest alpha of every interior texel of a flat cell. */
    static final int OPAQUE = 250;
    /** Colour distance (0-255 per channel, Euclidean) up to which two neighbouring cells are blended. */
    static final int SIMILAR = 80;

    private TextureSmoothing() {
    }

    /** Whether a texture of this size is laid out on the girls' grid with large enough cells to be worth smoothing. */
    public static boolean applies(int width, int height) {
        return width == height && width % GRID == 0 && width / GRID >= 8;
    }

    /** Smooths the flat cells of {@code pixels} in place. */
    public static void smoothCells(int[] pixels, int width, int height) {
        if (!applies(width, height)) {
            return;
        }
        int cell = width / GRID;
        int border = cell / 16 + 2; // the off-grid strip, plus a margin
        boolean[] flat = new boolean[GRID * GRID];
        int[][] mean = new int[GRID * GRID][];
        for (int cy = 0; cy < GRID; cy++) {
            for (int cx = 0; cx < GRID; cx++) {
                int[] min = {255, 255, 255};
                int[] max = {0, 0, 0};
                long[] sum = new long[3];
                boolean opaque = true;
                for (int y = cy * cell + border; y < (cy + 1) * cell - border && opaque; y++) {
                    int row = y * width;
                    for (int x = cx * cell + border; x < (cx + 1) * cell - border; x++) {
                        int p = pixels[row + x];
                        if ((p >>> 24) < OPAQUE) {
                            opaque = false;
                            break;
                        }
                        for (int c = 0; c < 3; c++) {
                            int v = (p >>> (c * 8)) & 0xFF;
                            min[c] = Math.min(min[c], v);
                            max[c] = Math.max(max[c], v);
                            sum[c] += v;
                        }
                    }
                }
                if (!opaque) {
                    continue;
                }
                int index = cy * GRID + cx;
                long count = (long) (cell - 2 * border) * (cell - 2 * border);
                boolean isFlat = true;
                int[] colour = new int[3];
                for (int c = 0; c < 3; c++) {
                    isFlat &= max[c] - min[c] <= FLAT_SPREAD;
                    colour[c] = (int) ((sum[c] + count / 2) / count);
                }
                flat[index] = isFlat;
                mean[index] = colour;
            }
        }

        float[] blended = new float[3];
        for (int cy = 0; cy < GRID; cy++) {
            for (int cx = 0; cx < GRID; cx++) {
                int index = cy * GRID + cx;
                if (!flat[index]) {
                    continue;
                }
                int[] self = mean[index];
                // the colour each neighbour contributes: its own if it is flat and similar, else this cell's
                int[][] around = new int[9][];
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int ny = cy + dy;
                        int nx = cx + dx;
                        int[] chosen = self;
                        if (ny >= 0 && nx >= 0 && ny < GRID && nx < GRID && flat[ny * GRID + nx]
                                && distanceSquared(self, mean[ny * GRID + nx]) <= SIMILAR * SIMILAR) {
                            chosen = mean[ny * GRID + nx];
                        }
                        around[(dy + 1) * 3 + dx + 1] = chosen;
                    }
                }
                for (int y = 0; y < cell; y++) {
                    float fy = (y + 0.5F) / cell - 0.5F; // -0.5 .. 0.5 across the cell
                    int sy = fy < 0.0F ? -1 : 1;
                    float wy = Math.abs(fy);
                    int row = (cy * cell + y) * width + cx * cell;
                    for (int x = 0; x < cell; x++) {
                        float fx = (x + 0.5F) / cell - 0.5F;
                        int sx = fx < 0.0F ? -1 : 1;
                        float wx = Math.abs(fx);
                        int[] side = around[4 + sx];
                        int[] vertical = around[4 + sy * 3];
                        int[] diagonal = around[4 + sy * 3 + sx];
                        int out = pixels[row + x] & 0xFF000000;
                        for (int c = 0; c < 3; c++) {
                            blended[c] = (1 - wy) * ((1 - wx) * self[c] + wx * side[c]) + wy * ((1 - wx) * vertical[c] + wx * diagonal[c]);
                            out |= Math.max(0, Math.min(255, Math.round(blended[c]))) << (c * 8);
                        }
                        pixels[row + x] = out;
                    }
                }
            }
        }
    }

    private static int distanceSquared(int[] a, int[] b) {
        int sum = 0;
        for (int c = 0; c < 3; c++) {
            int d = a[c] - b[c];
            sum += d * d;
        }
        return sum;
    }

    /** The mip levels below {@code base} (level 1 first) down to 1 x 1; see the class notes for how a texel is made. */
    public static int[][] mipChain(int[] base, int width, int height) {
        int levels = 0;
        for (int size = Math.min(width, height); size > 1; size >>= 1) {
            levels++;
        }
        int[][] chain = new int[levels][];
        int[] above = base;
        int w = width;
        int h = height;
        long[] weighted = new long[3];
        long[] plain = new long[3];
        for (int level = 0; level < levels; level++) {
            int nw = Math.max(1, w >> 1);
            int nh = Math.max(1, h >> 1);
            int[] next = new int[nw * nh];
            for (int y = 0; y < nh; y++) {
                for (int x = 0; x < nw; x++) {
                    long a = 0;
                    int coverage = 0;
                    java.util.Arrays.fill(weighted, 0L);
                    java.util.Arrays.fill(plain, 0L);
                    for (int k = 0; k < 4; k++) {
                        int sx = Math.min(w - 1, x * 2 + (k & 1));
                        int sy = Math.min(h - 1, y * 2 + (k >> 1));
                        int p = above[sy * w + sx];
                        int alpha = p >>> 24;
                        a += alpha;
                        coverage = Math.max(coverage, alpha);
                        for (int c = 0; c < 3; c++) {
                            int v = (p >>> (c * 8)) & 0xFF;
                            weighted[c] += (long) v * alpha;
                            plain[c] += v;
                        }
                    }
                    int out = coverage << 24;
                    for (int c = 0; c < 3; c++) {
                        int v = a > 0 ? (int) ((weighted[c] + a / 2) / a) : (int) ((plain[c] + 2) / 4);
                        out |= v << (c * 8);
                    }
                    next[y * nw + x] = out;
                }
            }
            chain[level] = next;
            above = next;
            w = nw;
            h = nh;
        }
        return chain;
    }
}
