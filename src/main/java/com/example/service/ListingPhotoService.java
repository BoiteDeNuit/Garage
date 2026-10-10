package com.example.service;

import com.example.dto.ListingDto;
import com.example.dto.PhotoDto;
import com.example.dto.PhotoUploadDto;
import com.example.dto.PhotoUploadRequest;
import com.example.exception.EntityNotFoundException;
import com.example.exception.InvalidRequestException;
import com.example.exception.ListingStateException;
import com.example.exception.PhotoStateException;
import com.example.model.Listing;
import com.example.model.ListingAction;
import com.example.model.ListingPhoto;
import com.example.model.PhotoStatus;
import com.example.repository.ListingPhotoRepository;
import com.example.security.AppUserPrincipal;
import com.example.storage.PhotoFilesRemoved;
import com.example.storage.PhotoSignature;
import com.example.storage.PhotoStorage;
import com.example.storage.PresignedUpload;
import com.example.storage.StoredObject;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ListingPhotoService {
    // Тот же предел стоит CHECK-ом в базе (V15)
    static final int MAX_PHOTOS = 20;
    private final ListingLoader loader;
    private final ListingReader reader;
    private final ListingAccessPolicy policy;
    private final ListingPhotoRepository photos;
    private final PhotoStorage storage;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    public ListingPhotoService(ListingLoader loader,
                               ListingReader reader,
                               ListingAccessPolicy policy,
                               ListingPhotoRepository photos,
                               PhotoStorage storage,
                               PlatformTransactionManager transactionManager,
                               ApplicationEventPublisher events,
                               Clock clock)
    {
        this.loader=loader;
        this.reader=reader;
        this.policy=policy;
        this.photos=photos;
        this.storage=storage;
        this.transactions=new TransactionTemplate(transactionManager);
        this.events=events;
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
    // Подтверждение загрузки. Хранилище спрашивается вне транзакции: соединение с базой не держится, пока ждём S3.
    // Поэтому три коротких транзакции вместо одной: проверить права, при отказе удалить строку, при успехе отметить READY.
    // Повторное подтверждение READY-фото ничего не меняет
    public PhotoDto confirm(Long listingId, Long photoId, AppUserPrincipal actor)
    {
        ListingPhoto photo = transactions.execute(status -> photoForChange(listingId, photoId, actor));
        if(photo.getStatus() == PhotoStatus.READY)
        {
            return toDto(photo);
        }
        StoredObject stored = storage.find(photo.getObjectKey()).orElseThrow(PhotoStateException::notUploaded);
        byte[] head = storage.readHead(photo.getObjectKey(), PhotoSignature.HEAD_BYTES);
        boolean sameFile = stored.sizeBytes() == photo.getSizeBytes() && photo.getContentType().equals(stored.contentType());
        if(!sameFile || !PhotoSignature.matches(photo.getContentType(), head))
        {
            // Чужой формат под видом фото: файл и место освобождаются, загружать заново
            storage.delete(photo.getObjectKey());
            transactions.executeWithoutResult(status -> photos.deleteById(photoId));
            throw new InvalidRequestException("Файл не " + photo.getContentType() + ", фото удалено. Загрузите заново");
        }
        return transactions.execute(status -> {
            ListingPhoto fresh = photos.findById(photoId).orElseThrow(() -> photoNotFound(photoId));
            fresh.markReady();
            return toDto(fresh);
        });
    }
    // Готовые фото по порядку. Кто видит объявление, тот видит и фото: черновик — только продавец и админ.
    // Ссылки подписываются на каждый запрос и нигде не кэшируются, поэтому не протухают в кэше карточки
    @Transactional(readOnly = true)
    public List<PhotoDto> readyPhotos(Long listingId, @Nullable AppUserPrincipal viewer)
    {
        ListingDto card = reader.findCard(listingId);
        if(!policy.canView(card.status(), card.sellerId(), viewer))
        {
            throw EntityNotFoundException.listing(listingId);
        }
        return photos.findByListingIdAndStatusOrderByPosition(listingId, PhotoStatus.READY).stream()
                .map(this::toDto)
                .toList();
    }
    // Строка и сдвиг остальных фото — в транзакции, файл из хранилища — после коммита (PhotoFilesCleaner)
    @Transactional
    public void delete(Long listingId, Long photoId, AppUserPrincipal actor)
    {
        ListingPhoto photo = photoForChange(listingId, photoId, actor);
        photos.delete(photo);
        photos.closeGap(listingId, photo.getPosition());
        events.publishEvent(new PhotoFilesRemoved(List.of(photo.getObjectKey())));
    }
    // Новый порядок — список всех фото объявления, включая неподтверждённые: места у них общие.
    // Обмен местами проходит потому, что уникальность места проверяется при коммите (V15)
    @Transactional
    public List<PhotoDto> reorder(Long listingId, List<Long> photoIds, AppUserPrincipal actor)
    {
        loader.forChange(listingId, actor, ListingAction.EDIT).checkEditable();
        List<ListingPhoto> all = photos.findByListingIdOrderByPosition(listingId);
        Map<Long, ListingPhoto> byId = all.stream().collect(Collectors.toMap(ListingPhoto::getId, photo -> photo));
        if(photoIds.size() != all.size() || new HashSet<>(photoIds).size() != photoIds.size() || !byId.keySet().containsAll(photoIds))
        {
            throw new InvalidRequestException("Нужен порядок всех фото объявления, каждое по одному разу");
        }
        for(int i = 0; i < photoIds.size(); i++)
        {
            byId.get(photoIds.get(i)).moveTo(i + 1);
        }
        return photoIds.stream()
                .map(byId::get)
                .filter(photo -> photo.getStatus() == PhotoStatus.READY)
                .map(this::toDto)
                .toList();
    }
    private ListingPhoto photoForChange(Long listingId, Long photoId, AppUserPrincipal actor)
    {
        loader.forChange(listingId, actor, ListingAction.EDIT).checkEditable();
        return photos.findByIdAndListingId(photoId, listingId).orElseThrow(() -> photoNotFound(photoId));
    }
    private PhotoDto toDto(ListingPhoto photo)
    {
        return new PhotoDto(photo.getId(), photo.getPosition(), storage.presignDownload(photo.getObjectKey()).toString());
    }
    private EntityNotFoundException photoNotFound(Long photoId)
    {
        return new EntityNotFoundException("Фото с id: " + photoId + " не найдено");
    }
}
