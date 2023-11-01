package com.owlmaddie;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.resource.Resource;

public class TextureLoader {
    public static final Logger LOGGER = LoggerFactory.getLogger("mobgpt");
    private static final String ASSETS_PATH = "/assets/mobgpt/";
    private static final String ENTITY_TEXTURE_PATH = "textures/entity/";
    private static final String UI_TEXTURE_PATH = "textures/ui/";
    private static final Map<String, Identifier> TEXTURE_MAP = new HashMap<>();

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
            LOGGER.info(texture_path + " was not found in mobgpt");
            return null;
        }
    }
}