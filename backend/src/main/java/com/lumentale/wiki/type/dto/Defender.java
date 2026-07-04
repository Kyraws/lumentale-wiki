package com.lumentale.wiki.type.dto;

/**
 * A form's defensive standing across the elemental axis, scored
 * {@code 2·immune + resist − 2·weak} (the redesign has no REFLECT relation), with
 * the breakdown and the bulk stats used for tie-breaks.
 */
public record Defender(String guid, String species, String variant, String emo, String ele,
                       int score, int weak, int resist, int immune, int def, int spd) {}
