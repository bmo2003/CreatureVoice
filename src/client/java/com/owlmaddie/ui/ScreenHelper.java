// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.ui;

import com.owlmaddie.utils.TextureLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Provides a Screen class which renders a chat-background, and can be modified
 * for different versions of Minecraft (as API changes happen).
 */
public abstract class ScreenHelper extends Screen {
    protected int BG_WIDTH, BG_HEIGHT, bgX, bgY;
    protected static final TextureLoader textures = new TextureLoader();

    protected ScreenHelper(Text title) {
        super(title);
    }

    /** Subclass must return its TextFieldWidget instance here */
    protected abstract TextFieldWidget getTextField();

    /** Subclass must return its label Text here */
    protected abstract Text getLabelText();

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Full-screen vanilla gradient
        renderBackground(context);

        // Chat-box texture
        Identifier bgTex = textures.GetUI("chat-background");
        if (bgTex != null) {
            context.drawTexture(
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
        TextFieldWidget tf = getTextField();
        Text label = getLabelText();
        int lw = this.textRenderer.getWidth(label);
        int lx = (this.width - lw) / 2;
        int ly = tf.getY() - 15;
        context.drawTextWithShadow(this.textRenderer, label, lx, ly, 0xFFFFFF);
    }

    @Override
    public void renderBackground(DrawContext context) {
        // call the vanilla full-screen gradient
        super.renderBackground(context);
    }
}
