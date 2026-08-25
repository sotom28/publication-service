package com.pcmarketbuilder.publication_service.Dto;

public record PublicationImageResponse(
        String imageId,
        String imageUrl,
        boolean isPrimary
) {}
