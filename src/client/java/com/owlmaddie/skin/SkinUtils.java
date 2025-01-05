package com.owlmaddie.skin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

/**
 * SkinUtils contains functions to check for certain black and white pixel values in a skin, to determine
 * if the skin contains a custom hidden icon to show in the player chat message.
 */
public class SkinUtils {
    public static boolean checkCustomSkinKey(Identifier skinId) {
        // Grab the AbstractTexture from the TextureManager
        AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getTexture(skinId);

        // Check if it implements our Mixin interface: IPlayerSkinTexture
        if (tex instanceof IPlayerSkinTexture iSkin) {
            // Get the NativeImage we stored in the Mixin
            NativeImage image = iSkin.getLoadedImage();
            if (image != null) {
                int width = image.getWidth();
                int height = image.getHeight();

                // Check we have the full 64x64
                if (width == 64 && height == 64) {
                    // Example: black & white pixel at (31,48) and (32,48)
                    int color31_48 = image.getColor(31, 49);
                    int color32_48 = image.getColor(32, 49);
                    return (color31_48 == 0xFF000000 && color32_48 == 0xFFFFFFFF);
                }
            }
        }

        // If it's still loading, or not a PlayerSkinTexture, or no NativeImage loaded yet
        return false;
    }
}
