package com.pcmarketbuilder.publication_service.Dto;

import com.pcmarketbuilder.publication_service.Model.Grade;
import com.pcmarketbuilder.publication_service.Model.PublicationStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body para crear o actualizar una publicación.
 *
 * - productId llega del front (referencia al catálogo en db_products); este
 *   servicio no lo valida contra las otras bases, solo lo guarda como
 *   referencia remota.
 * - sellerId NO se recibe por el body: siempre sale del header de identidad
 *   (X-User-Id), ver AuthContext. Evita publicar "a nombre de otro".
 * - status es opcional y solo para el PUT: el service lo restringe a
 *   ACTIVE / WITHDRAWN (transiciones del ciclo de vida van por PATCH /status).
 */
public record PublicationRequest(
        @NotBlank String productId,
        @NotBlank @Size(max = 150) String title,
        String description,
        @NotNull @Min(0) Integer price,
        @NotNull Grade grade,
        @Min(0) Integer usageTimeMonths,
        PublicationStatus status
) {}
