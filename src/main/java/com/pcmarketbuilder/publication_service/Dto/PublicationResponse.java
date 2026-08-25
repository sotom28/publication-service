package com.pcmarketbuilder.publication_service.Dto;

import com.pcmarketbuilder.publication_service.Model.Grade;
import com.pcmarketbuilder.publication_service.Model.Publication;
import com.pcmarketbuilder.publication_service.Model.PublicationStatus;

import java.time.LocalDateTime;
import java.util.List;
// DTO (Data Transfer Object) para representar la respuesta de una publicación.
public record PublicationResponse(
        String publicationId,
        String sellerId,
        String productId,
        String title,
        String description,
        Integer price,
        Grade grade,
        Integer usageTimeMonths,
        PublicationStatus status,
        LocalDateTime createdAt,
        List<PublicationImageResponse> images
) {
        // Método estático para convertir una entidad Publication en un DTO PublicationResponse.
    public static PublicationResponse fromEntity(Publication publication) {
        List<PublicationImageResponse> imageResponses = publication.getImages().stream()
                .map(img -> new PublicationImageResponse(
                        img.getImageId(),
                        img.getImageUrl(),
                        Boolean.TRUE.equals(img.getIsPrimary())))
                .toList();

        return new PublicationResponse(
                publication.getPublicationId(),
                publication.getSellerId(),
                publication.getProductId(),
                publication.getTitle(),
                publication.getDescription(),
                publication.getPrice(),
                publication.getGrade(),
                publication.getUsageTimeMonths(),
                publication.getStatus(),
                publication.getCreatedAt(),
                imageResponses
        );
    }
}
