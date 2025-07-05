// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.skin;

import com.mojang.blaze3d.platform.NativeImage;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code IPlayerSkinTexture} interface adds a new getLoadedImage method to PlayerSkinTexture instances
 */
public interface IPlayerSkinTexture {
    @Nullable
    NativeImage getLoadedImage();
}