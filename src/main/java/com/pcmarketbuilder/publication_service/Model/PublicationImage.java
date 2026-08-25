package com.pcmarketbuilder.publication_service.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Entidad de la tabla "publication_images" (base de datos db_listings).
 * Ver Modelo_relacion.sql, sección 3.
 */
@Entity
@Table(name = "publication_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicationImage {

    @Id
    @UuidGenerator
    @Column(name = "image_id", columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String imageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publication_id", nullable = false)
    @JsonIgnore
    private Publication publication;

    // URL directa del objeto guardado en Amazon S3.
    @Column(name = "image_url", columnDefinition = "TEXT", nullable = false)
    private String imageUrl;

    @Column(name = "is_primary")
    @Builder.Default
    private Boolean isPrimary = false;
}
