package com.pcmarketbuilder.publication_service.Dto;

import com.pcmarketbuilder.publication_service.Model.Grade;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body para crear o actualizar una publicación.
 * sellerId y productId llegan del front (o se resuelven desde el JWT / el
 * catálogo respectivamente); este servicio no los valida contra las otras
 * bases de datos, solo los guarda como referencia remota.
 */
public record PublicationRequest(
        @NotBlank String sellerId,
        @NotBlank String productId,
        @NotBlank @Size(max = 150) String title,
        String description,
        @NotNull @Min(0) Integer price,
        @NotNull Grade grade,
        @Min(0) Integer usageTimeMonths
) {}
