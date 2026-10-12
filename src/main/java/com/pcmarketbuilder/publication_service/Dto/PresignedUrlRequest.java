package com.pcmarketbuilder.publication_service.Dto;

import jakarta.validation.constraints.NotBlank;

public record PresignedUrlRequest(
        @NotBlank(message = "El nombre de archivo es obligatorio")
        String fileName,

        @NotBlank(message = "El Content-Type es obligatorio")
        String contentType
) {}
