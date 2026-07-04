package com.lumentale.wiki.error;

/** Thrown when a request parameter is malformed (e.g. a non-uuid guid) → HTTP 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
