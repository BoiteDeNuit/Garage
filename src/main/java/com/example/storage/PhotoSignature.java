package com.example.storage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

// Тип файла по первым байтам. Content-Type присылает клиент, верить ему нельзя: под image/jpeg может лежать что угодно.
// Подпись ссылки гарантирует только, что заголовок совпал с заявленным, а не содержимое
public final class PhotoSignature {
    // Сколько байт читать из начала файла: WebP узнаётся по 12-ти
    public static final int HEAD_BYTES = 12;
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);
    private PhotoSignature() {}

    public static boolean matches(String contentType, byte[] head)
    {
        return switch (contentType)
        {
            case "image/jpeg" -> startsWith(head, 0, JPEG);
            case "image/png" -> startsWith(head, 0, PNG);
            // RIFF, четыре байта длины, WEBP
            case "image/webp" -> startsWith(head, 0, RIFF) && startsWith(head, 8, WEBP);
            default -> false;
        };
    }
    private static boolean startsWith(byte[] head, int offset, byte[] signature)
    {
        return head.length >= offset + signature.length
                && Arrays.equals(head, offset, offset + signature.length, signature, 0, signature.length);
    }
}
