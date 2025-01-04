package com.owlmaddie.utils;

import net.minecraft.client.texture.NativeImage;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code IPlayerSkinTexture} interface adds a new getLoadedImage method to PlayerSkinTexture instances
 */
public interface IPlayerSkinTexture {
    @Nullable
    NativeImage getLoadedImage();
}