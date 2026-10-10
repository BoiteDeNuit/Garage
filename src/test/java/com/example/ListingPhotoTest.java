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
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

        assertThat(putToStorage(upload, file, "image/jpeg").statusCode()).isEqualTo(200);

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

    // Неподтверждённое фото не видно. После подтверждения оно в списке, и по ссылке читается тот же файл
    @Test
    void photoIsVisibleOnlyAfterConfirm() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long listingId = insertListing(seller, uniqueBrand(), ListingStatus.ACTIVE);
        byte[] file = jpeg(3000);
        JsonNode upload = json(startUpload(listingId, seller, "image/jpeg", file.length));
        putToStorage(upload, file, "image/jpeg");
        long photoId = upload.get("photoId").asLong();

        assertThat(json(mockMvc.perform(get("/api/listings/{id}/photos", listingId))).isEmpty()).isTrue();

        mockMvc.perform(post("/api/listings/{id}/photos/{photoId}/confirm", listingId, photoId).header("Authorization", bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1));
        mockMvc.perform(post("/api/listings/{id}/photos/{photoId}/confirm", listingId, photoId).header("Authorization", bearer(seller)))
                .andExpect(status().isOk());

        JsonNode photos = json(mockMvc.perform(get("/api/listings/{id}/photos", listingId)));
        assertThat(photos).hasSize(1);
        HttpResponse<byte[]> download = http.send(HttpRequest.newBuilder(URI.create(photos.get(0).get("url").asString())).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(file);
    }

    @Test
    void confirmBeforeUploadIs409() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long listingId = insertListing(seller, uniqueBrand(), ListingStatus.DRAFT);
        long photoId = json(startUpload(listingId, seller, "image/png", 100)).get("photoId").asLong();

        mockMvc.perform(post("/api/listings/{id}/photos/{photoId}/confirm", listingId, photoId).header("Authorization", bearer(seller)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Файл ещё не загружен в хранилище"));
    }

    // Заголовок и размер совпали с подписью, а внутри PDF. Подтверждение смотрит первые байты: файл удалён, место свободно
    @Test
    void foreignContentIsDeletedOnConfirm() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long listingId = insertListing(seller, uniqueBrand(), ListingStatus.DRAFT);
        byte[] pdf = "%PDF-1.7 not a photo".getBytes();
        JsonNode upload = json(startUpload(listingId, seller, "image/jpeg", pdf.length));
        assertThat(putToStorage(upload, pdf, "image/jpeg").statusCode()).isEqualTo(200);
        long photoId = upload.get("photoId").asLong();
        String key = photoRepository.findById(photoId).orElseThrow().getObjectKey();

        mockMvc.perform(post("/api/listings/{id}/photos/{photoId}/confirm", listingId, photoId).header("Authorization", bearer(seller)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Файл не image/jpeg, фото удалено. Загрузите заново"));

        assertThat(photoRepository.findById(photoId)).isEmpty();
        assertFileIsGone(key);
    }

    // Фото черновика видит только продавец. Чужой photoId под своим объявлением не находится
    @Test
    void photosFollowListingVisibility() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long draft = insertListing(seller, uniqueBrand(), ListingStatus.DRAFT);
        Long other = insertListing(seller, uniqueBrand(), ListingStatus.DRAFT);
        insertPhoto(draft, 1);
        long otherPhoto = json(startUpload(other, seller, "image/png", 100)).get("photoId").asLong();

        mockMvc.perform(get("/api/listings/{id}/photos", draft)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/listings/{id}/photos", draft).header("Authorization", bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].position").value(1));
        mockMvc.perform(post("/api/listings/{id}/photos/{photoId}/confirm", draft, otherPhoto).header("Authorization", bearer(seller)))
                .andExpect(status().isNotFound());
    }

    // Удалили второе из трёх: третье встало на его место, файл ушёл из хранилища после коммита
    @Test
    void deletingPhotoClosesGapAndRemovesFile() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long listingId = insertListing(seller, uniqueBrand(), ListingStatus.ACTIVE);
        long first = uploadedPhoto(listingId, seller);
        long second = uploadedPhoto(listingId, seller);
        long third = uploadedPhoto(listingId, seller);
        String secondKey = photoRepository.findById(second).orElseThrow().getObjectKey();

        mockMvc.perform(delete("/api/listings/{id}/photos/{photoId}", listingId, second).header("Authorization", bearer(seller)))
                .andExpect(status().isNoContent());

        JsonNode photos = json(mockMvc.perform(get("/api/listings/{id}/photos", listingId)));
        assertThat(photos.valueStream().map(photo -> photo.get("id").asLong()).toList()).containsExactly(first, third);
        assertThat(photos.valueStream().map(photo -> photo.get("position").asInt()).toList()).containsExactly(1, 2);
        assertFileIsGone(secondKey);
    }

    // Обмен местами: на полпути у двух фото одно место. Проходит только с отложенной уникальностью
    @Test
    void photosSwapPlaces() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long listingId = insertListing(seller, uniqueBrand(), ListingStatus.DRAFT);
        long first = uploadedPhoto(listingId, seller);
        long second = uploadedPhoto(listingId, seller);

        mockMvc.perform(put("/api/listings/{id}/photos/order", listingId)
                        .header("Authorization", bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("photoIds", List.of(second, first)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[0].position").value(1));

        assertThat(photoRepository.findById(first).orElseThrow().getPosition()).isEqualTo(2);
        assertThat(photoRepository.findById(second).orElseThrow().getPosition()).isEqualTo(1);
    }

    @Test
    void orderWithoutEveryPhotoIs400() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long listingId = insertListing(seller, uniqueBrand(), ListingStatus.DRAFT);
        long first = uploadedPhoto(listingId, seller);
        uploadedPhoto(listingId, seller);

        mockMvc.perform(put("/api/listings/{id}/photos/order", listingId)
                        .header("Authorization", bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("photoIds", List.of(first)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Нужен порядок всех фото объявления, каждое по одному разу"));
    }

    // Строки фото база удаляет каскадом вместе с черновиком, файлы — слушатель после коммита
    @Test
    void deletingDraftRemovesItsFiles() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long listingId = insertListing(seller, uniqueBrand(), ListingStatus.DRAFT);
        String key = photoRepository.findById(uploadedPhoto(listingId, seller)).orElseThrow().getObjectKey();

        mockMvc.perform(delete("/api/listings/{id}", listingId).header("Authorization", bearer(seller)))
                .andExpect(status().isNoContent());

        assertThat(photoRepository.findObjectKeys(listingId)).isEmpty();
        assertFileIsGone(key);
    }

    // Тип и размер вошли в подпись: подменить файл после выдачи ссылки нельзя
    @Test
    void storageRejectsOtherSizeOrType() throws Exception
    {
        AppUser seller = createUser(Role.USER);
        Long listingId = insertListing(seller, uniqueBrand(), ListingStatus.DRAFT);
        JsonNode upload = startUpload(listingId, seller, "image/jpeg", 1000)
                .andReturn().getResponse().getContentAsString().transform(objectMapper::readTree);

        assertThat(putToStorage(upload, jpeg(5000), "image/jpeg").statusCode()).isEqualTo(403);
        assertThat(putToStorage(upload, jpeg(1000), "image/png").statusCode()).isEqualTo(403);
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

    // Полный путь фото: ссылка, PUT в хранилище, подтверждение
    private long uploadedPhoto(Long listingId, AppUser seller) throws Exception
    {
        byte[] file = jpeg(1500);
        JsonNode upload = json(startUpload(listingId, seller, "image/jpeg", file.length));
        assertThat(putToStorage(upload, file, "image/jpeg").statusCode()).isEqualTo(200);
        long photoId = upload.get("photoId").asLong();
        mockMvc.perform(post("/api/listings/{id}/photos/{photoId}/confirm", listingId, photoId).header("Authorization", bearer(seller)))
                .andExpect(status().isOk());
        return photoId;
    }

    private void assertFileIsGone(String key)
    {
        try (S3Client client = testS3Client())
        {
            assertThatThrownBy(() -> client.headObject(request -> request.bucket(BUCKET).key(key))).isInstanceOf(NoSuchKeyException.class);
        }
    }

    private JsonNode json(ResultActions result) throws Exception
    {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private HttpResponse<String> putToStorage(JsonNode upload, byte[] body, String contentType) throws Exception
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
