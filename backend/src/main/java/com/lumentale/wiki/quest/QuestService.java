package com.lumentale.wiki.quest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumentale.wiki.common.Guids;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.common.RawRecordService;
import com.lumentale.wiki.common.RawRecordService.RawTable;
import com.lumentale.wiki.error.NotFoundException;
import com.lumentale.wiki.quest.QuestRepository.NodeRow;
import com.lumentale.wiki.quest.QuestRepository.QuestRow;
import com.lumentale.wiki.quest.QuestRepository.RewardRow;
import com.lumentale.wiki.quest.QuestFlowAnalyzer.StateNode;
import com.lumentale.wiki.quest.QuestLinkRepository.StateRow;
import com.lumentale.wiki.quest.dto.QuestGraph;
import com.lumentale.wiki.quest.dto.QuestGraph.Node;
import com.lumentale.wiki.quest.dto.QuestGraph.RewardItem;
import com.lumentale.wiki.quest.dto.QuestGraph.Rewards;
import com.lumentale.wiki.quest.dto.QuestGraph.Transition;
import com.lumentale.wiki.quest.dto.QuestStartEnd;
import com.lumentale.wiki.quest.dto.QuestStartEnd.Endpoint;
import com.lumentale.wiki.quest.dto.QuestStartEnd.MapLink;
import com.lumentale.wiki.quest.dto.QuestStartEnd.SceneLink;
import com.lumentale.wiki.quest.dto.QuestStartEnd.Step;
import com.lumentale.wiki.quest.dto.QuestSummary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Assembles quest responses. The list/detail/graph come from {@link QuestRepository};
 * localized titles/descriptions/objectives resolve through the shared
 * {@link LocalizationResolver} (cached per table+lang), and the raw detail record is
 * served by {@link RawRecordService}. Guid validation happens here (→ 400); a missing
 * quest is a typed 404.
 */
@Service
public class QuestService {

    private final QuestRepository repo;
    private final QuestLinkRepository links;
    private final LocalizationResolver loc;
    private final RawRecordService rawRecords;
    private final ObjectMapper mapper;

    public QuestService(QuestRepository repo, QuestLinkRepository links, LocalizationResolver loc,
                        RawRecordService rawRecords, ObjectMapper mapper) {
        this.repo = repo;
        this.links = links;
        this.loc = loc;
        this.rawRecords = rawRecords;
        this.mapper = mapper;
    }

    /**
     * List with English-by-default titles; city/track are intentionally omitted.
     * {@code name} now carries the resolved English title (falling back to the
     * Italian {@code name_raw}); {@code title} keeps the same resolved value for the
     * existing contract. Honours {@code ?lang=} (default {@code en}).
     */
    public List<QuestSummary> list(String lang) {
        String l = loc.normalize(lang);
        Map<String, String> loc_ = loc.sourceToLang("QUEST", l);
        return repo.summaries().stream()
            .map(r -> {
                String title = localize(r.nameRaw(), loc_);
                return new QuestSummary(r.guid(), title, title, r.giver(), r.type(), r.nodes());
            })
            .toList();
    }

    public JsonNode detail(String guidStr) {
        UUID guid = Guids.require(guidStr);
        return rawRecords.find(RawTable.QUEST, guid).orElseThrow(() -> new NotFoundException("quest", guidStr));
    }

    public QuestGraph graph(String guidStr, String lang) {
        UUID guid = Guids.require(guidStr);
        QuestRow q = repo.base(guid).orElseThrow(() -> new NotFoundException("quest", guidStr));

        String l = loc.normalize(lang);
        Map<String, String> locQuest = loc.sourceToLang("QUEST", l);              // names: IT → lang
        Map<String, String> locDesc  = loc.sourceToLang("QUEST_DESCRIPTIONS", l); // descriptions
        ObjectiveResolver obj = objectiveResolver(l);
        Map<Long, List<Transition>> edges = repo.transitions(guid);

        List<Node> nodes = new ArrayList<>();
        for (NodeRow r : repo.nodeRows(guid)) {
            List<Transition> tr = edges.getOrDefault(r.pathid(), List.of());
            if ("state".equals(r.kind())) {
                String txt = obj.resolve(r.objectivesKey(), r.missionLabelRaw());
                nodes.add(new Node(r.pathid(), r.kind(), r.stateId(), r.stateName(), txt, null, tr));
            } else {
                nodes.add(new Node(r.pathid(), r.kind(), null, null, null,
                    parseJson(r.conditionsJson()), tr));
            }
        }

        return new QuestGraph(guid.toString(), q.internalName(), localize(q.nameRaw(), locQuest),
            localize(q.descriptionRaw(), locDesc), q.giver(), q.type(),
            rewards(guid, q, l), startEnd(guid, q.internalName(), obj), nodes);
    }

    // ---- start / completion recovery ----

