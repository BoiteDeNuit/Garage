package com.example.repository;

import com.example.model.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser,Long> {
    Optional<AppUser> findByUsername(String username);
    // Админ из переменных окружения. Два экземпляра стартуют на пустой базе одновременно: оба не нашли админа,
    // оба вставляют. С on conflict do nothing второй ничего не вставит и не упадёт на UNIQUE при старте
    @Transactional
    @Modifying
    @Query(value = "insert into users (username, password_hash, role) values (:username, :hash, 'ADMIN') on conflict (username) do nothing",
            nativeQuery = true)
    int insertAdminIfAbsent(@Param("username") String username, @Param("hash") String hash);
    // SELECT ... FOR UPDATE: два одновременных решения по одному пользователю идут по очереди,
    // второе читает уже то, что записало первое
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.username = :username")
    Optional<AppUser> findForUpdate(@Param("username") String username);
    boolean existsByUsername(String username);
}
