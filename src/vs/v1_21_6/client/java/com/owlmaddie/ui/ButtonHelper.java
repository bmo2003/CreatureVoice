// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.ui;

import com.owlmaddie.render.BlendHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Create an image‐only button that swaps between normal/hover textures.
 * Version‐specific subclasses just override the rendering hook. This is modified for Minecraft 1.21.6.
 */
public class ButtonHelper {

    public static Button createImageButton(
            int x, int y,
            int width, int height,
            ResourceLocation normalTex,
            ResourceLocation hoverTex,
            Button.OnPress onPress,
            Button.CreateNarration narrate
    ) {
        return new Button(x, y, width, height, Component.empty(), onPress, narrate) {
            @Override
            protected void renderWidget(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
                // turn on alpha blending
                BlendHelper.enableBlend();
                BlendHelper.defaultBlendFunc();

                // choose the correct texture
                ResourceLocation tex = isHovered() ? hoverTex : normalTex;

                // draw from the GUI atlas, sampling just this sprite’s region
                ctx.blit(
                        RenderPipelines.GUI_TEXTURED,  // supplies the atlas layer for this sprite
                        tex,                          // your sprite ID
                        getX(), getY(),               // on-screen position
                        0f, 0f,                       // u,v origin
                        width, height,                // region size
                        width, height                 // atlas size = region size
                );

                // restore default blending
                BlendHelper.disableBlend();
            }
        };
    }
}
