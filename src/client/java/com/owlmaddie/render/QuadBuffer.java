// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;

/**
 * Wrapper for Tessellator/BufferBuilder. Since the API changes between different versions of Minecraft,
 * this wrapper helps standardize the rendering/drawing calls, so we can override them in newer versions.
 */
@Environment(EnvType.CLIENT)
public final class QuadBuffer {
    public static final QuadBuffer INSTANCE = new QuadBuffer();

    private final Tessellator tessellator = Tessellator.getInstance();
    private BufferBuilder buf;

    private QuadBuffer() {}

    // begin
    public QuadBuffer begin(VertexFormat.DrawMode mode) {
        return begin(mode, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT);
    }

    // Accepts 0‒1 float channels, matches vanilla VertexConsumer.color(float…)
    public QuadBuffer color(float r, float g, float b, float a) {
        buf.color(r, g, b, a);
        return this;
    }

    public QuadBuffer begin(VertexFormat.DrawMode mode, VertexFormat fmt) {
        buf = tessellator.getBuffer();
        buf.begin(mode, fmt);
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

    public QuadBuffer texture(float u, float v)           { buf.texture(u, v);   return this; }
    public QuadBuffer color(int r,int g,int b,int a)      { buf.color(r, g, b, a); return this; }
    public QuadBuffer light(int packed)                   { buf.light(packed);   return this; }
    public QuadBuffer overlay(int packed) {
        buf.overlay(packed);
        buf.next(); // immediately finalize
        return this;
    }

    // end & draw
    public void draw() {
        tessellator.draw();              // 1.20.x path
    }
}
