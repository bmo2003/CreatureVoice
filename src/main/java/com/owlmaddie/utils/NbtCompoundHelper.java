// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import java.util.UUID;

public class NbtCompoundHelper {
    public static void putUuid(NbtCompound nbt, String key, UUID uuid) {
        nbt.put(key, NbtHelper.fromUuid(uuid));
    }

    public static UUID getUuid(NbtCompound nbt, String key) {
        return NbtHelper.toUuid(nbt.get(key));
    }

    public static boolean containsUuid(NbtCompound nbt, String key) {
        return nbt.contains(key);
    }
}
