package com.pcmarketbuilder.publication_service.Model;

/**
 * Estado del ciclo de vida de una publicación.
 *
 * En la BD la columna "status" es VARCHAR(20) a propósito (no ENUM nativo),
 * porque el set de valores sigue en evolución (ver comentario en
 * Modelo_relacion.sql sobre el "soft-hold" pendiente). Este enum de Java
 * cumple el rol de validación: agregar un valor nuevo solo requiere tocar
 * este archivo y desplegar, sin necesidad de un ALTER TABLE.
 */
public enum PublicationStatus {
    ACTIVE,
    RESERVED,
    SOLD,
    IN_INSPECTION
}
