package com.example.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

// endpoint — куда ходит само приложение, publicEndpoint — что попадает в presigned-ссылки для клиента.
// В compose это разные адреса: приложение видит хранилище как http://s3:4566, а браузер — как localhost:4566.
// Подпись включает хост, поэтому ссылку нельзя переписать после подписи, её сразу подписывают под публичный адрес.
// @Validated: пустой адрес или ключ — отказ при старте с именем свойства, а не ошибка разбора URI из глубины SDK
@ConfigurationProperties("storage")
@Validated
public record StorageProperties(
        @NotBlank(message = "задайте STORAGE_ENDPOINT — адрес S3 для приложения") String endpoint,
        @NotBlank(message = "задайте STORAGE_PUBLIC_ENDPOINT — адрес S3 в ссылках для клиента") String publicEndpoint,
        @NotBlank(message = "задайте STORAGE_REGION") String region,
        @NotBlank(message = "задайте STORAGE_BUCKET") String bucket,
        @NotBlank(message = "задайте STORAGE_ACCESS_KEY") String accessKey,
        @NotBlank(message = "задайте STORAGE_SECRET_KEY") String secretKey,
        @NotNull Duration uploadTtl,
        @NotNull Duration downloadTtl) {
}
