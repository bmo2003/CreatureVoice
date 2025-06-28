// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import com.mojang.blaze3d.systems.RenderSystem;
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
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");
    private static final Set<String> missingTextures = new HashSet<>();

    public TextureLoader() {}

    public Identifier GetUI(String name) {
        String texturePath = "textures/ui/" + name + ".png";
        Identifier textureId = new Identifier("creaturechat", texturePath);
        Optional<Resource> resource = MinecraftClient
                .getInstance()
                .getResourceManager()
                .getResource(textureId);

        if (resource.isPresent()) {
            // replace bindTexture(...) with RenderSystem
            RenderSystem.setShaderTexture(0, textureId);
            return textureId;
        } else {
            logMissingTextureOnce(texturePath);
            return null;
        }
    }

    public Identifier GetEntity(String texturePath) {
        Identifier textureId = new Identifier("creaturechat", texturePath);
        Optional<Resource> resource = MinecraftClient
                .getInstance()
                .getResourceManager()
                .getResource(textureId);

        if (resource.isPresent()) {
            RenderSystem.setShaderTexture(0, textureId);
            return textureId;
        } else {
            Identifier notFoundId = new Identifier("creaturechat", "textures/entity/not_found.png");
            RenderSystem.setShaderTexture(0, notFoundId);
            logMissingTextureOnce(texturePath);
            return notFoundId;
        }
    }

    private void logMissingTextureOnce(String texturePath) {
        if (missingTextures.add(texturePath)) {
            LOGGER.info("{} was not found", texturePath);
        }
    }
}