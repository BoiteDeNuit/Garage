package com.example.dto;

import java.time.Instant;

// hiddenListings — сколько объявлений блокировка убрала с витрины и из очереди
public record UserBanDto(String username, Instant bannedAt, String reason, int hiddenListings) {
}
