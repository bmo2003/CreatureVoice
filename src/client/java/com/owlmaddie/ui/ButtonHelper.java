// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ButtonHelper {
  /**
   * Create an image‐only button that swaps between normal/hover textures.
   * Version‐specific subclasses just override the rendering hook.
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
      public void renderButton(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Identifier tex = isHovered() ? hoverTex : normalTex;
        ctx.drawTexture(tex, getX(), getY(), 0, 0, width, height, width, height);
      }
    };
  }
}
