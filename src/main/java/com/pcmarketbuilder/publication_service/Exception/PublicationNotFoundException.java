package com.pcmarketbuilder.publication_service.Exception;

public class PublicationNotFoundException extends RuntimeException {
    public PublicationNotFoundException(String publicationId) {
        super("No se encontró la publicación con id: " + publicationId);
    }
}
