// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC – unauthorized use prohibited
package com.owlmaddie.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Central place to create/unwrap buffers.
 *  ▸ 1 .20 .4   → returns a plain PacketByteBuf backed by a fresh Netty buffer.
 *  ▸ 1 .20 .5   → will keep the same method names but may wrap/unwrap RawPayload
 *                 behind the scenes – callers never notice the difference.
 */
public final class BufferHelper {

    private BufferHelper() {}

    /** Writable buffer, no preset capacity. */
    public static FriendlyByteBuf create() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    /** Writable buffer with an initial capacity hint. */
    public static FriendlyByteBuf create(int initialCapacity) {
        return new FriendlyByteBuf(Unpooled.buffer(initialCapacity));
    }
}
