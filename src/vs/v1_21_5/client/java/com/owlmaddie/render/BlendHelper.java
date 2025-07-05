// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;

public final class BlendHelper {
    private BlendHelper() {}

    /** turn on alpha blending */
    public static void enableBlend() {
        GlStateManager._enableBlend();
    }

    /** standard src-alpha, one-minus-src-alpha */
    public static void defaultBlendFunc() {
        // note the 4-arg separate func: srcRgb, dstRgb, srcAlpha, dstAlpha
        GlStateManager._blendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
    }

    /** turn off alpha blending */
    public static void disableBlend() {
        GlStateManager._disableBlend();
    }

    /** enable depth test (so bubbles depth-test against world) */
    public static void enableDepthTest() {
        GlStateManager._enableDepthTest();
    }

    /** disable depth test */
    public static void disableDepthTest() {
        GlStateManager._disableDepthTest();
    }

    /**
     * control whether your draw writes to the depth buffer.
     * false for transparent overlays, true to restore normal writes.
     */
    public static void depthMask(boolean write) {
        GlStateManager._depthMask(write);
    }
}
