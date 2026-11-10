package com.fartypoopy.sexmod.util;

/**
 * Texture smoothing for the girls' skins. Their 4096 textures sit on a 64 x 64 UV layout: part of the art is painted at full
 * resolution (outlines, faces, curves), part is pixel art blown up into flat 64 x 64 texel cells (hair, clothes, shading),
 * which is what makes them look blocky up close.
 * <p>
 * {@link #smoothCells} treats those flat cells as the pixels of a 64 x 64 image and upscales it the way pixel-art scalers in
 * emulators do (xBR / super-xBR: edge-directed where colours differ, linear where they are close):
 * <ul>
 * <li>neighbouring cells of similar colour blend into a soft gradient (bilinear between cell centres), so shading has no
 * steps;</li>
 * <li>where clearly different colours meet, the edge follows the shape of the region instead of the cell staircase: the
 * coverage of the cell's own colour is interpolated between cell centres and turned into a soft, anti-aliased edge at the
 * half-way line, so stair steps become smooth diagonals and rounded corners, and no edge is pixel-hard;</li>
 * <li>painted cells and see-through texels are left as they were (line art, faces, cut-outs), and so is every alpha.</li>
 * </ul>
 * Flatness is judged on a cell's interior: some skins were blown up slightly off the grid, leaving a strip of the
 * neighbouring colour along a cell's edge, which the smoothing replaces too.
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
    /** Colour distance (0-255 per channel, Euclidean) up to which neighbouring cells blend; beyond it they form an edge. */
    static final int SIMILAR = 110;
    /** Half-width of an edge's soft ramp, in coverage (0.5 = the whole cell): about a fifth of a model pixel each side. */
    static final float EDGE_SOFTNESS = 0.2F;

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

        float[] own = new float[3];
        float[] other = new float[3];
        int[][] around = new int[9][];
        boolean[] differs = new boolean[9];
        int[] slots = new int[4];
        float[] weights = new float[4];
        for (int cy = 0; cy < GRID; cy++) {
            for (int cx = 0; cx < GRID; cx++) {
                int index = cy * GRID + cx;
                if (!flat[index]) {
                    continue;
                }
                int[] self = mean[index];
                // each neighbour: a flat one brings its colour (similar: blended, different: an edge); painted or
                // see-through ones bring this cell's own colour, so nothing is smoothed into or out of them
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int ny = cy + dy;
                        int nx = cx + dx;
                        int slot = (dy + 1) * 3 + dx + 1;
                        around[slot] = self;
                        differs[slot] = false;
                        if (ny >= 0 && nx >= 0 && ny < GRID && nx < GRID && flat[ny * GRID + nx]) {
                            around[slot] = mean[ny * GRID + nx];
                            differs[slot] = distanceSquared(self, around[slot]) > SIMILAR * SIMILAR;
                        }
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
                        // the four cell centres around this texel and their bilinear weights
                        slots[0] = 4;
                        slots[1] = 4 + sx;
                        slots[2] = 4 + sy * 3;
                        slots[3] = 4 + sy * 3 + sx;
                        weights[0] = (1 - wy) * (1 - wx);
                        weights[1] = (1 - wy) * wx;
                        weights[2] = wy * (1 - wx);
                        weights[3] = wy * wx;
                        float ownWeight = 0.0F;
                        float otherWeight = 0.0F;
                        java.util.Arrays.fill(own, 0.0F);
                        java.util.Arrays.fill(other, 0.0F);
                        for (int k = 0; k < 4; k++) {
                            int[] colour = around[slots[k]];
                            float[] into = differs[slots[k]] ? other : own;
                            for (int c = 0; c < 3; c++) {
                                into[c] += weights[k] * colour[c];
                            }
                            if (differs[slots[k]]) {
                                otherWeight += weights[k];
                            } else {
                                ownWeight += weights[k];
                            }
                        }
                        // the own colour's interpolated coverage decides which side of the edge the texel is on
                        float t = Math.max(0.0F, Math.min(1.0F, (ownWeight - 0.5F + EDGE_SOFTNESS) / (2.0F * EDGE_SOFTNESS)));
                        float keep = otherWeight > 0.0F ? t * t * (3.0F - 2.0F * t) : 1.0F;
                        int out = pixels[row + x] & 0xFF000000;
                        for (int c = 0; c < 3; c++) {
                            float mine = own[c] / ownWeight;
                            float theirs = otherWeight > 0.0F ? other[c] / otherWeight : mine;
                            out |= Math.max(0, Math.min(255, Math.round(keep * mine + (1.0F - keep) * theirs))) << (c * 8);
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
