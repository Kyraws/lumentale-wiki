package com.lumentale.wiki.error;

/** Thrown when a requested entity (by guid/id) does not exist → HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String kind, String id) {
        super(kind + " not found: " + id);
    }
}
