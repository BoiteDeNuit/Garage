package com.example.storage;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Фото не идут через приложение: клиент кладёт файл прямо в хранилище по presigned PUT.
// Приложение не держит поток на каждую загрузку и не платит за трафик дважды
@Component
public class PhotoStorage {
    private final S3Client client;
    private final S3Presigner presigner;
    private final StorageProperties properties;
    public PhotoStorage(S3Client client, S3Presigner presigner, StorageProperties properties)
    {
        this.client=client;
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
    // Ссылка на чтение. Её никто не кэширует: каждый запрос списка фото подписывает заново
    public URL presignDownload(String key)
    {
        return presigner.presignGetObject(request -> request
                .signatureDuration(properties.downloadTtl())
                .getObjectRequest(get -> get.bucket(properties.bucket()).key(key))).url();
    }
    // HEAD без тела файла. Нет файла — пустой Optional: клиент взял ссылку, но ещё не загрузил
    public Optional<StoredObject> find(String key)
    {
        try
        {
            HeadObjectResponse head = client.headObject(request -> request.bucket(properties.bucket()).key(key));
            return Optional.of(new StoredObject(head.contentLength(), head.contentType()));
        }
        catch (NoSuchKeyException e)
        {
            return Optional.empty();
        }
    }
    // Только начало файла (Range), чтобы узнать тип по сигнатуре. Весь файл до 10 МБ качать незачем
    public byte[] readHead(String key, int bytes)
    {
        return client.getObjectAsBytes(request -> request.bucket(properties.bucket()).key(key).range("bytes=0-" + (bytes - 1))).asByteArray();
    }
    public void delete(String key)
    {
        client.deleteObject(request -> request.bucket(properties.bucket()).key(key));
    }
    // Один запрос DeleteObjects на все ключи (до 1000). Возвращает, сколько хранилище удалить не смогло
    public int deleteAll(List<String> keys)
    {
        List<ObjectIdentifier> objects = keys.stream().map(key -> ObjectIdentifier.builder().key(key).build()).toList();
        DeleteObjectsResponse response = client.deleteObjects(request -> request
                .bucket(properties.bucket())
                .delete(delete -> delete.objects(objects).quiet(true)));
        return response.errors().size();
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
