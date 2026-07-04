package com.lumentale.wiki.mechanics.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * A recovered formula as citable documentation-as-data, with the named constants
 * that feed it ({@code game_constant.formula_key} → this key). {@code raw} carries
 * the structured inputs/outputs the extract preserved.
 */
public record FormulaDetail(
    String key,
    String name,
    String signature,
    String expression,
    String description,
    String confidence,
    String sourceFile,
    List<Constant> constants,
    JsonNode raw
) {}
