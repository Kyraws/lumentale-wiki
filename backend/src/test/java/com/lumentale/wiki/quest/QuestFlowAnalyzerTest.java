package com.lumentale.wiki.quest;

import com.lumentale.wiki.quest.QuestFlowAnalyzer.Flow;
import com.lumentale.wiki.quest.QuestFlowAnalyzer.StateNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure tests for the quest start/completion recovery (no DB). Pins the topology +
 * state-id-ordinal start/end pick, the step-number parser, the flag-prefix
 * convention, and the completion-flag ranking — the load-bearing parsing the
 * QuestDetail "Starts / Completes" sections depend on.
 */
class QuestFlowAnalyzerTest {

    private static StateNode n(long id, String stateId) { return new StateNode(id, stateId, stateId); }

    private static Set<String> ids(List<StateNode> ns) {
        return ns.stream().map(StateNode::stateId).collect(Collectors.toSet());
    }

    @Test
    void linearChain_startIsFirst_endIsLast() {
        // 1 → 2 → 3
        List<StateNode> states = List.of(n(1, "AL_MAIN_1"), n(2, "AL_MAIN_2"), n(3, "AL_MAIN_3"));
        Map<Long, Integer> in = Map.of(2L, 1, 3L, 1);    // 1 has no incoming
        Map<Long, Integer> out = Map.of(1L, 1, 2L, 1);   // 3 has no outgoing
        Flow f = QuestFlowAnalyzer.analyze(states, in, out);
        assertEquals(Set.of("AL_MAIN_1"), ids(f.startNodes()));
        assertEquals(Set.of("AL_MAIN_3"), ids(f.endNodes()));
    }

    @Test
    void parallelForks_disambiguatedByOrdinal_notAllEntriesSurface() {
        // Three task-fork entries (1a/2a/3a, all no incoming) but only the lowest
        // ordinal is the real opening step; three completes (1b/2b/3b) → highest closes.
        List<StateNode> states = List.of(
            n(11, "AL_Q2_1a"), n(12, "AL_Q2_2a"), n(13, "AL_Q2_3a"),
            n(21, "AL_Q2_1b"), n(22, "AL_Q2_2b"), n(23, "AL_Q2_3b"));
        Map<Long, Integer> in = Map.of(21L, 1, 22L, 1, 23L, 1);   // the *a nodes have no incoming
        Map<Long, Integer> out = Map.of(11L, 1, 12L, 1, 13L, 1);  // the *b nodes have no outgoing
        Flow f = QuestFlowAnalyzer.analyze(states, in, out);
        assertEquals(Set.of("AL_Q2_1a"), ids(f.startNodes()));
        assertEquals(Set.of("AL_Q2_3b"), ids(f.endNodes()));
    }

    @Test
    void decimalSteps_orderCorrectly() {
        assertEquals(3.2, QuestFlowAnalyzer.ordinalOf("SP_Main_3.2"));
        assertEquals(10.5, QuestFlowAnalyzer.ordinalOf("MG_MAIN_10.5"));
        assertEquals(10.5, QuestFlowAnalyzer.ordinalOf("MG_MAIN_10.5_EXILE"));
        assertEquals(1.0, QuestFlowAnalyzer.ordinalOf("AL_Q2_1a"));
        assertNull(QuestFlowAnalyzer.ordinalOf("STEPTWO"));   // hand-authored id, no number
        assertNull(QuestFlowAnalyzer.ordinalOf(null));
    }

    @Test
    void noParseableIds_fallsBackToTopology() {
        // hand-authored quest ("The Rising Star"): keep topology entry/terminal as-is.
        List<StateNode> states = List.of(n(1, "STEPTWO"), n(2, "OA_Q1_A3"), n(3, "MMVCYOQ4P7"));
        // OA_Q1_A3 carries a number (A3 → 3 via the [a-z]* tail? no — it's _A3, letters not digits)
        Map<Long, Integer> in = Map.of(2L, 1, 3L, 1);
        Map<Long, Integer> out = Map.of(1L, 1, 2L, 1);
        Flow f = QuestFlowAnalyzer.analyze(states, in, out);
        assertEquals(Set.of("STEPTWO"), ids(f.startNodes()));
        assertEquals(Set.of("MMVCYOQ4P7"), ids(f.endNodes()));
    }

    @Test
    void flagPrefix_mainAndSide() {
        assertEquals("AL", QuestFlowAnalyzer.flagPrefix("AL_MAIN"));
        assertEquals("SP", QuestFlowAnalyzer.flagPrefix("SP_Main"));
        assertEquals("AL_Q2", QuestFlowAnalyzer.flagPrefix("AL_Q2_PageBoy"));
        assertEquals("MS_Q1", QuestFlowAnalyzer.flagPrefix("MS_Q1_NeighSayer"));
        assertEquals("VL_Q1", QuestFlowAnalyzer.flagPrefix("VL_Quest1_SMPPT"));   // Quest1 → Q1
        assertEquals("BIR_Q1", QuestFlowAnalyzer.flagPrefix("BIR_Q1"));
        assertNull(QuestFlowAnalyzer.flagPrefix("TestQuest"));
        assertNull(QuestFlowAnalyzer.flagPrefix("The Rising Star"));
    }

    @Test
    void startFlag_onlyForCityMainQuests() {
        assertEquals("AL_QuestStart", QuestFlowAnalyzer.startFlag("AL_MAIN"));
        assertEquals("MG_QuestStart", QuestFlowAnalyzer.startFlag("MG_MAIN"));
        assertEquals("SP_QuestStart", QuestFlowAnalyzer.startFlag("SP_Main"));
        // side quests carry no _QuestStart flag — never link the city-start scene to them
        assertNull(QuestFlowAnalyzer.startFlag("AL_Q2_PageBoy"));
        assertNull(QuestFlowAnalyzer.startFlag("TestQuest"));
    }

    @Test
    void endFlag_ranksEndOverDefeatedOverNothing() {
        // AL_Q2_END wins over AL_Q2_Repeating
        assertEquals("AL_Q2_END",
            QuestFlowAnalyzer.endFlag(List.of("AL_Q2_Repeating", "AL_Q2_END", "AL_Q2_Repetitions"), "AL_Q2"));
        // MR_Completed wins
        assertEquals("MR_Completed",
            QuestFlowAnalyzer.endFlag(List.of("MR_QuestStart", "MR_Completed", "MR_Story_DefeatedPrimalong"), "MR"));
        // only a "defeated" flag → use it
        assertEquals("AP_MorsiverDefeated",
            QuestFlowAnalyzer.endFlag(List.of("AP_BadEnding", "AP_MorsiverDefeated", "AP_QuestStart"), "AP"));
        // nothing reads as completion → null (never fabricate)
        assertNull(QuestFlowAnalyzer.endFlag(List.of("CL_QuestStart"), "CL"));
        // prefix mismatch is ignored
        assertNull(QuestFlowAnalyzer.endFlag(List.of("ZZ_END"), "AL"));
    }

    @Test
    void emptyQuest_yieldsEmptyFlow() {
        Flow f = QuestFlowAnalyzer.analyze(List.of(), Map.of(), Map.of());
        assertTrue(f.startNodes().isEmpty());
        assertTrue(f.endNodes().isEmpty());
    }
}
