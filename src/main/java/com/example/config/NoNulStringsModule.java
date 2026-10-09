package com.example.config;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.jdk.StringDeserializer;
import tools.jackson.databind.module.SimpleModule;

// Нулевой символ Postgres в тексте не принимает: строка с \u0000 доходила до INSERT и давала 500.
// Модуль подменяет чтение всех строк в JSON, Spring Boot регистрирует бины JacksonModule сам.
// Query-параметры проверяет NoNulParametersAdvice
@Component
public class NoNulStringsModule extends SimpleModule {
    public NoNulStringsModule()
    {
        super("no-nul-strings");
        addDeserializer(String.class, new NoNulStringDeserializer());
    }

    static class NoNulStringDeserializer extends StringDeserializer {
        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) throws JacksonException
        {
            String value = super.deserialize(parser, context);
            if(value != null && value.indexOf('\u0000') >= 0)
            {
                // InvalidFormatException с типом String, обработчик 400 отличает его по типу
                return (String) context.handleWeirdStringValue(String.class, value, "нулевой символ");
            }
            return value;
        }
    }
}
