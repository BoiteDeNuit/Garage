package com.example.dto;

import java.time.LocalDateTime;

public record ErrorResponse(LocalDateTime timestamp,int status,String error,String message) {
}
