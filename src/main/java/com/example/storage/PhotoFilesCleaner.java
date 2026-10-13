package com.example.storage;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Файлы удаляются после коммита, а не до: откатилась транзакция — файл на месте, строка тоже.
// Наоборот было бы хуже: файл удалён, а строка осталась, и в списке битая ссылка.
// Не удалилось из хранилища — файл остаётся без строки. Его никто не видит, он только занимает место:
// такие считает photos_files_delete_failed_total, убирать их — дело уборки по расписанию
@Component
public class PhotoFilesCleaner {
    private static final Logger log = LoggerFactory.getLogger(PhotoFilesCleaner.class);
    private final PhotoStorage storage;
    private final Counter failed;
    public PhotoFilesCleaner(PhotoStorage storage, MeterRegistry meterRegistry)
    {
        this.storage=storage;
        this.failed=Counter.builder("photos.files.delete.failed")
                .description("Файлов фото, которые не удалось удалить из хранилища")
                .register(meterRegistry);
    }
    // Транзакция уже закоммичена: исключение отсюда ничего не откатит, поэтому ловим и считаем
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPhotoFilesRemoved(PhotoFilesRemoved event)
    {
        if(event.objectKeys().isEmpty())
        {
            return;
        }
        try
        {
            int notDeleted = storage.deleteAll(event.objectKeys());
            if(notDeleted > 0)
            {
                failed.increment(notDeleted);
                log.warn("Не удалено из хранилища файлов фото: {} из {}", notDeleted, event.objectKeys().size());
            }
        }
        catch (RuntimeException e)
        {
            failed.increment(event.objectKeys().size());
            log.warn("Файлы фото не удалены из хранилища: {}", e.getMessage());
        }
    }
}
