// SPDX-License-Identifier: GPL-3.0-or-later | CreatureChat™ © owlmaddie LLC
// Code: GPLv3 | Assets: CC BY-NC 4.0; See LICENSE.md & LICENSE-ASSETS.md.
package com.owlmaddie.skin;

import net.minecraft.client.texture.NativeImage;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code IPlayerSkinTexture} interface adds a new getLoadedImage method to PlayerSkinTexture instances
 */
public interface IPlayerSkinTexture {
    @Nullable
    NativeImage getLoadedImage();
}