package com.example.dto;

import java.util.List;

// Итог обмена: кому выписать новый access-токен и какой refresh положить в куку. Роли и версия — из базы на момент обмена
public record RotatedSession(String username, List<String> roles, int tokenVersion, IssuedRefreshToken refresh) {
}
