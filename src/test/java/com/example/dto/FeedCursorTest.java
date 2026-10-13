package com.example.dto;

import com.example.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedCursorTest {

    @Test
    void roundTripKeepsMicroseconds()
    {
        FeedCursor cursor = new FeedCursor(Instant.parse("2026-10-09T12:30:45.123456Z"), 1000L);

        assertThat(FeedCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    // В базе timestamptz хранит микросекунды, наносекунд там не бывает. Если пришли — отбрасываются
    @Test
    void nanosecondsAreTruncatedToMicroseconds()
    {
        FeedCursor cursor = new FeedCursor(Instant.parse("2026-10-09T12:30:45.123456789Z"), 7L);

        assertThat(FeedCursor.decode(cursor.encode()).publishedAt()).isEqualTo(Instant.parse("2026-10-09T12:30:45.123456Z"));
    }

    @Test
    void cursorIsUrlSafe()
    {
        String encoded = new FeedCursor(Instant.parse("2026-10-09T12:30:45.123456Z"), Long.MAX_VALUE).encode();

        assertThat(encoded).matches("[A-Za-z0-9_-]+");
    }

    @ParameterizedTest
    @ValueSource(strings = {"не base64 %%%", "v2:1:1", "v1:1", "v1:abc:1", "v1:1:-5", "v1:-1:5", "v1:1:0", "v1:1:2:3"})
    void brokenCursorIs400(String raw)
    {
        String cursor = raw.contains("%") ? raw : Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> FeedCursor.decode(cursor))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Неверный курсор");
    }
}
