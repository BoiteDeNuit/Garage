package com.example.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// endpoint — куда ходит само приложение, publicEndpoint — что попадает в presigned-ссылки для клиента.
// В compose это разные адреса: приложение видит хранилище как http://s3:4566, а браузер — как localhost:4566.
// Подпись включает хост, поэтому ссылку нельзя переписать после подписи, её сразу подписывают под публичный адрес
@ConfigurationProperties("storage")
public record StorageProperties(
        String endpoint,
        String publicEndpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        Duration uploadTtl) {
}
