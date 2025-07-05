// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited

package com.owlmaddie.render;

import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Pre-1.21.5: delegate blend & depth calls to RenderSystem
 */
public final class BlendHelper {
    private BlendHelper() {}

    public static void enableBlend()       { RenderSystem.enableBlend(); }
    public static void defaultBlendFunc()  { RenderSystem.defaultBlendFunc(); }
    public static void disableBlend()      { RenderSystem.disableBlend(); }

    public static void enableDepthTest()   { RenderSystem.enableDepthTest(); }
    public static void disableDepthTest()  { RenderSystem.disableDepthTest(); }
    public static void depthMask(boolean m){ RenderSystem.depthMask(m); }
}
