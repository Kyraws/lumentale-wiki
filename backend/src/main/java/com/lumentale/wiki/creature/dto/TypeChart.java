package com.lumentale.wiki.creature.dto;

import java.util.List;

/**
 * A form's two-axis defensive/offensive profile — the capability the redesign
 * unlocks by promoting the per-form {@code Weaknesses[13]} blob to
 * {@code form_weakness} and recovering the global 5×5 {@code emotion_chart}.
 *
 *  - {@code elemental}: how each attacking elemental type fares against this form
 *    (the per-form elemental axis).
 *  - {@code emotionOffense}: this form's emotion attacking each emotion (×mult).
 *  - {@code emotionDefense}: each emotion attacking this form (×mult).
 */
public record TypeChart(
    String emotion,
    List<EleReaction> elemental,
    List<EmotionReaction> emotionOffense,
    List<EmotionReaction> emotionDefense
) {
    /** WEAKNESS | RESISTANCE | NORMAL | IMMUNITY for one attacking elemental type. */
    public record EleReaction(String attacker, String effectiveness) {}

    /** A ×multiplier between this form's emotion and another emotion. */
    public record EmotionReaction(String other, double multiplier) {}
}
