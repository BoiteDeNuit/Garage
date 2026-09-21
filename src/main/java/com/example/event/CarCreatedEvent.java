package com.example.event;
import java.time.Instant;

public record CarCreatedEvent(Long id, String brand, String model, Instant createdAt) {}
