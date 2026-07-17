package org.example;

import java.util.*;

public class InMemoryRepository<T extends Identifiable> implements CrudRepository<T> {
    private final Map<Long,T> storage = new HashMap<>();
    private final Class<T> type;
    private final int maxSize;
    private long lastId;
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
            lastId++;
            entity.setId(lastId);
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
}
