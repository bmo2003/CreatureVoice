// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

public class NbtCompoundHelper {
    public static void putUuid(CompoundTag nbt, String key, UUID uuid) {
        nbt.put(key, NbtUtils.createUUID(uuid));
    }

    public static UUID getUuid(CompoundTag nbt, String key) {
        return NbtUtils.loadUUID(nbt.get(key));
    }

    public static boolean containsUuid(CompoundTag nbt, String key) {
        return nbt.contains(key);
    }
}
