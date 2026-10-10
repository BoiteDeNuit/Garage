package com.example.service;

import com.example.dto.PhotoUploadDto;
import com.example.dto.PhotoUploadRequest;
import com.example.exception.ListingStateException;
import com.example.model.Listing;
import com.example.model.ListingAction;
import com.example.model.ListingPhoto;
import com.example.repository.ListingPhotoRepository;
import com.example.security.AppUserPrincipal;
import com.example.storage.PhotoStorage;
import com.example.storage.PresignedUpload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ListingPhotoService {
    // Тот же предел стоит CHECK-ом в базе (V15)
    static final int MAX_PHOTOS = 20;
    private final ListingLoader loader;
    private final ListingPhotoRepository photos;
    private final PhotoStorage storage;
    private final Clock clock;
    public ListingPhotoService(ListingLoader loader, ListingPhotoRepository photos, PhotoStorage storage, Clock clock)
    {
        this.loader=loader;
        this.photos=photos;
        this.storage=storage;
        this.clock=clock;
    }
    // Ссылка на загрузку одного фото. Права те же, что на правку: только продавец, проданное — 409.
    // Строка PENDING пишется сразу: место занято, ключ известен. Ключ из UUID, имя файла от клиента в него не попадает.
    // Не загрузил файл — строка остаётся PENDING и в карточке не видна
    @Transactional
    public PhotoUploadDto startUpload(Long listingId, PhotoUploadRequest request, AppUserPrincipal actor)
    {
        Listing listing = loader.forChange(listingId, actor, ListingAction.EDIT);
        listing.checkEditable();
        // Две одновременные загрузки получат одно место. Пройдёт одна, вторую остановит уникальность в базе при коммите
        int position = photos.maxPosition(listingId) + 1;
        if(position > MAX_PHOTOS)
        {
            throw ListingStateException.tooManyPhotos(MAX_PHOTOS);
        }
        String key = "listings/" + listingId + "/" + UUID.randomUUID();
        ListingPhoto photo = photos.save(ListingPhoto.pending(listing, key, request.contentType(), request.sizeBytes(), position, Instant.now(clock)));
        PresignedUpload upload = storage.presignUpload(key, request.contentType(), request.sizeBytes());
        return new PhotoUploadDto(photo.getId(), upload.url().toString(), "PUT", upload.headers(), upload.expiresAt());
    }
}
