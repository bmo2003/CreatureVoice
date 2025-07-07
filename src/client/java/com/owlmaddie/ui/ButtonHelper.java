// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ButtonHelper {
  /**
   * Create an image‐only button that swaps between normal/hover textures.
   * Version‐specific subclasses just override the rendering hook.
   */
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
      public void renderWidget(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ResourceLocation tex = isHovered() ? hoverTex : normalTex;
        ctx.blit(tex, getX(), getY(), 0, 0, width, height, width, height);
      }
    };
  }
}
