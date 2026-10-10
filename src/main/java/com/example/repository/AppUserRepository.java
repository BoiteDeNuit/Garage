package com.example.repository;

import com.example.model.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser,Long> {
    Optional<AppUser> findByUsername(String username);
    // SELECT ... FOR UPDATE: два одновременных решения по одному пользователю идут по очереди,
    // второе читает уже то, что записало первое
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.username = :username")
    Optional<AppUser> findForUpdate(@Param("username") String username);
    boolean existsByUsername(String username);
}
