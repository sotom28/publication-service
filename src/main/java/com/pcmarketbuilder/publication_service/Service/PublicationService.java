package com.pcmarketbuilder.publication_service.Service;

import com.pcmarketbuilder.publication_service.Auth.AuthContext;
import com.pcmarketbuilder.publication_service.Dto.PublicationImageRequest;
import com.pcmarketbuilder.publication_service.Dto.PublicationRequest;
import com.pcmarketbuilder.publication_service.Exception.ForbiddenException;
import com.pcmarketbuilder.publication_service.Exception.PublicationNotFoundException;
import com.pcmarketbuilder.publication_service.Model.Grade;
import com.pcmarketbuilder.publication_service.Model.Publication;
import com.pcmarketbuilder.publication_service.Model.PublicationImage;
import com.pcmarketbuilder.publication_service.Model.PublicationStatus;
import com.pcmarketbuilder.publication_service.Repository.PublicationRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class PublicationService {

    private final PublicationRepository publicationRepository;

    /**
     * Estados que el dueño (vendedor) puede fijar mediante el PUT.
     * Cualquier otro (RESERVED, SOLD, IN_INSPECTION) es exclusivo de PATCH /status.
     */
    private static final List<PublicationStatus> OWNER_EDITABLE_STATUSES =
            List.of(PublicationStatus.ACTIVE, PublicationStatus.WITHDRAWN);

    @Transactional
    public Publication create(PublicationRequest request, AuthContext auth) {
        // sellerId sale SIEMPRE de la identidad autenticada, nunca del body:
        // impide publicar "a nombre de otro usuario".
        Publication publication = Publication.builder()
                .sellerId(auth.userId())
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

    /**
     * Búsqueda con filtros combinables y paginación (page/limit humano-indexado, 1-based).
     * Consistente con el patrón de product-service.searchProducts.
     */
    @Transactional(readOnly = true)
    public Page<Publication> search(String sellerId, PublicationStatus status, String productId,
                                    Integer maxPrice, Grade grade, int page, int limit) {
        Pageable pageable = PageRequest.of(page > 0 ? page - 1 : 0, limit > 0 ? limit : 20);

        Specification<Publication> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (sellerId != null && !sellerId.isBlank()) {
                predicates.add(cb.equal(root.get("sellerId"), sellerId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (productId != null && !productId.isBlank()) {
                predicates.add(cb.equal(root.get("productId"), productId));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            if (grade != null) {
                predicates.add(cb.equal(root.get("grade"), grade));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return publicationRepository.findAll(spec, pageable);
    }

    @Transactional
    public Publication update(String publicationId, PublicationRequest request, AuthContext auth) {
        Publication publication = getById(publicationId);
        auth.requireOwner(publication.getSellerId());

        PublicationStatus newStatus = request.status();
        if (newStatus != null && !OWNER_EDITABLE_STATUSES.contains(newStatus)) {
            throw new ForbiddenException(
                    "El estado '" + newStatus + "' no puede fijarse por PUT; usa PATCH /{id}/status");
        }

        publication.setTitle(request.title());
        publication.setDescription(request.description());
        publication.setPrice(request.price());
        publication.setGrade(request.grade());
        publication.setUsageTimeMonths(request.usageTimeMonths());
        if (newStatus != null) {
            // sellerId y productId no se tocan en el update: identifican de forma
            // permanente quién publicó y qué producto del catálogo es.
            publication.setStatus(newStatus);
        }

        return publicationRepository.save(publication);
    }

    @Transactional
    public Publication updateStatus(String publicationId, PublicationStatus newStatus, AuthContext auth) {
        // Las transiciones del ciclo de vida (RESERVED/SOLD/IN_INSPECTION) solo las
        // puede aplicar un rol administrativo del taller, no un comprador/vendedor.
        auth.requireRole(AuthContext.ROLE_WORKSHOP_ADMIN);

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
    public Publication addImage(String publicationId, PublicationImageRequest request, AuthContext auth) {
        Publication publication = getById(publicationId);
        auth.requireOwner(publication.getSellerId());

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
    public Publication removeImage(String publicationId, String imageId, AuthContext auth) {
        Publication publication = getById(publicationId);
        auth.requireOwner(publication.getSellerId());

        PublicationImage image = publication.getImages().stream()
                .filter(img -> img.getImageId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No se encontró la imagen con id: " + imageId));

        publication.removeImage(image);
        return publicationRepository.save(publication);
    }

    @Transactional
    public Publication setPrimaryImage(String publicationId, String imageId, AuthContext auth) {
        Publication publication = getById(publicationId);
        auth.requireOwner(publication.getSellerId());

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
