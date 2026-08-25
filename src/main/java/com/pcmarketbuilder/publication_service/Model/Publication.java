package com.pcmarketbuilder.publication_service.Model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad de la tabla "publications" (base de datos db_listings).
 * Ver Modelo_relacion.sql, sección 3.
 */
@Entity
@Table(name = "publications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Publication {

    @Id
    @UuidGenerator
    @Column(name = "publication_id", columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String publicationId;

    // Referencia remota a db_users.users.user_id. No es FK real: cada
    // microservicio tiene su propia base de datos, así que no se puede hacer
    // JOIN cross-database. El id se valida contra el claim del JWT.
    @Column(name = "seller_id", columnDefinition = "CHAR(36)", nullable = false)
    private String sellerId;

    // Referencia remota a db_products.master_products.product_id (mismo motivo).
    @Column(name = "product_id", columnDefinition = "CHAR(36)", nullable = false)
    private String productId;

    @Column(name = "title", length = 150, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Precio en CLP.
    @Column(name = "price", nullable = false)
    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false)
    private Grade grade;

    @Column(name = "usage_time_months")
    private Integer usageTimeMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private PublicationStatus status = PublicationStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "publication", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PublicationImage> images = new ArrayList<>();

    public void addImage(PublicationImage image) {
        images.add(image);
        image.setPublication(this);
    }

    public void removeImage(PublicationImage image) {
        images.remove(image);
        image.setPublication(null);
    }
}
