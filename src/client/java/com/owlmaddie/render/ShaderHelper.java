// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.GameRenderer;   // 1.20.x path

/** Binds the GUI textured-quad shader (1.20.x mappings). */
public final class ShaderHelper {
    private ShaderHelper() {}

    public static void setTexturedShader() {
        RenderSystem.setShader(GameRenderer::getPositionColorTexLightmapProgram);
    }
}
