// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.ui;

import com.owlmaddie.utils.TextureLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Provides a Screen class which renders a chat-background, and can be modified
 * for different versions of Minecraft (as API changes happen).
 */
public abstract class ScreenHelper extends Screen {
    protected int BG_WIDTH, BG_HEIGHT, bgX, bgY, TITLE_OFFSET;
    protected static final TextureLoader textures = new TextureLoader();

    protected ScreenHelper(Component title) {
        super(title);
    }

    /** Subclass must return its TextFieldWidget instance here */
    protected abstract EditBox getTextField();

    /** Subclass must return its label Text here */
    protected abstract Component getLabelText();

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Full-screen vanilla gradient
        renderBackground(context);

        // Chat-box texture
        ResourceLocation bgTex = textures.GetUI("chat-background");
        if (bgTex != null) {
            context.blit(
                    bgTex,
                    bgX, bgY,        // on-screen pos
                    0,  0,           // texture origin
                    BG_WIDTH, BG_HEIGHT,
                    BG_WIDTH, BG_HEIGHT
            );
        }

        // Render all children (textField, buttons)
        super.render(context, mouseX, mouseY, delta);

        // Label (using inherited textRenderer)
        EditBox tf = getTextField();
        Component label = getLabelText();
        int lw = this.font.width(label);
        int lx = (this.width - lw) / 2;
        int ly = tf.getY() - TITLE_OFFSET;
        context.drawString(this.font, label, lx, ly, 0xFFFFFF);
    }

    @Override
    public void renderBackground(GuiGraphics context) {
        // call the vanilla full-screen gradient
        super.renderBackground(context);
    }
}
