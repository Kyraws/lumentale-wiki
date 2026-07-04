package com.lumentale.wiki.creature.dto;

import java.util.List;

/**
 * One dex-grid row (one non-boss form). camelCase; null fields are omitted by the
 * serializer. {@code ele}/{@code emo} are resolved labels (the schema stores int
 * codes); {@code menuArt}/{@code front} come from the hybrid asset resolver.
 */
public record CreatureSummary(
    String guid,
    String species,
    String variant,
    Integer dex,
    String emo,
    String ele,
    int variants,
    boolean hasLost,
    String menuArt,
    String front,
    List<String> regions
) {}
