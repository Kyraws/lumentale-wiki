package com.lumentale.wiki.quest;

import com.lumentale.wiki.quest.QuestService.ObjectiveResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pure mapping tests for quest-objective localization. Objectives resolve through the
 * {@code QUEST_OBJECTIVES} loc table by two independent routes: the {@code objectives_key}
 * code ({@code byKey}) and the Italian {@code mission_label_raw} source text
 * ({@code byItalian}). Key wins when present; the source-text bridge fills the gap;
 * the raw Italian is the honest fallback.
 */
class QuestObjectiveResolverTest {

    private final ObjectiveResolver r = new ObjectiveResolver(
        Map.of("quest_state_AL_MAIN_1_[AL_EnteredCity]", "Explore Arsilia"),
        Map.of("Segui Nipo", "Follow Nipo", "Esplora Arsilia", "Explore Arsilia"));

    @Test
    void keyMatchWins() {
        assertEquals("Explore Arsilia", r.resolve("quest_state_AL_MAIN_1_[AL_EnteredCity]", "Esplora Arsilia"));
    }

    @Test
    void sourceTextFallbackWhenKeyMisses() {
        // objectives_key not in the loc table → falls back to the Italian source-text map.
        assertEquals("Follow Nipo", r.resolve("quest_state_unmapped_[X]", "Segui Nipo"));
        assertEquals("Follow Nipo", r.resolve(null, "Segui Nipo"));
    }

    @Test
    void rawItalianWhenNoEnglishSource() {
        assertEquals("Frase senza traduzione", r.resolve("quest_state_unmapped_[X]", "Frase senza traduzione"));
    }

    @Test
    void resolveTextUsesSourceMapOnly() {
        assertEquals("Follow Nipo", r.resolveText("Segui Nipo"));
        assertEquals("Niente", r.resolveText("Niente"));
        assertNull(r.resolveText(null));
    }
}
