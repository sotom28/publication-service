package com.pcmarketbuilder.publication_service.Repository;

import com.pcmarketbuilder.publication_service.Model.PublicationImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublicationImageRepository extends JpaRepository<PublicationImage, String> {

    List<PublicationImage> findByPublication_PublicationId(String publicationId);
}
