// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;   // table of built-in keys

/** Binds the GUI textured-quad shader (1.21.2+). */
public final class ShaderHelper {
    public static void setTexturedShader() {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR_TEX_LIGHTMAP);
    }
}