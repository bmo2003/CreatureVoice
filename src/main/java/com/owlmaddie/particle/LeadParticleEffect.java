// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;

import static com.owlmaddie.network.ServerPackets.LEAD_PARTICLE;

/**
 * The {@code LeadParticleEffect} class allows for an 'angle' to be passed along with the Particle, to rotate it in the direction of LEAD behavior.
 */
public class LeadParticleEffect implements ParticleEffect {
    public static final ParticleEffect.Factory<LeadParticleEffect> DESERIALIZER = new Factory<>() {
        @Override
        public LeadParticleEffect read(ParticleType<LeadParticleEffect> particleType, PacketByteBuf buf) {
            // Read the angle (or any other data) from the packet
            double angle = buf.readDouble();
            return new LeadParticleEffect(angle);
        }

        @Override
        public LeadParticleEffect read(ParticleType<LeadParticleEffect> particleType, StringReader reader) throws CommandSyntaxException {
            // Read the angle from a string
            double angle = reader.readDouble();
            return new LeadParticleEffect(angle);
        }
    };

    private final double angle;

    public LeadParticleEffect(double angle) {
        this.angle = angle;
    }

    @Override
    public ParticleType<?> getType() {
        return LEAD_PARTICLE;
    }

    public double angle() {
        return angle;
    }

    @Override
    public void write(PacketByteBuf buf) {
        // Write the angle to the packet
        buf.writeDouble(angle);
    }

    @Override
    public String asString() {
        return Double.toString(angle);
    }
}
