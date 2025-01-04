package com.owlmaddie.mixin.client;

import com.owlmaddie.utils.IPlayerSkinTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.PlayerSkinTexture;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerSkinTexture.class)
public abstract class MixinPlayerSkinTexturePre1202 extends ResourceTexture implements IPlayerSkinTexture {
    @Unique
    private NativeImage cachedSkinImage;

    public MixinPlayerSkinTexturePre1202(Identifier location) {
        super(location);
    }

    @Inject(method = "onTextureLoaded", at = @At("HEAD"))
    private void captureNativeImage(NativeImage image, CallbackInfo ci) {
        this.cachedSkinImage = cloneNativeImage(image);
    }

    @Override
    public NativeImage getLoadedImage() {
        return this.cachedSkinImage;
    }

    private static NativeImage cloneNativeImage(NativeImage source) {
        NativeImage copy = new NativeImage(source.getFormat(), source.getWidth(), source.getHeight(), false);
        copy.copyFrom(source);
        return copy;
    }
}
