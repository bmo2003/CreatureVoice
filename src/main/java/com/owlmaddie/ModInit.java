package com.owlmaddie;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ModInit implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("mobgpt");
	public static final Identifier PACKET_CLIENT_CLICK = new Identifier("mobgpt", "packet_client_click");

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		ServerPlayNetworking.registerGlobalReceiver(PACKET_CLIENT_CLICK, (server, player, handler, buf, responseSender) -> {
			int entityId = buf.readInt();

			// Ensure that the task is synced with the server thread
			server.execute(() -> {
				// Your logic here, e.g., handle the entity click
				Entity entity = player.getServerWorld().getEntityById(entityId);
				if (entity != null) {
					// Perform action with the clicked entity
					LOGGER.info("Entity received: " + entity.getType().toString());
				}
			});
		});

		ServerWorldEvents.LOAD.register((server, world) -> {
			// Load chat data...
			LOGGER.info("LOAD chat data from NBT: " + world.getRegistryKey().getValue());
		});
		ServerWorldEvents.UNLOAD.register((server, world) -> {
			// Save chat data...
			LOGGER.info("SAVE chat data to NBT: " + world.getRegistryKey().getValue());
		});

		LOGGER.info("MobGPT Initialized!");
	}
}