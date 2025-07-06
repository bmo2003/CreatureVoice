// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.owlmaddie.utils.TextureLoader;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.*;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class QuadBuffer {
    public static final QuadBuffer INSTANCE = new QuadBuffer();
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");

    private static final RenderPipeline.Snippet QUAD_SNIPPET = RenderPipeline.builder()
            .withUniform("ModelViewMat", UniformType.MATRIX4X4)
            .withUniform("ProjMat", UniformType.MATRIX4X4)
            .withUniform("ColorModulator", UniformType.VEC4)
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.DrawMode.QUADS)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .buildSnippet();

    private static final RenderPipeline QUAD_PIPELINE = RenderPipeline.builder(QUAD_SNIPPET)
            .withLocation("pipeline/gui_textured")
            .withDepthBias(3.0f, 3.0f)
            .withBlend(BlendFunction.PANORAMA)
            .build();

    private BufferBuilder buf;
    private QuadBuffer() {}

    // begin
    public QuadBuffer begin() {
        return begin(VertexFormat.DrawMode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
    }

    public QuadBuffer begin(VertexFormat.Mode mode) {
        return begin(mode, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
    }

    public QuadBuffer begin(VertexFormat.Mode mode, VertexFormat fmt) {
        buf = Tesselator.getInstance().begin(mode, fmt);
        return this;
    }

    // vertex helpers
    public QuadBuffer vertex(Matrix4f mat, float x, float y, float z) {
        buf.addVertex(mat, x, y, z);
        return this;
    }

    public QuadBuffer vertex(float x, float y, float z) {
        buf.addVertex(x, y, z);
        return this;
    }

    public QuadBuffer texture(float u, float v)           { buf.setUv(u, v);   return this; }
    public QuadBuffer color(float r, float g, float b, float a) { buf.setColor(r, g, b, a); return this; }
    public QuadBuffer color(int r, int g, int b, int a)  { buf.setColor(r, g, b, a); return this; }
    public QuadBuffer light(int packed)                   { buf.setLight(packed);   return this; }
    public QuadBuffer overlay(int packed)                 { buf.setOverlay(packed); return this; }

    public void draw() {
        // finish building your mesh
        MeshData mesh = buf.buildOrThrow();  // buf was created by begin(...)

        // reset to full-alpha; otherwise GUI code often leaves alpha == 0
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // this is the 1.21.5+build.1 API:
        // pick the GUI-textured layer for your Identifier, then draw it
        RenderType.guiTextured(TextureLoader.lastTextureId).draw(mesh);

        // free the mesh
        mesh.close();
    }
}