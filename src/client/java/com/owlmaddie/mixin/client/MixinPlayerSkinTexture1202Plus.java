package com.owlmaddie.mixin.client;

import com.owlmaddie.skin.IPlayerSkinTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.PlayerSkinTexture;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The {@code MixinPlayerSkinTexture} class injects code into the PlayerSkinTexture class, to make a copy
 * of the player's skin native image, so we can later use it for pixel checking (black/white key) for
 * loading custom player icons in the unused UV coordinates of the player skin image.
 */
@Mixin(PlayerSkinTexture.class)
public abstract class MixinPlayerSkinTexture1202Plus extends ResourceTexture implements IPlayerSkinTexture {

    @Unique
    private NativeImage cachedSkinImage;

    public MixinPlayerSkinTexture1202Plus(Identifier location) {
        super(location);
    }

    @Inject(method = "onTextureLoaded", at = @At("HEAD"))
    private void captureNativeImage(NativeImage image, CallbackInfo ci) {
        // Instead of image.copy(), we do a manual clone
        this.cachedSkinImage = cloneNativeImage(image);
    }

    @Override
    public NativeImage getLoadedImage() {
        return this.cachedSkinImage;
    }

    // Example of the utility method in the same class (or in a separate helper):
    private static NativeImage cloneNativeImage(NativeImage source) {
        NativeImage copy = new NativeImage(
                source.getFormat(),
                source.getWidth(),
                source.getHeight(),
                false
        );
        copy.copyFrom(source);
        return copy;
    }
}

