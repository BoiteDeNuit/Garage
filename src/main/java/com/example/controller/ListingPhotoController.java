package com.example.controller;

import com.example.dto.PhotoDto;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    @Operation(summary = "Подтвердить загрузку", description = "Проверяет файл в хранилище: размер и первые байты должны совпасть с заявленным типом. "
            + "После этого фото видно в списке. Не тот формат — файл удаляется, загружать заново")
    @ApiResponse(responseCode = "200", description = "Фото готово, повтор ничего не меняет")
    @ApiResponse(responseCode = "400", description = "Содержимое не совпало с типом, фото удалено")
    @ApiResponse(responseCode = "404", description = "Нет объявления или такого фото у него")
    @ApiResponse(responseCode = "409", description = "Файл ещё не загружен или объявление продано")
    @PostMapping("/{photoId}/confirm")
    public PhotoDto confirm(@PathVariable Long id,
                            @PathVariable Long photoId,
                            @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal actor)
    {
        return photos.confirm(id, photoId, actor);
    }
    @Operation(summary = "Фото объявления", description = "Только подтверждённые, по порядку. Ссылки на чтение временные, на час. "
            + "Фото черновика и архива видят только продавец и админ")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Объявления нет или оно не видно")
    @GetMapping
    public List<PhotoDto> list(@PathVariable Long id,
                               @Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal viewer)
    {
        return photos.readyPhotos(id, viewer);
    }
}
