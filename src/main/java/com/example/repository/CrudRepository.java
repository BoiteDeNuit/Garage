package com.example.repository;

import com.example.exception.StorageFullException;
import com.example.model.Identifiable;

import java.util.List;
import java.util.Optional;

public interface CrudRepository<T extends Identifiable>  {
    T save(T entity) throws StorageFullException;
    Optional<T> findById(Long id);
    List<T> findAll();
    boolean deleteById(Long id);
    Long getLastId();

}
