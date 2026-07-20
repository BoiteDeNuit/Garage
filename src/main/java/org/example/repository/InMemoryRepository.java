package org.example.repository;

import org.example.exception.StorageFullException;
import org.example.model.Identifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryRepository<T extends Identifiable> implements CrudRepository<T> {
    private final Map<Long,T> storage = new ConcurrentHashMap<>();
    private final Class<T> type;
    private final int maxSize;
    private AtomicLong lastId = new AtomicLong();
    public InMemoryRepository(Class<T> type,int maxSize)
    {
        this.type = type;
        this.maxSize = maxSize;
    }
    @Override
    public T save(T entity) throws StorageFullException
    {
        if(storage.size() >= maxSize)
        {
            throw new StorageFullException("Хранилище " + type.getSimpleName() + " переполнено");
        }
        if(entity.getId() == null)
        {
            entity.setId(lastId.incrementAndGet());
        }
        storage.put(entity.getId(), entity);
        return entity;
    }
    @Override
    public List<T> findAll()
    {
        return new ArrayList<>(storage.values());
    }
    @Override
    public boolean deleteById(Long id)
    {
        return storage.remove(id) != null;
    }
    @Override
    public Optional<T> findById(Long id) { return Optional.ofNullable(storage.get(id));}
    public Long getLastId()
    {
        return lastId.get();
    }
}
