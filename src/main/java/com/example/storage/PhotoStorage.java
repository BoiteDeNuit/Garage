package com.example.storage;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Фото не идут через приложение: клиент кладёт файл прямо в хранилище по presigned PUT.
// Приложение не держит поток на каждую загрузку и не платит за трафик дважды
@Component
public class PhotoStorage {
    private final S3Presigner presigner;
    private final StorageProperties properties;
    public PhotoStorage(S3Presigner presigner, StorageProperties properties)
    {
        this.presigner=presigner;
        this.properties=properties;
    }
    // Тип и размер входят в подпись: файл другого типа или длины хранилище отвергнет с 403.
    // Presigned PUT сам размер не ограничивает, поэтому клиент заявляет его заранее, а мы подписываем ровно его
    public PresignedUpload presignUpload(String key, String contentType, long sizeBytes)
    {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(request -> request
                .signatureDuration(properties.uploadTtl())
                .putObjectRequest(put));
        return new PresignedUpload(presigned.url(), singleValues(presigned.signedHeaders()), presigned.expiration());
    }
    // SDK отдаёт имена в нижнем регистре (content-type), клиенту привычнее Content-Type: регистр имён HTTP не важен.
    // host клиент выставит сам по адресу ссылки, передавать его не нужно
    private Map<String, String> singleValues(Map<String, List<String>> signedHeaders)
    {
        Map<String, String> headers = new HashMap<>();
        signedHeaders.forEach((name, values) -> {
            if(!name.equalsIgnoreCase("host"))
            {
                headers.put(canonical(name), String.join(",", values));
            }
        });
        return headers;
    }
    private String canonical(String name)
    {
        StringBuilder result = new StringBuilder();
        for(String part : name.split("-"))
        {
            if(!result.isEmpty())
            {
                result.append('-');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }
}
