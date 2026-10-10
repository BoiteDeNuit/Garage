package com.example.service;

import com.example.dto.ListingDto;
import com.example.dto.PhotoDto;
import com.example.dto.PhotoUploadDto;
import com.example.dto.PhotoUploadRequest;
import com.example.exception.EntityNotFoundException;
import com.example.exception.InvalidRequestException;
import com.example.exception.ListingStateException;
import com.example.exception.PhotoStateException;
import com.example.model.AppUser;
import com.example.model.Listing;
import com.example.model.ListingDetails;
import com.example.model.ListingPhoto;
import com.example.model.ListingStatus;
import com.example.model.PhotoStatus;
import com.example.model.Role;
import com.example.repository.ListingPhotoRepository;
import com.example.repository.ListingRepository;
import com.example.security.AppUserPrincipal;
import com.example.storage.PhotoSignature;
import com.example.storage.PhotoStorage;
import com.example.storage.PresignedUpload;
import com.example.storage.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingPhotoServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    @Mock
    private ListingRepository listings;
    @Mock
    private ListingPhotoRepository photos;
    @Mock
    private PhotoStorage storage;
    @Mock
    private ListingReader reader;
    @Mock
    private PlatformTransactionManager transactionManager;
    private ListingPhotoService service;

    @BeforeEach
    void setUp()
    {
        ListingAccessPolicy policy = new ListingAccessPolicy();
        service = new ListingPhotoService(new ListingLoader(listings, policy), reader, policy, photos, storage, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // Место — следующее за последним, ключ из UUID под каталогом объявления, в подпись уходят заявленные тип и размер
    @Test
    void sellerGetsLinkForNextPlace() throws Exception
    {
        when(listings.findById(1L)).thenReturn(Optional.of(listing()));
        when(photos.maxPosition(1L)).thenReturn(3);
        when(photos.save(any(ListingPhoto.class))).thenAnswer(call -> {
            ListingPhoto photo = call.getArgument(0);
            ReflectionTestUtils.setField(photo, "id", 50L);
            return photo;
        });
        when(storage.presignUpload(anyString(), anyString(), anyLong()))
                .thenReturn(new PresignedUpload(URI.create("http://s3/upload").toURL(), Map.of("Content-Type", "image/png"), NOW.plusSeconds(600)));

        PhotoUploadDto result = service.startUpload(1L, new PhotoUploadRequest("image/png", 2000L), principal(7L));

        ArgumentCaptor<ListingPhoto> saved = ArgumentCaptor.forClass(ListingPhoto.class);
        verify(photos).save(saved.capture());
        ListingPhoto photo = saved.getValue();
        assertThat(photo.getPosition()).isEqualTo(4);
        assertThat(photo.getStatus()).isEqualTo(PhotoStatus.PENDING);
        assertThat(photo.getCreatedAt()).isEqualTo(NOW);
        assertThat(photo.getObjectKey()).matches("listings/1/[0-9a-f-]{36}");
        verify(storage).presignUpload(photo.getObjectKey(), "image/png", 2000L);
        assertThat(result.photoId()).isEqualTo(50L);
        assertThat(result.uploadUrl()).isEqualTo("http://s3/upload");
        assertThat(result.method()).isEqualTo("PUT");
    }

    @Test
    void twentyFirstPhotoIsConflictWithoutLink()
    {
        when(listings.findById(1L)).thenReturn(Optional.of(listing()));
        when(photos.maxPosition(1L)).thenReturn(20);

        assertThatThrownBy(() -> service.startUpload(1L, new PhotoUploadRequest("image/jpeg", 100L), principal(7L)))
                .isInstanceOf(ListingStateException.class)
                .hasMessage("Не больше 20 фото на объявление");
        verify(photos, never()).save(any());
        verifyNoInteractions(storage);
    }

    @Test
    void strangerGetsNoLink()
    {
        Listing active = listing();
        active.publish(NOW);
        when(listings.findById(1L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.startUpload(1L, new PhotoUploadRequest("image/jpeg", 100L), principal(8L)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(photos, storage);
    }

    @Test
    void soldListingGetsNoLink()
    {
        Listing sold = listing();
        sold.publish(NOW);
        sold.markSold(NOW);
        when(listings.findById(1L)).thenReturn(Optional.of(sold));

        assertThatThrownBy(() -> service.startUpload(1L, new PhotoUploadRequest("image/jpeg", 100L), principal(7L)))
                .isInstanceOf(ListingStateException.class)
                .hasMessage("Проданное объявление менять нельзя");
        verifyNoInteractions(photos, storage);
    }

    @Test
    void confirmWithoutFileIsConflictAndKeepsPhoto()
    {
        ListingPhoto photo = pendingPhoto();
        when(listings.findById(1L)).thenReturn(Optional.of(listing()));
        when(photos.findByIdAndListingId(50L, 1L)).thenReturn(Optional.of(photo));
        when(storage.find(photo.getObjectKey())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(1L, 50L, principal(7L)))
                .isInstanceOf(PhotoStateException.class)
                .hasMessage("Файл ещё не загружен в хранилище");
        verify(photos, never()).deleteById(any());
        assertThat(photo.getStatus()).isEqualTo(PhotoStatus.PENDING);
    }

    // Размер и заголовок совпали с подписью, но внутри не JPEG: файл и строка удаляются, место свободно
    @Test
    void confirmOfForeignContentDeletesFileAndPhoto()
    {
        ListingPhoto photo = pendingPhoto();
        when(listings.findById(1L)).thenReturn(Optional.of(listing()));
        when(photos.findByIdAndListingId(50L, 1L)).thenReturn(Optional.of(photo));
        when(storage.find(photo.getObjectKey())).thenReturn(Optional.of(new StoredObject(2000L, "image/jpeg")));
        when(storage.readHead(photo.getObjectKey(), PhotoSignature.HEAD_BYTES)).thenReturn("%PDF-1.7 ....".getBytes());

        assertThatThrownBy(() -> service.confirm(1L, 50L, principal(7L)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Файл не image/jpeg, фото удалено. Загрузите заново");
        verify(storage).delete(photo.getObjectKey());
        verify(photos).deleteById(50L);
    }

    @Test
    void confirmMarksReady() throws Exception
    {
        ListingPhoto photo = pendingPhoto();
        when(listings.findById(1L)).thenReturn(Optional.of(listing()));
        when(photos.findByIdAndListingId(50L, 1L)).thenReturn(Optional.of(photo));
        when(photos.findById(50L)).thenReturn(Optional.of(photo));
        when(storage.find(photo.getObjectKey())).thenReturn(Optional.of(new StoredObject(2000L, "image/jpeg")));
        when(storage.readHead(photo.getObjectKey(), PhotoSignature.HEAD_BYTES)).thenReturn(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
        when(storage.presignDownload(photo.getObjectKey())).thenReturn(URI.create("http://s3/get").toURL());

        PhotoDto result = service.confirm(1L, 50L, principal(7L));

        assertThat(photo.getStatus()).isEqualTo(PhotoStatus.READY);
        assertThat(result.url()).isEqualTo("http://s3/get");
        verify(storage, never()).delete(anyString());
    }

    // Чужой продавец не подтверждает: отказ до похода в хранилище
    @Test
    void strangerCannotConfirm()
    {
        Listing active = listing();
        active.publish(NOW);
        when(listings.findById(1L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.confirm(1L, 50L, principal(8L))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(storage, photos);
    }

    @Test
    void draftPhotosAreHiddenFromStrangers()
    {
        when(reader.findCard(1L)).thenReturn(card(ListingStatus.DRAFT));

        assertThatThrownBy(() -> service.readyPhotos(1L, null)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.readyPhotos(1L, principal(8L))).isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(photos, storage);
    }

    private ListingPhoto pendingPhoto()
    {
        ListingPhoto photo = ListingPhoto.pending(listing(), "listings/1/abc", "image/jpeg", 2000L, 1, NOW);
        ReflectionTestUtils.setField(photo, "id", 50L);
        return photo;
    }

    private ListingDto card(ListingStatus status)
    {
        return new ListingDto(1L, 7L, status, "Toyota", "Supra", "2JZ", 320, 1998, 154000, null, null, null,
                new BigDecimal("4500000"), "Самара", null, NOW, NOW, null, 0L);
    }

    private Listing listing()
    {
        AppUser seller = new AppUser("u7", "!", Role.USER);
        ReflectionTestUtils.setField(seller, "id", 7L);
        Listing listing = Listing.draft(seller, new ListingDetails("Toyota", "Supra", "2JZ", 320, 1998, 154000, null, null, null,
                new BigDecimal("4500000"), "Самара", null), NOW);
        ReflectionTestUtils.setField(listing, "id", 1L);
        return listing;
    }

    private AppUserPrincipal principal(Long id)
    {
        return new AppUserPrincipal(id, "u" + id, "!", Role.USER);
    }
}
