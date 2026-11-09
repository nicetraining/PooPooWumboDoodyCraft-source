package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.util.TextureSmoothing;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * A girl's texture with {@link TextureSmoothing}: shown unchanged as soon as it is loaded, then replaced by the smoothed
 * version with mipmaps once a background thread has made it, so loading costs no more than before. Up close it is sampled
 * texel by texel like before (linear filtering would bleed the empty space around each face into its edges: hairline cracks
 * between the slices of a model); further away the mipmaps take over, so the 4096 textures stop shimmering and jagging.
 */
final class SmoothTexture extends SimpleTexture {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    /** Bumped on every load and release: a background result for an older one is dropped. */
    private volatile int generation;

    SmoothTexture(ResourceLocation location) {
        super(location);
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException {
        SimpleTexture.TextureImage textureImage = this.getTextureImage(resourceManager);
        textureImage.throwIfError();
        NativeImage image = textureImage.getImage();
        int width = image.getWidth();
        int height = image.getHeight();
        int load = ++this.generation;
        if (!TextureSmoothing.applies(width, height)) {
            this.onRenderThread(() -> {
                TextureUtil.prepareImage(this.getId(), 0, width, height);
                image.upload(0, 0, 0, 0, 0, width, height, false, false, false, true);
            });
            return;
        }
        int[] pixels = image.getPixelsRGBA();
        this.onRenderThread(() -> {
            TextureUtil.prepareImage(this.getId(), 0, width, height);
            image.upload(0, 0, 0, 0, 0, width, height, false, false, false, true);
        });
        CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            TextureSmoothing.smoothCells(pixels, width, height);
            int[][] mips = TextureSmoothing.mipChain(pixels, width, height);
            LOGGER.debug("Smoothed {} in {} ms", this.location, (System.nanoTime() - start) / 1_000_000L);
            return mips;
        }, Util.backgroundExecutor()).whenComplete((mips, error) -> {
            if (error != null) {
                LOGGER.warn("Could not smooth {}; it stays as it is", this.location, error);
                return;
            }
            RenderSystem.recordRenderCall(() -> {
                if (load == this.generation) {
                    this.uploadSmoothed(pixels, mips, width, height);
                }
            });
        });
    }

    private void uploadSmoothed(int[] base, int[][] mips, int width, int height) {
        TextureUtil.prepareImage(this.getId(), mips.length, width, height);
        upload(base, 0, width, height, mips.length > 0);
        int w = width;
        int h = height;
        for (int level = 0; level < mips.length; level++) {
            w = Math.max(1, w >> 1);
            h = Math.max(1, h >> 1);
            upload(mips[level], level + 1, w, h, true);
        }
    }

    /** Uploads one level to the bound texture (prepareImage bound it): nearest up close, blended between mip levels. */
    private static void upload(int[] pixels, int level, int width, int height, boolean mipmap) {
        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                image.setPixelRGBA(x, y, pixels[row + x]);
            }
        }
        image.upload(level, 0, 0, 0, 0, width, height, false, false, mipmap, true);
    }

    private void onRenderThread(Runnable task) {
        if (RenderSystem.isOnRenderThreadOrInit()) {
            task.run();
        } else {
            RenderSystem.recordRenderCall(task::run);
        }
    }

    @Override
    public void releaseId() {
        this.generation++;
        super.releaseId();
    }
}
