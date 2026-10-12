package com.pcmarketbuilder.publication_service.Service;

import com.pcmarketbuilder.publication_service.Dto.PresignedUrlRequest;
import com.pcmarketbuilder.publication_service.Dto.PresignedUrlResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name:pcb-publication-images-614788327678}")
    private String bucketName;

    @Value("${aws.region:us-east-1}")
    private String region;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/avif"
    );

    public PresignedUrlResponse createPresignedUploadUrl(String publicationId, PresignedUrlRequest request) {
        String contentType = request.contentType().trim().toLowerCase();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Tipo de archivo no permitido: " + request.contentType() +
                    ". Formatos admitidos: JPEG, PNG, WEBP, GIF, AVIF.");
        }

        String rawFileName = request.fileName().trim();
        String sanitizedFileName = rawFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String objectKey = String.format("publications/%s/%s-%s", publicationId, UUID.randomUUID(), sanitizedFileName);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        String uploadUrl = presigned.url().toString();
        String fileUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, objectKey);

        log.info("Generada presigned URL para publicación {}: {}", publicationId, objectKey);

        return new PresignedUrlResponse(uploadUrl, fileUrl, objectKey);
    }

    public void deleteObjectByUrl(String imageUrl) {
        try {
            if (imageUrl != null && imageUrl.contains(bucketName)) {
                // Formato: https://bucket.s3.region.amazonaws.com/publications/xxx/uuid-name
                String prefix = String.format("https://%s.s3.%s.amazonaws.com/", bucketName, region);
                if (imageUrl.startsWith(prefix)) {
                    String objectKey = imageUrl.substring(prefix.length());
                    s3Client.deleteObject(DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectKey)
                            .build());
                    log.info("Eliminado objeto de S3: {}", objectKey);
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo eliminar el objeto de S3 para url {}: {}", imageUrl, e.getMessage());
        }
    }
}
