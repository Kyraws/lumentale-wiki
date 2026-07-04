package com.lumentale.wiki.type.dto;

import java.util.List;

/**
 * For one attacking elemental type: which defending forms react each way
 * (display names). The redesign's {@code form_weakness.effectiveness} has four
 * relations — WEAKNESS / RESISTANCE / NORMAL / IMMUNITY — so the buckets are
 * weakness / normal / resistance / immunity (v2's REFLECT no longer exists in
 * the elemental axis).
 */
public record TypeCoverage(String type, List<String> weakness, List<String> normal,
                           List<String> resistance, List<String> immunity) {}
