package com.example.repository;

import com.example.model.SavedSearch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {
    List<SavedSearch> findByUserIdOrderByCreatedAtDesc(Long userId);
    // Поиск ищется вместе с владельцем: чужой id найдётся как «нет такого», а не «не твоё»
    Optional<SavedSearch> findByIdAndUserId(Long id, Long userId);
    long countByUserId(Long userId);
}
