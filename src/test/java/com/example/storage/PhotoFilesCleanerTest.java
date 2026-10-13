package com.example.storage;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoFilesCleanerTest {
    @Mock
    private PhotoStorage storage;
    private final MeterRegistry registry = new SimpleMeterRegistry();

    // Транзакция уже закоммичена: сбой хранилища не должен вылететь наружу, его только считают
    @Test
    void storageFailureIsCountedNotThrown()
    {
        when(storage.deleteAll(anyList())).thenThrow(new RuntimeException("S3 недоступно"));

        new PhotoFilesCleaner(storage, registry).onPhotoFilesRemoved(new PhotoFilesRemoved(List.of("a", "b")));

        assertThat(registry.counter("photos.files.delete.failed").count()).isEqualTo(2);
    }

    @Test
    void partlyDeletedIsCounted()
    {
        when(storage.deleteAll(List.of("a", "b", "c"))).thenReturn(1);

        new PhotoFilesCleaner(storage, registry).onPhotoFilesRemoved(new PhotoFilesRemoved(List.of("a", "b", "c")));

        assertThat(registry.counter("photos.files.delete.failed").count()).isEqualTo(1);
    }

    // Черновик без фото: в хранилище не ходим
    @Test
    void nothingToDeleteMakesNoRequest()
    {
        new PhotoFilesCleaner(storage, registry).onPhotoFilesRemoved(new PhotoFilesRemoved(List.of()));

        verifyNoInteractions(storage);
    }
}
