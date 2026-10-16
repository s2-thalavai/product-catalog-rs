package com.infy.catalog.domain.exception;

/** Base type for expected business failures. The API layer maps each subtype to an error response. */
public abstract class CatalogException extends RuntimeException {

    protected CatalogException(String message) {
        super(message);
    }
}
