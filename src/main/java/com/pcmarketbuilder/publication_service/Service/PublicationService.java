package com.pcmarketbuilder.publication_service.Service;

import com.pcmarketbuilder.publication_service.Dto.PublicationImageRequest;
import com.pcmarketbuilder.publication_service.Dto.PublicationRequest;
import com.pcmarketbuilder.publication_service.Exception.PublicationNotFoundException;
import com.pcmarketbuilder.publication_service.Model.Publication;
import com.pcmarketbuilder.publication_service.Model.PublicationImage;
import com.pcmarketbuilder.publication_service.Model.PublicationStatus;
import com.pcmarketbuilder.publication_service.Repository.PublicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class PublicationService {

    private final PublicationRepository publicationRepository;

    @Transactional
    public Publication create(PublicationRequest request) {
        Publication publication = Publication.builder()
                .sellerId(request.sellerId())
                .productId(request.productId())
                .title(request.title())
                .description(request.description())
                .price(request.price())
                .grade(request.grade())
                .usageTimeMonths(request.usageTimeMonths())
                .status(PublicationStatus.ACTIVE)
                .build();

        return publicationRepository.save(publication);
    }

    @Transactional(readOnly = true)
    public Publication getById(String publicationId) {
        return publicationRepository.findById(publicationId)
                .orElseThrow(() -> new PublicationNotFoundException(publicationId));
    }

    @Transactional(readOnly = true)
    public List<Publication> getAll() {
        return publicationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Publication> getBySeller(String sellerId) {
        return publicationRepository.findBySellerId(sellerId);
    }

    @Transactional(readOnly = true)
    public List<Publication> getByStatus(PublicationStatus status) {
        return publicationRepository.findByStatus(status);
    }

    @Transactional
    public Publication update(String publicationId, PublicationRequest request) {
        Publication publication = getById(publicationId);

        publication.setTitle(request.title());
        publication.setDescription(request.description());
        publication.setPrice(request.price());
        publication.setGrade(request.grade());
        publication.setUsageTimeMonths(request.usageTimeMonths());
        // sellerId y productId no se tocan en el update: identifican de forma
        // permanente quién publicó y qué producto del catálogo es.

        return publicationRepository.save(publication);
    }

    @Transactional
    public Publication updateStatus(String publicationId, PublicationStatus newStatus) {
        Publication publication = getById(publicationId);
        publication.setStatus(newStatus);
        return publicationRepository.save(publication);
    }

    @Transactional
    public void delete(String publicationId) {
        Publication publication = getById(publicationId);
        publicationRepository.delete(publication);
    }

    @Transactional
    public Publication addImage(String publicationId, PublicationImageRequest request) {
        Publication publication = getById(publicationId);

        if (request.isPrimary()) {
            publication.getImages().forEach(img -> img.setIsPrimary(false));
        }

        PublicationImage image = PublicationImage.builder()
                .imageUrl(request.imageUrl())
                .isPrimary(request.isPrimary())
                .build();

        publication.addImage(image);
        return publicationRepository.save(publication);
    }

    @Transactional
    public Publication removeImage(String publicationId, String imageId) {
        Publication publication = getById(publicationId);

        PublicationImage image = publication.getImages().stream()
                .filter(img -> img.getImageId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No se encontró la imagen con id: " + imageId));

        publication.removeImage(image);
        return publicationRepository.save(publication);
    }

    @Transactional
    public Publication setPrimaryImage(String publicationId, String imageId) {
        Publication publication = getById(publicationId);

        boolean found = false;
        for (PublicationImage img : publication.getImages()) {
            boolean isTarget = img.getImageId().equals(imageId);
            img.setIsPrimary(isTarget);
            found = found || isTarget;
        }

        if (!found) {
            throw new NoSuchElementException("No se encontró la imagen con id: " + imageId);
        }

        return publicationRepository.save(publication);
    }
}
