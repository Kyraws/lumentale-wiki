package com.lumentale.wiki.error;

/** Uniform JSON error body for every non-2xx API response. */
public record ApiError(int status, String error, String message, String path) {}
