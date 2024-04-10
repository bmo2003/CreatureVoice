package com.owlmaddie.mixin;

import com.owlmaddie.utils.LivingEntityInterface;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class MixinLivingEntity implements LivingEntityInterface {
    private boolean canTargetPlayers = true;  // Default to true to maintain original behavior

    @Inject(method = "canTarget(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void modifyCanTarget(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (!this.canTargetPlayers && target instanceof PlayerEntity) {
            cir.setReturnValue(false);
        }
    }

    @Override
    public void setCanTargetPlayers(boolean canTarget) {
        this.canTargetPlayers = canTarget;
    }
}
