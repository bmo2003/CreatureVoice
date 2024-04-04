package com.owlmaddie;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * The {@code TextureLoader} class registers and returns texture identifiers for resources
 * contained for this mod. UI and Entity icons.
 */
public class TextureLoader {
    public static final Logger LOGGER = LoggerFactory.getLogger("mobgpt");

    public TextureLoader() {
    }

    public Identifier GetUI(String name) {
        // Attempt to load texture resource
        String texture_path = "textures/ui/" + name + ".png";
        Identifier textureId = new Identifier("mobgpt", texture_path);
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(textureId);

        if (!resource.isEmpty()) {
            // Bind texture, and return Identity
            MinecraftClient.getInstance().getTextureManager().bindTexture(textureId);
            return textureId;
        } else {
            // Resource not found
            LOGGER.info(texture_path + " was not found");
            return null;
        }
    }

    public Identifier GetEntity(String texturePath) {
        Identifier textureId = new Identifier("mobgpt", texturePath);
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(textureId);

        if (resource.isPresent()) {
            // Texture found, bind it and return the Identifier
            MinecraftClient.getInstance().getTextureManager().bindTexture(textureId);
            return textureId;
        } else {
            // Texture not found, log a message and return the "not_found" texture Identifier
            LOGGER.info(texturePath + " was not found");
            Identifier notFoundTextureId = new Identifier("mobgpt", "textures/entity/not_found.png");
            MinecraftClient.getInstance().getTextureManager().bindTexture(notFoundTextureId);
            return notFoundTextureId;
        }
    }
}