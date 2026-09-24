package com.example.security;

import com.example.model.AppUser;
import com.example.model.Role;
import com.example.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);
    private final AppUserRepository repository;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;
    public AdminInitializer(AppUserRepository repository,
                            PasswordEncoder encoder,
                            @Value("${admin.username}") String username,
                            @Value("${admin.password}") String password)
    {
        this.repository=repository;
        this.encoder=encoder;
        this.username=username;
        this.password=password;
    }
    @Override
    public void run(ApplicationArguments args)
    {
        if (username.isBlank() || password.isBlank())
        {
            throw new IllegalStateException("Не заданы ADMIN_USERNAME или ADMIN_PASSWORD");
        }
        repository.findByUsername(username).ifPresentOrElse(admin -> {
            if (!encoder.matches(password, admin.getPasswordHash()))
            {
                admin.changePassword(encoder.encode(password));
                repository.save(admin);
                log.info("Пароль администратора {} обновлён", username);
            }
        }, () -> {
            repository.save(new AppUser(username, encoder.encode(password), Role.ADMIN));
            log.info("Создан администратор {}", username);
        });
    }
}
