package com.lumentale.wiki.quest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure logic that recovers <em>where a quest starts and where it completes</em> from
 * its state-machine topology — no DB, no Spring, so it is unit-tested directly
 * ({@code QuestFlowAnalyzerTest}).
 *
 * <p>A quest's nodes form a directed graph (state nodes chained by Next/Previous,
 * branch nodes gating on flags). We combine two signals:
 * <ol>
 *   <li><b>Topology</b> — an <em>entry</em> node has no incoming transition; a
 *       <em>terminal</em> node has no outgoing transition. This is authoritative for
 *       graph reachability but can be noisy (parallel task-forks, disconnected
 *       {@code _EXILE} variants), so we never trust it alone.</li>
 *   <li><b>State-id ordinal</b> — most state ids carry a numeric step
 *       ({@code AL_MAIN_1}, {@code SP_Main_3.2}, {@code AL_Q2_1a}); the lowest is the
 *       opening step, the highest the closing step. Ids with no parseable number
 *       (a few hand-authored quests) fall back to topology order only.</li>
 * </ol>
 *
 * <p>The flag side (which story scene <em>sets</em> the quest's start/end flag) is a
 * separate DB cross-reference (see {@code QuestLinkRepository}); the
 * {@link #flagPrefix(String)} helper here derives the {@code AL}/{@code AL_Q2} prefix
 * used to match a quest's flags by naming convention.
 */
public final class QuestFlowAnalyzer {

    private QuestFlowAnalyzer() {}

    /** A state node reduced to the fields the analyzer needs. */
    public record StateNode(long pathid, String stateId, String label) {}

    /**
     * The recovered start/end of a quest.
     *
     * @param startNodes the opening state node(s): no incoming edge, lowest ordinal
     * @param endNodes   the closing state node(s): no outgoing edge, highest ordinal
     */
    public record Flow(List<StateNode> startNodes, List<StateNode> endNodes) {}

    /**
     * Recover the start/end state nodes.
     *
     * @param states      every {@code kind='state'} node (branch nodes are excluded —
     *                    they are conditional gates, not narrative steps)
     * @param incoming    pathid → count of transitions pointing AT it
     * @param outgoing    pathid → count of transitions leaving it
     */
    public static Flow analyze(List<StateNode> states,
                               Map<Long, Integer> incoming,
                               Map<Long, Integer> outgoing) {
        if (states == null || states.isEmpty()) return new Flow(List.of(), List.of());

        // Entry/terminal by topology.
        List<StateNode> entries = states.stream()
            .filter(n -> incoming.getOrDefault(n.pathid(), 0) == 0)
            .toList();
        List<StateNode> terminals = states.stream()
            .filter(n -> outgoing.getOrDefault(n.pathid(), 0) == 0)
            .toList();

        // If topology gives a single clean entry/terminal, trust it. Otherwise pick by
        // the state-id ordinal (lowest opens, highest closes) so parallel forks and
        // disconnected variants don't all surface as "starts".
        List<StateNode> start = pickByOrdinal(entries.isEmpty() ? states : entries, true);
        List<StateNode> end   = pickByOrdinal(terminals.isEmpty() ? states : terminals, false);
        return new Flow(start, end);
    }

    /**
     * From a candidate set, keep the node(s) with the extreme ordinal (min for the
     * start, max for the end). Nodes whose ordinal ties for the extreme are all kept
     * (genuine parallel branches). Nodes with no parseable ordinal are kept only when
     * NONE of the candidates have one (otherwise the numbered steps win).
     */
    private static List<StateNode> pickByOrdinal(List<StateNode> candidates, boolean min) {
        List<StateNode> withNum = candidates.stream().filter(n -> ordinalOf(n.stateId()) != null).toList();
        if (withNum.isEmpty()) return candidates;            // hand-authored ids: keep topology set
        Comparator<StateNode> byOrd = Comparator.comparingDouble(n -> ordinalOf(n.stateId()));
        double extreme = (min
            ? withNum.stream().min(byOrd)
            : withNum.stream().max(byOrd)).map(n -> ordinalOf(n.stateId())).orElse(0.0);
        List<StateNode> out = new ArrayList<>();
        for (StateNode n : withNum) if (ordinalOf(n.stateId()) == extreme) out.add(n);
        return out;
    }

    // Trailing step number in a state id: AL_MAIN_1 → 1, SP_Main_3.2 → 3.2,
    // AL_Q2_1a → 1, MG_MAIN_10.5_EXILE → 10.5. null when there is no number.
    private static final Pattern STEP = Pattern.compile("_(\\d+(?:\\.\\d+)?)[a-zA-Z]*(?:_[A-Za-z]+)?$");

    /** The numeric step of a state id, or null when it carries none. Package-private for tests. */
    static Double ordinalOf(String stateId) {
        if (stateId == null) return null;
        Matcher m = STEP.matcher(stateId);
        if (!m.find()) return null;
        try { return Double.parseDouble(m.group(1)); }
        catch (NumberFormatException e) { return null; }
    }

    /**
     * The flag-naming prefix for a quest, used to match its variables/flags by
     * convention. Main quests collapse to the city tag ({@code AL_MAIN} → {@code AL},
     * {@code SP_Main} → {@code SP}); side quests keep the quest tag
     * ({@code AL_Q2_PageBoy} → {@code AL_Q2}, {@code MS_Q1_NeighSayer} → {@code MS_Q1}).
     * Returns null for quests whose internal name carries no city/quest tag
     * (TestQuest, "The Rising Star", …) — those have no flag convention to match.
     */
    public static String flagPrefix(String internalName) {
        if (internalName == null) return null;
        Matcher main = Pattern.compile("^([A-Z]{2,4})_(?i:MAIN)\\b").matcher(internalName);
        if (main.find()) return main.group(1);
        Matcher side = Pattern.compile("^([A-Z]{2,4}_Q(?:uest)?\\d+)").matcher(internalName);
        if (side.find()) return side.group(1).replace("Quest", "Q");
        Matcher bare = Pattern.compile("^([A-Z]{2,4})_").matcher(internalName);
        if (bare.find()) return bare.group(1);
        return null;
    }

    /**
     * The {@code <CITY>_QuestStart} flag a <em>city main</em> quest sets on entry.
     * Only main quests ({@code <CITY>_MAIN}) use this convention; side quests/tasks
     * carry no {@code _QuestStart} flag, so they return null (their start is recovered
     * from the entry node + linked scenes instead).
     */
    public static String startFlag(String internalName) {
        if (internalName == null || !Pattern.compile("^[A-Z]{2,4}_(?i:MAIN)\\b").matcher(internalName).find())
            return null;
        String p = cityTag(internalName);
        return p == null ? null : p + "_QuestStart";
    }

    /** The city tag (first 2–4 caps before the first underscore), or null. */
    static String cityTag(String internalName) {
        if (internalName == null) return null;
        Matcher m = Pattern.compile("^([A-Z]{2,4})_").matcher(internalName);
        return m.find() ? m.group(1) : null;
    }

    /**
     * From the flags a quest touches, pick the one that most plausibly marks
     * <em>completion</em>. The game has no single convention, so we rank by a
     * suffix/keyword priority and require the flag to share the quest's prefix.
     * Returns null when none of the quest's flags read as a completion marker — we
     * never invent one (the topology-terminal node still describes the end).
     *
     * @param flags        the distinct flag names the quest's scenes set/check
     * @param questPrefix  {@link #flagPrefix(String)} of the quest (e.g. {@code AL_Q2})
     */
    public static String endFlag(Collection<String> flags, String questPrefix) {
        if (flags == null || questPrefix == null) return null;
        String best = null;
        int bestRank = Integer.MAX_VALUE;
        for (String f : flags) {
            if (f == null || !f.toUpperCase().startsWith(questPrefix.toUpperCase())) continue;
            String u = f.toUpperCase();
            int rank = u.endsWith("_END") ? 0
                : (u.endsWith("_COMPLETED") || u.endsWith("_COMPLETE")) ? 1
                : u.contains("_COMPLETE") ? 2
                : (u.contains("DEFEATED") || u.contains("SAVED") || u.contains("WON")) ? 3
                : -1;
            if (rank >= 0 && rank < bestRank) { bestRank = rank; best = f; }
        }
        return best;
    }
}