    /**
     * Recover where the quest starts and completes: the opening/closing state nodes
     * (with the maps they target) from {@link QuestFlowAnalyzer}, cross-referenced
     * with the story scenes that set the quest's start/end flags and the wider set of
     * scenes that touch any flag with the quest's prefix.
     */
    private QuestStartEnd startEnd(UUID guid, String internalName, ObjectiveResolver obj) {
        List<StateRow> rows = links.stateNodes(guid);
        if (rows.isEmpty()) return new QuestStartEnd(null, null, List.of());

        List<StateNode> states = rows.stream().map(StateRow::node).toList();
        Map<Long, Integer> in = links.incoming(guid);
        Map<Long, Integer> out = links.outgoing(guid);
        QuestFlowAnalyzer.Flow flow = QuestFlowAnalyzer.analyze(states, in, out);

        // Resolve every target-area guid the start/end steps reference, in one query.
        Set<Long> startEndIds = new java.util.HashSet<>();
        flow.startNodes().forEach(n -> startEndIds.add(n.pathid()));
        flow.endNodes().forEach(n -> startEndIds.add(n.pathid()));
        Set<String> mapGuids = new LinkedHashSet<>();
        Map<Long, String> areaByNode = new java.util.HashMap<>();
        for (StateRow r : rows) {
            if (startEndIds.contains(r.node().pathid()) && r.targetAreaGuid() != null) {
                areaByNode.put(r.node().pathid(), r.targetAreaGuid());
                mapGuids.add(r.targetAreaGuid());
            }
        }
        Map<String, MapLink> maps = links.maps(mapGuids);

        String prefix = QuestFlowAnalyzer.flagPrefix(internalName);
        String startFlag = QuestFlowAnalyzer.startFlag(internalName);

        // All flags the quest touches (via prefix) → the linked-scene rollup + end flag.
        List<SceneLink> allLinked = prefix == null ? List.of()
            : links.scenesForFlag(prefix, null, null);
        Set<String> distinctFlags = new LinkedHashSet<>();
        allLinked.forEach(s -> distinctFlags.add(s.flag()));
        String endFlag = QuestFlowAnalyzer.endFlag(distinctFlags, prefix);

        Endpoint start = endpoint(flow.startNodes(), areaByNode, maps, startFlag,
            startFlag == null ? null : links.scenesForFlag(prefix, startFlag, "set"), obj);
        Endpoint end = endpoint(flow.endNodes(), areaByNode, maps, endFlag,
            endFlag == null ? null : links.scenesForFlag(prefix, endFlag, "set"), obj);

        return new QuestStartEnd(start, end, allLinked);
    }

    private Endpoint endpoint(List<StateNode> nodes, Map<Long, String> areaByNode,
                              Map<String, MapLink> maps, String flag, List<SceneLink> scenes,
                              ObjectiveResolver obj) {
        List<Step> steps = new ArrayList<>();
        for (StateNode n : nodes) {
            String area = areaByNode.get(n.pathid());
            // The flow label is a coalesced mission_label_raw (Italian) | state_name |
            // state_id; localize the objective text when it has an English source.
            steps.add(new Step(n.pathid(), n.stateId(), obj.resolveText(n.label()),
                area == null ? null : maps.get(area)));
        }
        return new Endpoint(steps, flag, scenes == null ? List.of() : scenes);
    }

    // ---- objective localization ----

    /**
     * Resolve quest-objective text to {@code lang}. Objectives have TWO independent
     * routes into the {@code QUEST_OBJECTIVES} loc table:
     * <ul>
     *   <li><b>by key</b> — {@code quest_node.objectives_key} (a {@code quest_state_…}
     *       code) matches a {@code loc_key.m_key} for ~217 nodes;</li>
     *   <li><b>by source text</b> — the Italian {@code mission_label_raw} matches an
     *       {@code it} source string in the same table, bridging IT → string_id → lang
     *       (covers ~265 nodes).</li>
     * </ul>
     * Combined they localize ~271/284 labels; the remainder genuinely have no English
     * source and fall back to the Italian.
     */
    record ObjectiveResolver(Map<String, String> byKey, Map<String, String> byItalian) {
        /** Graph node: prefer the key match, then the source-text match, then raw. */
        String resolve(String key, String raw) {
            if (key != null) {
                String t = byKey.get(key);
                if (t != null && !t.isBlank()) return t;
            }
            return resolveText(raw);
        }

        /** A coalesced label (already text): source-text match, else the raw text. */
        String resolveText(String raw) {
            if (raw == null) return null;
            String t = byItalian.get(raw);
            return (t != null && !t.isBlank()) ? t : raw;
        }
    }

    private ObjectiveResolver objectiveResolver(String lang) {
        return new ObjectiveResolver(
            loc.table("QUEST_OBJECTIVES", lang),          // m_key → text (objectives_key path)
            loc.sourceToLang("QUEST_OBJECTIVES", lang));  // IT source text → text
    }

    // ---- helpers ----

    private static String localize(String sourceText, Map<String, String> table) {
        return sourceText == null ? null : table.getOrDefault(sourceText, sourceText);
    }

    private Rewards rewards(UUID guid, QuestRow q, String lang) {
        List<RewardItem> items = new ArrayList<>();
        for (RewardRow r : repo.rewards(guid))
            items.add(new RewardItem(r.kind(), r.guid(), r.amount(),
                loc.display(lang, r.nameKey(), r.name())));
        return new Rewards(q.money(), q.exp(), items);
    }

    /** A node's stored conditions jsonb → JsonNode; null when absent or malformed. */
    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return mapper.readTree(json); }
        catch (Exception e) { return null; }
    }
}
