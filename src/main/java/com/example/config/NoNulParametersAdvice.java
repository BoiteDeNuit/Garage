package com.example.config;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

import java.beans.PropertyEditorSupport;

// То же для query-параметров и полей @ParameterObject: ?brand=a%00b падал на SELECT с 500.
// Редактор строк срабатывает и при String -> String, так работает StringTrimmerEditor.
// Отказ становится ошибкой привязки, и GlobalExceptionHandler отвечает 400
@ControllerAdvice
public class NoNulParametersAdvice {
    @InitBinder
    public void rejectNul(WebDataBinder binder)
    {
        binder.registerCustomEditor(String.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text)
            {
                if(text != null && text.indexOf('\u0000') >= 0)
                {
                    throw new IllegalArgumentException("Нулевой символ в параметре");
                }
                setValue(text);
            }
        });
    }
}
