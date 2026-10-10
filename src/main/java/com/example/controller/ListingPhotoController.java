package com.example.controller;

import com.example.dto.PhotoUploadDto;
import com.example.dto.PhotoUploadRequest;
import com.example.security.AppUserPrincipal;
import com.example.service.ListingPhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Фото объявлений", description = "Файл грузится прямо в хранилище по ссылке, приложение его не принимает")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/listings/{id}/photos")
public class ListingPhotoController {
    private final ListingPhotoService photos;
    public ListingPhotoController(ListingPhotoService photos) { this.photos=photos; }
    @Operation(summary = "Ссылка на загрузку фото", description = "Отдаёт presigned PUT на 10 минут. Файл отправить PUT-ом на uploadUrl "
            + "с заголовками из headers, без токена. Тип и размер должны совпасть с заявленными, иначе хранилище ответит 403")
    @ApiResponse(responseCode = "201", description = "Ссылка выдана, фото ждёт загрузки")
    @ApiResponse(responseCode = "400", description = "Тип не JPEG, PNG или WebP, размер больше 10 МБ")
    @ApiResponse(responseCode = "403", description = "Не своё объявление")
    @ApiResponse(responseCode = "404", description = "Объявления нет или оно не видно")
    @ApiResponse(responseCode = "409", description = "Уже 20 фото или объявление продано")
    @PostMapping
    public ResponseEntity<PhotoUploadDto> startUpload(@PathVariable Long id,
                                                      @Valid @RequestBody PhotoUploadRequest request,
                                                      @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal actor)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(photos.startUpload(id, request, actor));
    }
}
