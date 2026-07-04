package com.lumentale.wiki.creature;

/**
 * The global 5×5 emotion-effectiveness chart, recovered from the binary and held
 * as tested reference data (the Seeder writes it into {@code emotion_chart}; the
 * creature type chart reads it back from the DB).
 *
 * <h3>Provenance</h3>
 * Logic from {@code native/decompiled/BattleMath__GetEmotionalTypeEffectiveness\
 * Multiplier.c}; the three returned {@code _DAT_} symbols resolve via
 * {@code native/constants.json}:
 * <pre>
 *   _DAT_18229ba4c = 0.8  (resisted)
 *   _DAT_18229b7a0 = 1.0  (neutral)
 *   _DAT_18229bb04 = 1.2  (super-effective)
 * </pre>
 * The decompiled function indexes the engine {@code EmoTypes} enum
 * (SEREUM=1, FELICIS=2, HORRENS=3, FUROR=4, MESTUS=5). This matrix is already
 * <b>translated by name</b> into the DB's forms code system
 * (FELICIS=1, MESTUS=2, FUROR=3, SEREUM=4, HORRENS=5) so it joins
 * {@code form.emotion_code} directly. Row = attacker code−1, col = defender
 * code−1; the diagonal (self vs self) is neutral.
 */
public final class EmotionChartData {

    private EmotionChartData() {}

    /** Multipliers indexed [attackerCode-1][defenderCode-1], forms code system. */
    public static final double[][] FORMS_CODE = {
        //         def: FELICIS MESTUS FUROR SEREUM HORRENS
        /* FELICIS */ { 1.0,    1.2,   0.8,  1.2,   0.8 },
        /* MESTUS  */ { 0.8,    1.0,   1.2,  1.2,   1.0 },
        /* FUROR   */ { 1.2,    1.0,   1.0,  0.8,   1.2 },
        /* SEREUM  */ { 1.2,    0.8,   1.0,  1.0,   1.2 },
        /* HORRENS */ { 1.0,    1.2,   1.2,  0.8,   1.0 },
    };
}
