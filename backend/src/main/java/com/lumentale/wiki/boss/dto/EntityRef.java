package com.lumentale.wiki.boss.dto;

/**
 * A light cross-link to another entity (origin species, the boss's form, …).
 * {@code menuArt}/{@code variant} are omitted when not applicable (null-omit).
 */
public record EntityRef(String guid, String species, String variant, String menuArt) {}
