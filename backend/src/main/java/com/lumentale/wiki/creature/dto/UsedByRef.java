package com.lumentale.wiki.creature.dto;

/** A trainer or boss that fields this form. {@code kind} ∈ {trainer, boss}. */
public record UsedByRef(String kind, String guid, String name, Integer level) {}
