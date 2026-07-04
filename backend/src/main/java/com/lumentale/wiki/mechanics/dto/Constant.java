package com.lumentale.wiki.mechanics.dto;

/**
 * A named tuning constant ({@code game_constant}). {@code va} (the virtual
 * address provenance) is intentionally not surfaced — it's an extraction detail.
 */
public record Constant(String name, Double value, String kind, String formulaKey, String description) {}
