package com.owlmaddie.mixin;

import com.owlmaddie.chat.EntityChatData;
import com.owlmaddie.commands.ConfigurationHandler;
import com.owlmaddie.network.ServerPackets;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.owlmaddie.network.ServerPackets.BroadcastPlayerMessage;

/**
 * The {@code MixinOnChat} mixin class intercepts chat messages from players, and broadcasts them as chat bubbles
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class MixinOnChat {

    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
    private void onChatMessage(ChatMessageC2SPacket packet, CallbackInfo ci) {
        ConfigurationHandler.Config config = new ConfigurationHandler(ServerPackets.serverInstance).loadConfig();
        if (config.getChatBubbles()) {

            // Get the player who sent the message
            ServerPlayNetworkHandler handler = (ServerPlayNetworkHandler) (Object) this;
            ServerPlayerEntity player = handler.player;

            // Get the chat message
            String chatMessage = packet.chatMessage();

            // Example: Call your broadcast function
            EntityChatData chatData = new EntityChatData(player.getUuidAsString());
            chatData.currentMessage = chatMessage;
            BroadcastPlayerMessage(chatData, player);

            // Optionally, cancel the event to prevent the default behavior
            //ci.cancel();
        }
    }
}
