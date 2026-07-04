package com.lumentale.wiki.type.dto;

/** Offense leaderboard row: how many forms an attacking elemental type hits each way. */
public record Offense(String type, int superEffective, int neutral, int resisted, int immune) {}
