package com.lumentale.wiki.map.dto;

/**
 * One row of the maps list, with a spawn count for the card. {@code name} is the
 * internal codename; {@code displayName} is the friendly LOCATION name resolved from
 * loc key {@code mapname_<guid>} (null when none — 85/300 maps have one). {@code mapName}
 * and {@code region} are null-omitted when blank; {@code tile} is the hybrid-resolved
 * UI-map prefab art URL (null when no tile guid is set).
 */
public record MapSummary(String guid, String name, String displayName, String mapName,
                         String region, boolean interior, int spawns, String tile) {}
