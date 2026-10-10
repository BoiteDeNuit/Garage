package com.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Включена ли проверка перед публикацией. Отдельным бином, а не полем сервиса: в тестах его подменяют,
// не поднимая второй контекст Spring с другим свойством
@Component
public class ModerationSettings {
    private final boolean required;
    public ModerationSettings(@Value("${moderation.required:false}") boolean required)
    {
        this.required=required;
    }
    public boolean required()
    {
        return required;
    }
}
