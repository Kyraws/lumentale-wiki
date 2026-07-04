package com.lumentale.wiki.card.dto;

/**
 * One row of the card-pools list. {@code name} is the natural key (the source
 * guid is empty for every pool); {@code entries} is the live count of
 * {@code card_pool_entry} rows for the pool.
 */
public record CardPoolSummary(String name, Boolean kickstarter, Integer cardCount, Long entries) {}
