package com.owlmaddie;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import java.util.Map;
import java.util.HashMap;
import java.io.InputStreamReader;
import java.io.InputStream;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TextureLoader {
    public static final Logger LOGGER = LoggerFactory.getLogger("mobgpt");
    private static final String ASSETS_PATH = "/assets/mobgpt/";
    private static final String ENTITY_TEXTURE_PATH = "textures/entity/";
    private static final String UI_TEXTURE_PATH = "textures/ui/";
    private static final Map<String, Identifier> TEXTURE_MAP = new HashMap<>();

    public TextureLoader() {
        // Load Entity Textures
        InputStream inputStream = TextureLoader.class.getResourceAsStream(ASSETS_PATH + ENTITY_TEXTURE_PATH + "textures.json");
        Gson gson = new Gson();
        // Assuming your JSON is a list of strings, i.e., filenames.
        String[] filenames = gson.fromJson(new InputStreamReader(inputStream), String[].class);

        for(String filename : filenames) {
            String texturePath = ENTITY_TEXTURE_PATH + filename;
            Identifier textureId = new Identifier("mobgpt", texturePath);
            TEXTURE_MAP.put(filename.replace(".png", ""), textureId);
        }

        // Load UI Textures
        inputStream = TextureLoader.class.getResourceAsStream(ASSETS_PATH + UI_TEXTURE_PATH + "textures.json");
        gson = new Gson();
        // Assuming your JSON is a list of strings, i.e., filenames.
        filenames = gson.fromJson(new InputStreamReader(inputStream), String[].class);

        for(String filename : filenames) {
            String texturePath = UI_TEXTURE_PATH + filename;
            Identifier textureId = new Identifier("mobgpt", texturePath);
            TEXTURE_MAP.put(filename.replace(".png", ""), textureId);
        }
    }

    public Identifier Get(String name) {
        Identifier textureId = TEXTURE_MAP.get(name);

        if (textureId != null) {
            MinecraftClient.getInstance().getTextureManager().bindTexture(textureId);
            return textureId;
        } else {
            // You can bind and return a default texture here if you have one
            // textureManager.bindTexture(DEFAULT_TEXTURE_ID);
            // return DEFAULT_TEXTURE_ID;

            // Or just return null or handle the missing texture situation as needed
            LOGGER.info(name + " was not found in mobgpt");
            return null;
        }
    }
}