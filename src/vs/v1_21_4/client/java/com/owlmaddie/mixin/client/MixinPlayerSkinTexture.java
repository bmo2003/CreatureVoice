// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.mixin.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.owlmaddie.skin.IPlayerSkinTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * The {@code MixinPlayerSkinTexture} class injects code into the PlayerSkinTexture class, to make a copy
 * of the player's skin native image, so we can later use it for pixel checking (black/white key) for
 * loading custom player icons in the unused UV coordinates of the player skin image. Modified for Minecraft 1.21.4.
 */
@Mixin(DynamicTexture.class)
public abstract class MixinPlayerSkinTexture implements IPlayerSkinTexture {
    /** The private field that holds the loaded image */
    @Shadow private NativeImage image;

    @Override
    public NativeImage getLoadedImage() {
        return this.image;
    }
}
