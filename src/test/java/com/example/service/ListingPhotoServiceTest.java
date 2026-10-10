package com.example.service;

import com.example.dto.PhotoUploadDto;
import com.example.dto.PhotoUploadRequest;
import com.example.exception.ListingStateException;
import com.example.model.AppUser;
import com.example.model.Listing;
import com.example.model.ListingDetails;
import com.example.model.ListingPhoto;
import com.example.model.PhotoStatus;
import com.example.model.Role;
import com.example.repository.ListingPhotoRepository;
import com.example.repository.ListingRepository;
import com.example.security.AppUserPrincipal;
import com.example.storage.PhotoStorage;
import com.example.storage.PresignedUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

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
    private ListingPhotoService service;

    @BeforeEach
    void setUp()
    {
        service = new ListingPhotoService(new ListingLoader(listings, new ListingAccessPolicy()), photos, storage, Clock.fixed(NOW, ZoneOffset.UTC));
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
