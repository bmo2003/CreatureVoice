package com.owlmaddie.utils;

import java.io.ByteArrayOutputStream;
import java.util.zip.Inflater;

/**
 * The {@code Decompression} class is used to decompress a JSON byte array and return a string.
 */
public class Decompression {

    public static String decompressString(byte[] data) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length)) {
            Inflater inflater = new Inflater();
            inflater.setInput(data);

            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            inflater.end();
            return outputStream.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
