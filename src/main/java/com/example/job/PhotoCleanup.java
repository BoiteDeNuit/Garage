package com.example.job;

import com.example.model.PhotoStatus;
import com.example.repository.ListingPhotoRepository;
import com.example.service.ListingPhotoService;
import com.example.storage.PhotoStorage;
import com.example.storage.StoredKey;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

// Две уборки фото. Брошенные загрузки: ссылку взяли, а подтверждения нет сутки — место освобождается, файл удаляется.
// Сироты: файл в хранилище, а строки нет. Так бывает, когда удаление файла после коммита не прошло
// (photos_files_delete_failed_total). Сиротой считается файл старше суток: строка фото всегда пишется раньше,
// чем выдаётся ссылка на загрузку, так что свежий файл без строки — только что удалённый, его удалит PhotoFilesCleaner
@Component
public class PhotoCleanup {
    private static final Logger log = LoggerFactory.getLogger(PhotoCleanup.class);
    private static final String PREFIX = "listings/";
    private final ListingPhotoRepository photos;
    private final ListingPhotoService photoService;
    private final PhotoStorage storage;
    private final Clock clock;
    private final Duration pendingTtl;
    private final Duration orphanAge;
    private final int batchSize;
    private final Counter uploadsDropped;
    private final Counter orphansDeleted;
    public PhotoCleanup(ListingPhotoRepository photos,
                        ListingPhotoService photoService,
                        PhotoStorage storage,
                        Clock clock,
                        @Value("${cleanup.photo-uploads.pending-ttl:1d}") Duration pendingTtl,
                        @Value("${cleanup.photo-files.orphan-age:1d}") Duration orphanAge,
                        @Value("${cleanup.batch-size:1000}") int batchSize,
                        MeterRegistry meterRegistry)
    {
        this.photos=photos;
        this.photoService=photoService;
        this.storage=storage;
        this.clock=clock;
        this.pendingTtl=pendingTtl;
        this.orphanAge=orphanAge;
        this.batchSize=batchSize;
        this.uploadsDropped=Counter.builder("cleanup.photo.uploads.dropped")
                .description("Удалено брошенных загрузок фото")
                .register(meterRegistry);
        this.orphansDeleted=Counter.builder("cleanup.photo.orphans.deleted")
                .description("Удалено файлов фото без строки в базе")
                .register(meterRegistry);
    }
    @Scheduled(cron = "${cleanup.photo-uploads.cron:0 15 * * * *}")
    @SchedulerLock(name = "stalePhotoUploads", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void dropStaleUploads()
    {
        Instant before = Instant.now(clock).minus(pendingTtl);
        int dropped = 0;
        Long afterId = 0L;
        List<Long> batch;
        do
        {
            batch = photos.findStaleIds(PhotoStatus.PENDING, before, afterId, PageRequest.of(0, batchSize));
            for(Long id : batch)
            {
                if(photoService.removeIfStillPending(id, before))
                {
                    dropped++;
                }
                afterId = id;
            }
        }
        while(batch.size() == batchSize);
        uploadsDropped.increment(dropped);
        log.info("Уборка фото: удалено брошенных загрузок {}, начатых раньше {}", dropped, before);
    }
    @Scheduled(cron = "${cleanup.photo-files.cron:0 0 4 * * *}")
    @SchedulerLock(name = "orphanPhotoFiles", lockAtMostFor = "PT1H", lockAtLeastFor = "PT1M")
    public void deleteOrphanFiles()
    {
        Instant before = Instant.now(clock).minus(orphanAge);
        AtomicInteger deleted = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        storage.forEachPage(PREFIX, page -> {
            List<String> old = page.stream().filter(file -> file.lastModified().isBefore(before)).map(StoredKey::key).toList();
            if(old.isEmpty())
            {
                return;
            }
            Set<String> known = new HashSet<>(photos.findExistingKeys(old));
            List<String> orphans = old.stream().filter(key -> !known.contains(key)).toList();
            if(orphans.isEmpty())
            {
                return;
            }
            int notDeleted = storage.deleteAll(orphans);
            deleted.addAndGet(orphans.size() - notDeleted);
            failed.addAndGet(notDeleted);
        });
        orphansDeleted.increment(deleted.get());
        log.info("Уборка фото: удалено файлов-сирот {}, не удалось {}", deleted.get(), failed.get());
    }
}
