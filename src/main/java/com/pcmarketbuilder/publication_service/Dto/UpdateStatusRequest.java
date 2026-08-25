package com.pcmarketbuilder.publication_service.Dto;

import com.pcmarketbuilder.publication_service.Model.PublicationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull PublicationStatus status
) {}
