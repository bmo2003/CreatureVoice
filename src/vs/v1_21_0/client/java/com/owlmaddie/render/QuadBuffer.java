// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

// Official Mojang mappings for 1.21+
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import org.joml.Matrix4f;

/**
 * Wrapper for Tessellator/BufferBuilder. Since the API changes between different versions of Minecraft,
 * this wrapper helps standardize the rendering/drawing calls. This is modified for Minecraft 1.21.0+.
 */
@Environment(EnvType.CLIENT)
public final class QuadBuffer {
    public static final QuadBuffer INSTANCE = new QuadBuffer();

    private BufferBuilder buf;

    private QuadBuffer() {}

    // begin
    public QuadBuffer begin() {
        return begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
    }

    public QuadBuffer begin(Mode mode) {
        return begin(mode, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
    }

    public QuadBuffer begin(Mode mode, VertexFormat fmt) {
        buf = Tesselator.getInstance().begin(mode, fmt);
        return this;
    }

    /** Add a vertex and set a default normal (pointing up) to satisfy the format */
    public QuadBuffer vertex(float x, float y, float z) {
        buf.addVertex(x, y, z);
        return this;
    }

    // vertex helpers
    public QuadBuffer vertex(Matrix4f mat, float x, float y, float z) {
        buf.addVertex(mat, x, y, z);
        return this;
    }

    public QuadBuffer color(int r, int g, int b, int a) {
        buf.setColor(r, g, b, a);
        return this;
    }

    public QuadBuffer texture(float u, float v) {
        buf.setUv(u, v);
        return this;
    }

    public QuadBuffer overlay(int packed) {
        buf.setOverlay(packed);
        return this;
    }

    public QuadBuffer light(int packed) {
        buf.setLight(packed);
        return this;
    }

    public void draw() {
        var mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }
}