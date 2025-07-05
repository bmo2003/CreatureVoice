// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The {@code TextureLoader} class registers and returns texture identifiers for resources
 * contained for this mod. UI and Entity icons. Missing textures are logged once.
 * Modified for 1.21.5.
 */
public class TextureLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");
    private static final Set<String> missing = new HashSet<>();
    public static GpuTexture lastTexture = null;
    public static Identifier lastTextureId = null;

    public TextureLoader() {}

    /**
     * Load and bind a UI texture (assets/creaturechat/textures/ui/{name}.png).
     * Returns the Identifier if found, or null if missing.
     */
    public Identifier GetUI(String name) {
        return load(new Identifier("creaturechat", "textures/ui/" + name + ".png"));
    }

    /**
     * Load and bind an entity texture (assets/creaturechat/{texturePath}).
     * Returns the Identifier if found, or falls back to not_found.png.
     */
    public Identifier GetEntity(String texturePath) {
        Identifier id = new Identifier("creaturechat", texturePath);
        ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
        if (rm.getResource(id).isPresent()) {
            return load(id);
        } else {
            LOGGER.info("Texture not found: {}", texturePath);
            return load(new Identifier("creaturechat", "textures/entity/not_found.png"));
        }
    }

    private Identifier load(Identifier id) {
        MinecraftClient client = MinecraftClient.getInstance();
        ResourceManager rm = client.getResourceManager();
        Optional<Resource> res = rm.getResource(id);

        if (res.isEmpty()) {
            // first time missing: log once
            if (missing.add(id.toString())) {
                LOGGER.info("Missing texture: {}", id);
            }
            return null;
        }
        return id;
    }

    /**
     * Bind any already-registered texture at the given unit,
     * or register+bind it if it's not yet in the TextureManager.
     */
    public static void bind(int unit, Identifier id) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextureManager tm = client.getTextureManager();
        AbstractTexture tex = tm.getTexture(id);
        if (tex == null) {
            // register a ResourceTexture so the manager will load it from assets
            tex = new ResourceTexture(id);
            tm.registerTexture(id, tex);
        }

        // Store last GpuTexture
        GpuTexture gpu = tex.getGlTexture();
        lastTexture = gpu;
        lastTextureId = id;

        // Set Texture
        RenderSystem.setShaderTexture(unit, gpu);
    }
}
