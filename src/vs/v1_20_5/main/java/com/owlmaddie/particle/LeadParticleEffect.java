// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

/**
 * Lead particle effect (for Minecraft 1.20.5+ compatibility)
 */
public record LeadParticleEffect(float angle) implements ParticleEffect {
    //Registry/command codec
    public static final MapCodec<LeadParticleEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(inst ->
            inst.group(Codec.FLOAT.fieldOf("angle").forGetter(LeadParticleEffect::angle)).apply(inst, LeadParticleEffect::new)
    );

    // Network codec: writer first, then reader
    public static final PacketCodec<PacketByteBuf, LeadParticleEffect> PACKET_CODEC =
            PacketCodec.of(
                    LeadParticleEffect::write, // encoder: write the float
                    buf -> new LeadParticleEffect(buf.readFloat()) // decoder: read the float
            );

    // The ParticleType, wired up with both codecs
    public static final ParticleType<LeadParticleEffect> TYPE =
            FabricParticleTypes.complex(
                    type -> MAP_CODEC,
                    type -> PACKET_CODEC
            );

    public void write(PacketByteBuf buf) {
        buf.writeFloat(angle);
    }

    @Override
    public ParticleType<LeadParticleEffect> getType() {
        return TYPE;
    }
}
