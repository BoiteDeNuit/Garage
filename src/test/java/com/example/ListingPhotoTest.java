package com.example;

import com.example.dto.ErrorResponse;
import com.example.exception.GlobalExceptionHandler;
import com.example.model.AppUser;
import com.example.model.ListingPhoto;
import com.example.model.ListingStatus;
import com.example.model.Role;
import com.example.repository.ListingPhotoRepository;
import com.example.repository.ListingRepository;
import com.example.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Фото через настоящее S3-хранилище (LocalStack): ссылка от приложения, файл — обычным HTTP PUT мимо приложения
class ListingPhotoTest extends IntegrationTest {
    // Эти заголовки java.net.http выставляет сам и задать их не даёт
    private static final Set<String> SET_BY_CLIENT = Set.of("content-length", "host");
    private final HttpClient http = HttpClient.newHttpClient();
    @Autowired
    ListingPhotoRepository photoRepository;
    @Autowired
    ListingRepository listingRepository;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    GlobalExceptionHandler exceptionHandler;

    @Test
    void sellerUploadsPhotoStraightToStorage() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long listingId = insertListing(seller, uniqueBrand(), ListingStatus.DRAFT);
        byte[] file = jpeg(2048);

        JsonNode upload = startUpload(listingId, seller, "image/jpeg", file.length)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.headers['Content-Type']").value("image/jpeg"))
                .andReturn().getResponse().getContentAsString().transform(objectMapper::readTree);

        assertThat(put(upload, file, "image/jpeg").statusCode()).isEqualTo(200);

        ListingPhoto photo = photoRepository.findById(upload.get("photoId").asLong()).orElseThrow();
        assertThat(photo.getObjectKey()).startsWith("listings/" + listingId + "/");
        assertThat(photo.getPosition()).isEqualTo(1);
        try (S3Client client = testS3Client())
        {
            HeadObjectResponse stored = client.headObject(request -> request.bucket(BUCKET).key(photo.getObjectKey()));
            assertThat(stored.contentLength()).isEqualTo(file.length);
            assertThat(stored.contentType()).isEqualTo("image/jpeg");
        }
    }

    // Тип и размер вошли в подпись: подменить файл после выдачи ссылки нельзя
    @Test
    void storageRejectsOtherSizeOrType() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long listingId = insertListing(seller, uniqueBrand(), ListingStatus.DRAFT);
        JsonNode upload = startUpload(listingId, seller, "image/jpeg", 1000)
                .andReturn().getResponse().getContentAsString().transform(objectMapper::readTree);

        assertThat(put(upload, jpeg(5000), "image/jpeg").statusCode()).isEqualTo(403);
        assertThat(put(upload, jpeg(1000), "image/png").statusCode()).isEqualTo(403);
    }

    @Test
    void strangerGets403AndHiddenListingIs404() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        AppUser stranger = createUser(Role.USER);
        Long active = insertListing(seller, uniqueBrand(), ListingStatus.ACTIVE);
        Long draft = insertListing(seller, uniqueBrand(), ListingStatus.DRAFT);

        startUpload(active, stranger, "image/jpeg", 1000).andExpect(status().isForbidden());
        startUpload(draft, stranger, "image/jpeg", 1000).andExpect(status().isNotFound());
    }

    @Test
    void soldListingTakesNoPhotos() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long sold = insertListing(seller, uniqueBrand(), ListingStatus.SOLD);

        startUpload(sold, seller, "image/jpeg", 1000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Проданное объявление менять нельзя"));
    }

    @Test
    void twentyFirstPhotoIs409() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long listingId = insertListing(seller, uniqueBrand(), ListingStatus.DRAFT);
        for (int position = 1; position <= 20; position++)
        {
            insertPhoto(listingId, position);
        }

        startUpload(listingId, seller, "image/jpeg", 1000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Не больше 20 фото на объявление"));
    }

    @Test
    void wrongTypeOrSizeIs400() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long listingId = insertListing(seller, uniqueBrand(), ListingStatus.DRAFT);

        startUpload(listingId, seller, "image/gif", 1000)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Фото только JPEG, PNG или WebP"));
        startUpload(listingId, seller, "image/jpeg", 10 * 1024 * 1024 + 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Фото не больше 10 МБ"));
    }

    // Гонка двух загрузок: обе посчитали одно место. Уникальность отложенная, поэтому отказ приходит при коммите,
    // уже за пределами сервиса. Исключение то, что получит обработчик: он отвечает 409, а не 500
    @Test
    void twoPhotosOnOnePlaceFailAtCommitWith409()
    {
        AppUser seller = createUser(Role.USER);
        Long listingId = insertListing(seller, uniqueBrand(), ListingStatus.DRAFT);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        DataIntegrityViolationException race = catchThrowableOfType(DataIntegrityViolationException.class, () -> transaction.executeWithoutResult(status -> {
            photoRepository.save(ListingPhoto.pending(listingRepository.getReferenceById(listingId), "race/" + listingId + "/1", "image/jpeg", 10, 1, Instant.now()));
            photoRepository.save(ListingPhoto.pending(listingRepository.getReferenceById(listingId), "race/" + listingId + "/2", "image/jpeg", 10, 1, Instant.now()));
            photoRepository.flush();
        }));

        assertThat(race).isNotNull();
        ResponseEntity<ErrorResponse> response = exceptionHandler.constraintViolation(race, new MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().message()).isEqualTo("Фото загружают одновременно, повторите");
        assertThat(photoRepository.maxPosition(listingId)).isZero();
    }

    private ResultActions startUpload(Long listingId, AppUser actor, String contentType, long size) throws Exception
    {
        return mockMvc.perform(post("/api/listings/{id}/photos", listingId)
                .header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("contentType", contentType, "sizeBytes", size))));
    }

    private HttpResponse<String> put(JsonNode upload, byte[] body, String contentType) throws Exception
    {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(upload.get("uploadUrl").asString()))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body));
        upload.get("headers").properties().forEach(header -> {
            if(!SET_BY_CLIENT.contains(header.getKey().toLowerCase()))
            {
                request.header(header.getKey(), header.getKey().equalsIgnoreCase("content-type") ? contentType : header.getValue().asString());
            }
        });
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void insertPhoto(Long listingId, int position)
    {
        jdbcTemplate.update("insert into listing_photos (listing_id, object_key, status, content_type, size_bytes, position, created_at) "
                + "values (?, ?, 'READY', 'image/jpeg', 1000, ?, now())", listingId, "seed/" + listingId + "/" + position, position);
    }

    // Начало JPEG (FF D8 FF) и нули до нужного размера
    private byte[] jpeg(int size)
    {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }
}
