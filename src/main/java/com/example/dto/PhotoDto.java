package com.example.dto;

// Ссылка временная (storage.download-ttl): сохранять её у себя бессмысленно, за свежей — снова в список фото
public record PhotoDto(Long id, int position, String url) {
}
