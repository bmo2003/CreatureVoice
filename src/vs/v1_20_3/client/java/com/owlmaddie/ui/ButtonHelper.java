// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ButtonHelper {
  /**
   * Create an image‐only button that swaps between normal/hover textures.
   * Version‐specific subclasses just override the rendering hook. Modified for Minecraft 1.20.3.
   */
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
        Identifier tex = isHovered() ? hoverTex : normalTex;
        ctx.drawTexture(tex, getX(), getY(), 0, 0,
                width, height,
                width, height);
      }
    };
  }
}
