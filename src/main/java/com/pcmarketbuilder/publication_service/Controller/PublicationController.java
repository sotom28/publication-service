package com.pcmarketbuilder.publication_service.Controller;

import com.pcmarketbuilder.publication_service.Auth.AuthContext;
import com.pcmarketbuilder.publication_service.Dto.PublicationImageRequest;
import com.pcmarketbuilder.publication_service.Dto.PublicationRequest;
import com.pcmarketbuilder.publication_service.Dto.PublicationResponse;
import com.pcmarketbuilder.publication_service.Dto.UpdateStatusRequest;
import com.pcmarketbuilder.publication_service.Model.Grade;
import com.pcmarketbuilder.publication_service.Model.PublicationStatus;
import com.pcmarketbuilder.publication_service.Service.PublicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/publications")
@RequiredArgsConstructor
public class PublicationController {

    private final PublicationService publicationService;

    @PostMapping
    public ResponseEntity<PublicationResponse> create(@RequestHeader(value = AuthContext.USER_ID_HEADER, required = false) String userId,
                                                      @RequestHeader(value = AuthContext.ROLE_HEADER, required = false) String role,
                                                      @Valid @RequestBody PublicationRequest request) {
        var created = publicationService.create(request, new AuthContext(userId, role));
        return ResponseEntity.status(HttpStatus.CREATED).body(PublicationResponse.fromEntity(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicationResponse> getById(@PathVariable("id") String id) {
        return ResponseEntity.ok(PublicationResponse.fromEntity(publicationService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<Page<PublicationResponse>> search(
            @RequestParam(required = false) String sellerId,
            @RequestParam(required = false) PublicationStatus status,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false) Grade grade,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        Page<PublicationResponse> results = publicationService
                .search(sellerId, status, productId, maxPrice, grade, page, limit)
                .map(PublicationResponse::fromEntity);
        return ResponseEntity.ok(results);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PublicationResponse> update(@PathVariable("id") String id,
                                                      @RequestHeader(value = AuthContext.USER_ID_HEADER, required = false) String userId,
                                                      @RequestHeader(value = AuthContext.ROLE_HEADER, required = false) String role,
                                                      @Valid @RequestBody PublicationRequest request) {
        return ResponseEntity.ok(
                PublicationResponse.fromEntity(publicationService.update(id, request, new AuthContext(userId, role))));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PublicationResponse> updateStatus(@PathVariable("id") String id,
                                                            @RequestHeader(value = AuthContext.USER_ID_HEADER, required = false) String userId,
                                                            @RequestHeader(value = AuthContext.ROLE_HEADER, required = false) String role,
                                                            @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(PublicationResponse.fromEntity(
                publicationService.updateStatus(id, request.status(), new AuthContext(userId, role))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        publicationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/images")
    public ResponseEntity<PublicationResponse> addImage(@PathVariable("id") String id,
                                                        @RequestHeader(value = AuthContext.USER_ID_HEADER, required = false) String userId,
                                                        @RequestHeader(value = AuthContext.ROLE_HEADER, required = false) String role,
                                                        @Valid @RequestBody PublicationImageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PublicationResponse.fromEntity(publicationService.addImage(id, request, new AuthContext(userId, role))));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ResponseEntity<PublicationResponse> removeImage(@PathVariable("id") String id,
                                                           @PathVariable("imageId") String imageId,
                                                           @RequestHeader(value = AuthContext.USER_ID_HEADER, required = false) String userId,
                                                           @RequestHeader(value = AuthContext.ROLE_HEADER, required = false) String role) {
        return ResponseEntity.ok(PublicationResponse.fromEntity(
                publicationService.removeImage(id, imageId, new AuthContext(userId, role))));
    }

    @PatchMapping("/{id}/images/{imageId}/primary")
    public ResponseEntity<PublicationResponse> setPrimaryImage(@PathVariable("id") String id,
                                                               @PathVariable("imageId") String imageId,
                                                               @RequestHeader(value = AuthContext.USER_ID_HEADER, required = false) String userId,
                                                               @RequestHeader(value = AuthContext.ROLE_HEADER, required = false) String role) {
        return ResponseEntity.ok(PublicationResponse.fromEntity(
                publicationService.setPrimaryImage(id, imageId, new AuthContext(userId, role))));
    }
}
