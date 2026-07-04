package com.lumentale.wiki.achievement.dto;

/**
 * One row of the achievements list. {@code rarity} is the resolved label when the
 * {@code achievement_rarity} lookup is populated; {@code rarityCode} is the raw int
 * code, always present so the axis is usable even when the lookup is unseeded.
 * {@code icon} is the hybrid-resolved URL (null-omitted when neither leg finds art).
 */
public record AchievementSummary(String guid, String name, String rarity, Integer rarityCode,
                                 Integer steps, String icon) {}
