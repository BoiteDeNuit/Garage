package com.example.storage;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class PhotoSignatureTest {
    // Начало настоящих файлов в hex. Короткий обрывок и чужой формат под видом фото не проходят
    @ParameterizedTest
    @CsvSource({
            "image/jpeg, ffd8ffe000104a4649460001, true",
            "image/png,  89504e470d0a1a0a0000000d, true",
            "image/webp, 52494646a40f000057454250, true",
            "image/png,  ffd8ffe000104a4649460001, false",
            "image/jpeg, 255044462d312e370a25e2e3, false",
            "image/webp, 52494646a40f000041564920, false",
            "image/jpeg, ffd8,                     false",
            "image/gif,  474946383961010001000000, false"})
    void recognisesFormatByFirstBytes(String contentType, String hex, boolean expected)
    {
        assertThat(PhotoSignature.matches(contentType, HexFormat.of().parseHex(hex))).isEqualTo(expected);
    }
}
