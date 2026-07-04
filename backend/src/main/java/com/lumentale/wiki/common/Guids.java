package com.lumentale.wiki.common;

import com.lumentale.wiki.error.BadRequestException;

import java.util.UUID;

/**
 * Entity GUID handling for the redesigned schema, where entity PKs are native
 * {@code uuid} (not {@code text} as in v2). Path variables arrive as strings; we
 * validate them ONCE at the edge so a malformed guid is a clean 400 rather than a
 * Postgres {@code invalid input syntax for type uuid} surfacing as a 500.
 *
 * Repositories then bind the validated {@link UUID} directly (the JDBC driver
 * maps {@code java.util.UUID} to the {@code uuid} column type natively — no
 * {@code ::uuid} cast needed).
 */
public final class Guids {

    private Guids() {}

    /** Parse a path-variable guid or throw a 400 (NOT a 500) if it isn't a uuid. */
    public static UUID require(String guid) {
        if (guid == null || guid.isBlank()) throw new BadRequestException("missing guid");
        try {
            return UUID.fromString(guid);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("malformed guid: " + guid);
        }
    }
}
