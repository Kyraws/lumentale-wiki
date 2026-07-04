package com.lumentale.wiki.squadron.dto;

/**
 * One row of the squadrons list. {@code name} is COALESCE(name_raw, display_name,
 * internal_name); {@code memberCount} is the count of {@code squadron_member}
 * rows; {@code logo} is the hybrid-resolved URL from the squadron's Addressables
 * logo guid (null-omitted when unresolved).
 */
public record SquadronSummary(String guid, String name, Integer rank, String rankLabel,
                              long memberCount, String logo) {}
