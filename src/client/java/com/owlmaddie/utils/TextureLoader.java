package com.owlmaddie.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The {@code TextureLoader} class registers and returns texture identifiers for resources
 * contained for this mod. UI and Entity icons. Missing textures are logged once.
 */
public class TextureLoader {
    public static final Logger LOGGER = LoggerFactory.getLogger("mobgpt");
    private static final Set<String> missingTextures = new HashSet<>();

    public TextureLoader() {
    }

    public Identifier GetUI(String name) {
        String texturePath = "textures/ui/" + name + ".png";
        Identifier textureId = new Identifier("mobgpt", texturePath);
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(textureId);

        if (resource.isPresent()) {
            // Bind texture, and return Identity
            MinecraftClient.getInstance().getTextureManager().bindTexture(textureId);
            return textureId;
        } else {
            // Resource not found
            logMissingTextureOnce(texturePath);
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
            Identifier notFoundTextureId = new Identifier("mobgpt", "textures/entity/not_found.png");
            MinecraftClient.getInstance().getTextureManager().bindTexture(notFoundTextureId);
            logMissingTextureOnce(texturePath);
            return notFoundTextureId;
        }
    }

    private void logMissingTextureOnce(String texturePath) {
        // Check if the missing texture has already been logged
        if (!missingTextures.contains(texturePath)) {
            LOGGER.info(texturePath + " was not found");
            missingTextures.add(texturePath);
        }
    }
}