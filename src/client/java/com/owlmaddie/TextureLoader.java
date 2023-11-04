package com.owlmaddie;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class TextureLoader {
    public static final Logger LOGGER = LoggerFactory.getLogger("mobgpt");

    public TextureLoader() {
    }

    public Identifier Get(String folder, String name) {
        // Attempt to load texture resource
        String texture_path = "textures/" + folder + "/" + name + ".png";
        Identifier textureId = new Identifier("mobgpt", texture_path);
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(textureId);

        if (!resource.isEmpty()) {
            // Bind texture, and return Identity
            MinecraftClient.getInstance().getTextureManager().bindTexture(textureId);
            return textureId;
        } else {
            // Resource not found
            //LOGGER.info(texture_path + " was not found in mobgpt");
            return null;
        }
    }
}