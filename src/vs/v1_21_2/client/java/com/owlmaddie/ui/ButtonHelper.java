// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Create an image‐only button that swaps between normal/hover textures.
 * Version‐specific subclasses just override the rendering hook. Modified for Minecraft 1.21.2.
 */
public class ButtonHelper {

    public static ButtonWidget createImageButton(
            int x, int y,
            int width, int height,
            Identifier normalTex,
            Identifier hoverTex,
            ButtonWidget.PressAction onPress,
            ButtonWidget.NarrationSupplier narrate
    ) {
        return new ButtonWidget(x, y, width, height, Text.empty(), onPress, narrate) {
            @Override
            protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
                // turn on alpha blending
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();

                // choose the correct texture
                Identifier tex = isHovered() ? hoverTex : normalTex;

                // draw from the GUI atlas, sampling just this sprite’s region
                ctx.drawTexture(
                        RenderLayer::getGuiTextured,  // supplies the atlas layer for this sprite
                        tex,                          // your sprite ID
                        getX(), getY(),               // on-screen position
                        0f, 0f,                       // u,v origin
                        width, height,                // region size
                        width, height                 // atlas size = region size
                );

                // restore default blending
                RenderSystem.disableBlend();
            }
        };
    }
}
