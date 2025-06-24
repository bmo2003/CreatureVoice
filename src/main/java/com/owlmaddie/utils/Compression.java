// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;

/**
 * The {@code Compression} class is used to compress a JSON string and return a byte array.
 */
public class Compression {

    public static byte[] compressString(String data) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length())) {
            Deflater deflater = new Deflater();
            deflater.setInput(data.getBytes());
            deflater.finish();

            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer); // Returns the generated code... index
                outputStream.write(buffer, 0, count);
            }
            deflater.end();
            return outputStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
