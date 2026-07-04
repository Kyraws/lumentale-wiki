package com.lumentale.wiki.boss.dto;

/** A move in a boss's kit ({@code boss_skill} → {@code move}). */
public record BossSkill(String moveGuid, String moveName, String type, Integer level, Integer ord) {}
