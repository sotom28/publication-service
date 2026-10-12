package com.pcmarketbuilder.publication_service.Dto;

public record PresignedUrlResponse(
        String uploadUrl,
        String fileUrl,
        String objectKey
) {}
