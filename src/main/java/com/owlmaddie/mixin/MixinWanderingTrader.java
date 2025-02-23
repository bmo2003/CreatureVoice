package com.owlmaddie.mixin;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.chat.EntityChatData;
import net.minecraft.entity.passive.WanderingTraderEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents WanderingTraderEntity from despawning if it has chat data or a character sheet.
 */
@Mixin(WanderingTraderEntity.class)
public abstract class MixinWanderingTrader {
    private static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");

    @Inject(method = "tickDespawnDelay", at = @At("HEAD"), cancellable = true)
    private void preventTraderDespawn(CallbackInfo ci) {
        WanderingTraderEntity trader = (WanderingTraderEntity) (Object) this;

        // Get chat data for this trader
        EntityChatData chatData = ChatDataManager.getServerInstance().getOrCreateChatData(trader.getUuidAsString());

        // If the character sheet is not empty, cancel the function to prevent despawning
        if (!chatData.characterSheet.isEmpty()) {
            ci.cancel();
        }
    }
}
