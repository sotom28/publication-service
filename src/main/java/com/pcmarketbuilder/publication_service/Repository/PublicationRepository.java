package com.pcmarketbuilder.publication_service.Repository;

import com.pcmarketbuilder.publication_service.Model.Publication;
import com.pcmarketbuilder.publication_service.Model.PublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublicationRepository extends JpaRepository<Publication, String> {

    List<Publication> findBySellerId(String sellerId);

    List<Publication> findByProductId(String productId);

    List<Publication> findByStatus(PublicationStatus status);

    List<Publication> findBySellerIdAndStatus(String sellerId, PublicationStatus status);
}
