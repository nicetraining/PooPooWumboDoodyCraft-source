package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.ClientConfig;
import com.fartypoopy.sexmod.entity.CompanionKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

/**
 * Puts a {@link SmoothTexture} behind each girl's texture location while {@link ClientConfig#smoothTextures()} is on (and a
 * plain one back when it is turned off). Every renderer and layer that draws with that location then gets the smoothed one.
 */
public final class SmoothTextures {
    private SmoothTextures() {
    }

    /** Cheap enough to call every frame: a map lookup per girl, a (re)registration only when the setting changed. */
    public static void ensureAll() {
        boolean smooth = ClientConfig.smoothTextures();
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        for (CompanionKind kind : CompanionKind.values()) {
            ensure(textures, kind.textureResource(), smooth);
        }
    }

    private static void ensure(TextureManager textures, ResourceLocation location, boolean smooth) {
        AbstractTexture current = textures.getTexture(location, null);
        if (smooth && !(current instanceof SmoothTexture)) {
            textures.register(location, new SmoothTexture(location));
        } else if (!smooth && current instanceof SmoothTexture) {
            textures.register(location, new SimpleTexture(location));
        }
    }
}
