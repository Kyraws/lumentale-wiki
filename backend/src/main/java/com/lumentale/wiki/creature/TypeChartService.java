package com.lumentale.wiki.creature;

import com.lumentale.wiki.creature.dto.TypeChart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure assembly of a form's {@link TypeChart} from the two effectiveness axes —
 * no database dependency, so the cross-product logic is unit-tested against
 * synthetic charts ({@code TypeChartServiceTest}).
 *
 * The two axes live in different shapes in the binary and so in the schema:
 *   - <b>elemental</b> is per-form ({@code form_weakness}), already a list of
 *     (attacking type → effectiveness) for THIS defender; we just order it.
 *   - <b>emotion</b> is a global 5×5 ({@code emotion_chart}); for a form with a
 *     given emotion we slice the row (offense) and the column (defense).
 */
public final class TypeChartService {

    private TypeChartService() {}

    /** One cell of the global emotion chart. */
    public record EmotionCell(String attacker, String defender, double multiplier) {}

    /**
     * @param emotion        this form's emotion label, or null (emotionless → ×1.0)
     * @param elemental      ordered (attacker label → effectiveness) for this form
     * @param emotionChart   the full global emotion chart cells
     */
    public static TypeChart build(String emotion,
                                  List<TypeChart.EleReaction> elemental,
                                  List<EmotionCell> emotionChart) {
        List<TypeChart.EmotionReaction> offense = new ArrayList<>();
        List<TypeChart.EmotionReaction> defense = new ArrayList<>();
        if (emotion != null) {
            for (EmotionCell c : emotionChart) {
                if (c.attacker().equals(emotion))
                    offense.add(new TypeChart.EmotionReaction(c.defender(), c.multiplier()));
                if (c.defender().equals(emotion))
                    defense.add(new TypeChart.EmotionReaction(c.attacker(), c.multiplier()));
            }
        }
        return new TypeChart(emotion, List.copyOf(elemental), offense, defense);
    }

    /**
     * Translate raw {@code form_weakness} rows (attacker int code → effectiveness)
     * into ordered, labelled {@link TypeChart.EleReaction}s, dropping NONE (code 0)
     * and anything that resolves to NORMAL with no label.
     */
    public static List<TypeChart.EleReaction> elementalFrom(Map<Integer,String> attackerEffectiveness,
                                                            Map<Integer,String> eleLabels) {
        List<TypeChart.EleReaction> out = new ArrayList<>();
        eleLabels.entrySet().stream()
            .filter(e -> e.getKey() != 0)
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> {
                String eff = attackerEffectiveness.get(e.getKey());
                if (eff != null) out.add(new TypeChart.EleReaction(e.getValue(), eff));
            });
        return out;
    }
}
