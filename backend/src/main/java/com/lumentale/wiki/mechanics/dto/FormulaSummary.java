package com.lumentale.wiki.mechanics.dto;

/** One recovered formula in the list view. {@code confidence} ∈ verified|structural|partial. */
public record FormulaSummary(String key, String name, String signature, String confidence) {}
