package com.lumentale.wiki.creature.dto;

/** One stat's S–F grade, bar percentage, and population percentile rank. */
public record StatGrade(String stat, String grade, int pct, Integer rank) {}
