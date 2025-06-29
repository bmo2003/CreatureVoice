// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.owlmaddie.utils.TextureLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Provides a Screen class which renders a chat-background, and can be modified
 * for different versions of Minecraft (as API changes happen). Modified for Minecraft 1.21.2.
 */
public abstract class ScreenHelper extends Screen {
    protected int BG_WIDTH, BG_HEIGHT, bgX, bgY;
    private boolean skipNextBackground = false;
    protected static final TextureLoader textures = new TextureLoader();

    protected ScreenHelper(Text title) {
        super(title);
    }

    /**
     * Subclass must return its TextFieldWidget instance here
     */
    protected abstract TextFieldWidget getTextField();

    /**
     * Subclass must return its label Text here
     */
    protected abstract Text getLabelText();

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw the vanilla gradient once
        super.renderBackground(context, mouseX, mouseY, delta);

        // Draw the chat-box texture
        Identifier bgTex = textures.GetUI("chat-background");
        if (bgTex != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            context.drawTexture(
                    RenderLayer::getGuiTextured,
                    bgTex,
                    bgX, bgY,
                    0f, 0f,
                    BG_WIDTH, BG_HEIGHT,
                    BG_WIDTH, BG_HEIGHT
            );
            RenderSystem.disableBlend();
        }

        // Render children/widgets but suppress their background call
        skipNextBackground = true;
        super.render(context, mouseX, mouseY, delta);
        skipNextBackground = false;

        // Draw the "Enter your message:" label
        TextFieldWidget tf = getTextField();
        Text           lbl = getLabelText();
        int lw = textRenderer.getWidth(lbl);
        int lx = (this.width - lw) / 2;
        int ly = tf.getY() - 15;
        context.drawTextWithShadow(textRenderer, lbl, lx, ly, 0xFFFFFF);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!skipNextBackground) {
            super.renderBackground(context, mouseX, mouseY, delta);
        }
    }
}
