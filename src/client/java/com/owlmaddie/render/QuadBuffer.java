// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;

/**
 * Wrapper for Tessellator/BufferBuilder. Since the API changes between different versions of Minecraft,
 * this wrapper helps standardize the rendering/drawing calls, so we can override them in newer versions.
 */
@Environment(EnvType.CLIENT)
public final class QuadBuffer {
    public static final QuadBuffer INSTANCE = new QuadBuffer();

    private final Tesselator tessellator = Tesselator.getInstance();
    private BufferBuilder buf;

    private QuadBuffer() {}

    // begin
    public QuadBuffer begin() {
        return begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
    }

    public QuadBuffer begin(VertexFormat.Mode mode) {
        return begin(mode, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
    }

    public QuadBuffer begin(VertexFormat.Mode mode, VertexFormat fmt) {
        buf = tessellator.getBuilder();
        buf.begin(mode, fmt);
        return this;
    }

    // Accepts 0‒1 float channels, matches vanilla VertexConsumer.color(float…)
    public QuadBuffer color(float r, float g, float b, float a) {
        buf.color(r, g, b, a);
        return this;
    }

    // vertex helpers
    public QuadBuffer vertex(Matrix4f mat, float x, float y, float z) {
        buf.vertex(mat, x, y, z);
        return this;
    }

    public QuadBuffer vertex(float x, float y, float z) {
        buf.vertex(x, y, z);
        return this;
    }

    public QuadBuffer texture(float u, float v)           { buf.uv(u, v);   return this; }
    public QuadBuffer color(int r,int g,int b,int a)      { buf.color(r, g, b, a); return this; }
    public QuadBuffer light(int packed)                   { buf.uv2(packed);   return this; }
    public QuadBuffer overlay(int packed) {
        buf.overlayCoords(packed);
        buf.endVertex(); // immediately finalize
        return this;
    }

    // end & draw
    public void draw() {
        tessellator.end();              // 1.20.x path
    }
}
