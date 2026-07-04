package com.lumentale.wiki.mechanics.dto;

/**
 * A difficulty damage multiplier ({@code difficulty_scalar}). {@code direction} ∈
 * player_out|enemy_out; Normal difficulty has no row (×1.0).
 */
public record DifficultyScalar(String difficulty, String direction, double multiplier) {}
