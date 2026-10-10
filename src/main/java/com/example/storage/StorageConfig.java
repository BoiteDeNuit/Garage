package com.example.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

// path-style (http://host/bucket/key), а не virtual-hosted (http://bucket.host/key): у LocalStack и других
// S3-совместимых хранилищ на своём адресе нет DNS под каждый бакет
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {
    @Bean(destroyMethod = "close")
    public S3Client s3Client(StorageProperties properties)
    {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .forcePathStyle(true)
                .build();
    }
    // Presigner подписывает локально, в хранилище не ходит: ссылка считается без сети
    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(StorageProperties properties)
    {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.publicEndpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
    private StaticCredentialsProvider credentials(StorageProperties properties)
    {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
}
