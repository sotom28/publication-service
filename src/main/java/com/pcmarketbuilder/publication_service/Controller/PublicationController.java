package com.pcmarketbuilder.publication_service.Controller;

import com.pcmarketbuilder.publication_service.Dto.PublicationImageRequest;
import com.pcmarketbuilder.publication_service.Dto.PublicationRequest;
import com.pcmarketbuilder.publication_service.Dto.PublicationResponse;
import com.pcmarketbuilder.publication_service.Dto.UpdateStatusRequest;
import com.pcmarketbuilder.publication_service.Model.PublicationStatus;
import com.pcmarketbuilder.publication_service.Service.PublicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/publications")
@RequiredArgsConstructor
public class PublicationController {

    private final PublicationService publicationService;

    @PostMapping
    public ResponseEntity<PublicationResponse> create(@Valid @RequestBody PublicationRequest request) {
        var created = publicationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(PublicationResponse.fromEntity(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicationResponse> getById(@PathVariable("id") String id) {
        return ResponseEntity.ok(PublicationResponse.fromEntity(publicationService.getById(id)));
    }

    @GetMapping("/")
    public ResponseEntity<List<PublicationResponse>> getAll(
            @RequestParam(required = false) String sellerId,
            @RequestParam(required = false) PublicationStatus status) {

        List<PublicationResponse> results;
        if (sellerId != null) {
            results = publicationService.getBySeller(sellerId).stream()
                    .map(PublicationResponse::fromEntity).toList();
        } else if (status != null) {
            results = publicationService.getByStatus(status).stream()
                    .map(PublicationResponse::fromEntity).toList();
        } else {
            results = publicationService.getAll().stream()
                    .map(PublicationResponse::fromEntity).toList();
        }
        return ResponseEntity.ok(results);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PublicationResponse> update(@PathVariable("id") String id,
                                                        @Valid @RequestBody PublicationRequest request) {
        return ResponseEntity.ok(PublicationResponse.fromEntity(publicationService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PublicationResponse> updateStatus(@PathVariable("id") String id,
                                                              @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(
                PublicationResponse.fromEntity(publicationService.updateStatus(id, request.status())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        publicationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/images")
    public ResponseEntity<PublicationResponse> addImage(@PathVariable("id") String id,
                                                          @Valid @RequestBody PublicationImageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PublicationResponse.fromEntity(publicationService.addImage(id, request)));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ResponseEntity<PublicationResponse> removeImage(@PathVariable("id") String id,
                                                             @PathVariable("imageId") String imageId) {
        return ResponseEntity.ok(PublicationResponse.fromEntity(publicationService.removeImage(id, imageId)));
    }

    @PatchMapping("/{id}/images/{imageId}/primary")
    public ResponseEntity<PublicationResponse> setPrimaryImage(@PathVariable("id") String id,
                                                                 @PathVariable("imageId") String imageId) {
        return ResponseEntity.ok(PublicationResponse.fromEntity(publicationService.setPrimaryImage(id, imageId)));
    }
}
