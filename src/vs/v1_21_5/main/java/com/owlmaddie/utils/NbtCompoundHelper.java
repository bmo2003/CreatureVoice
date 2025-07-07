// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import java.util.Optional;

/**
 * 1.21.5: putUuid/getUuid were removed, so store as a long[] tag instead.
 */
public class NbtCompoundHelper {
    public static void putUuid(CompoundTag nbt, String key, UUID uuid) {
        nbt.putLongArray(key, new long[]{
                uuid.getMostSignificantBits(),
                uuid.getLeastSignificantBits()
        });
    }

    public static UUID getUuid(CompoundTag nbt, String key) {
        Optional<long[]> opt = nbt.getLongArray(key);
        if (opt.isPresent()) {
            long[] data = opt.get();
            return new UUID(data[0], data[1]);
        }
        throw new IllegalStateException("Missing UUID tag: " + key);
    }

    public static boolean containsUuid(CompoundTag nbt, String key) {
        return nbt.contains(key);
    }
}
