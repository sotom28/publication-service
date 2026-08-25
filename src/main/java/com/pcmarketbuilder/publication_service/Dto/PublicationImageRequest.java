package com.pcmarketbuilder.publication_service.Dto;

import jakarta.validation.constraints.NotBlank;

public record PublicationImageRequest(
        @NotBlank String imageUrl,
        boolean isPrimary
) {}
