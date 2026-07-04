package com.lumentale.wiki.story;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.error.NotFoundException;
import com.lumentale.wiki.story.StoryRepository.SceneRow;
import com.lumentale.wiki.story.dto.SceneDetail;
import com.lumentale.wiki.story.dto.SceneLite;
import com.lumentale.wiki.story.dto.StoryCity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Assembles the Story page: the city-grouped backbone (scenes per city, with the
 * north/south fork filter) and the whole-scene reader. Thin — the repository does
 * the jsonb reads and rollup joins; the track classification is the pure
 * {@link StoryGeography} (unit-tested). {@code scene_id} is a {@code text} PK, so a
 * missing one is a typed 404, no guid validation.
 */
@Service
public class StoryService {

    private final StoryRepository repo;
    private final ObjectMapper mapper;
    private final LocalizationResolver loc;

    public StoryService(StoryRepository repo, ObjectMapper mapper, LocalizationResolver loc) {
        this.repo = repo;
        this.mapper = mapper;
        this.loc = loc;
    }

    /**
     * Cities for the Story page. {@code path} ∈ both|north|south filters the fork:
     * the opposite path's cities are hidden; shared/hub/other are always shown.
     */
    public List<StoryCity> cities(String path) {
        Map<String, List<SceneLite>> scenesByCity = repo.allScenes().stream()
            .collect(Collectors.groupingBy(SceneLite::region, LinkedHashMap::new, Collectors.toList()));

        // Within a city: main-quest scenes first in quest-line order, then the rest.
        Comparator<SceneLite> sceneOrder = Comparator
            .comparing(SceneLite::mainNum, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SceneLite::name, Comparator.nullsLast(String::compareToIgnoreCase));

        List<StoryCity> out = new ArrayList<>();
        for (var e : scenesByCity.entrySet()) {
            String track = StoryGeography.trackOf(e.getKey());
            if (!StoryGeography.pathAllows(path, track)) continue;
            List<SceneLite> scenes = new ArrayList<>(e.getValue());
            scenes.sort(sceneOrder);
            out.add(new StoryCity(e.getKey(), track, scenes));
        }
        // Cities in play order: track first, then the chapter the city is entered at.
        out.sort(Comparator
            .comparingInt((StoryCity c) -> StoryGeography.trackRank(c.track()))
            .thenComparing(c -> {
                Integer ch = StoryGeography.chapterOf(c.region());
                return ch == null ? Integer.MAX_VALUE : ch;
            })
            .thenComparing(StoryCity::region));
        return out;
    }

    /** One scene as an enriched flow; missing scene_id → 404. */
    public SceneDetail scene(String id, String lang) {
        SceneRow s = repo.sceneRow(id).orElseThrow(() -> new NotFoundException("scene", id));
        JsonNode nodes = parse(s.nodesText(), id);
        localizeDialogue(nodes, loc.normalize(lang));
        nameItems(nodes, loc.normalize(lang));
        JsonNode edges = parse(s.edgesText(), id);
        JsonNode entries = parse(s.entriesText(), id);
        String track = StoryGeography.trackOf(s.region());
        Integer chapter = s.chapter() != null ? s.chapter() : StoryGeography.chapterOf(s.region());
        Double main = s.mainNum() != null ? s.mainNum() : StoryGeography.mainNumOf(s.name());
        var prevNext = main == null ? null : spineNeighbours(id);
        return new SceneDetail(
            s.sceneId(), s.region(), track, s.name(), chapter, main,
            nodes, edges, entries,
            repo.flags(id), repo.battles(id), repo.maps(id),
            prevNext == null ? null : prevNext[0],
            prevNext == null ? null : prevNext[1]);
    }

    /**
     * Quests a scene is linked to by a shared flag (starts / completes / related),
     * with the quest title localized. Drives the "Starts quest X" badge on the scene
     * view. Missing scene_id just yields an empty list (no flags → no links).
     */
    public List<com.lumentale.wiki.story.dto.SceneQuestLink> sceneQuests(String id, String lang) {
        String l = loc.normalize(lang);
        Map<String, String> locQuest = loc.sourceToLang("QUEST", l);
        return repo.questLinks(id).stream()
            .map(q -> new com.lumentale.wiki.story.dto.SceneQuestLink(
                q.guid(), q.internalName(),
                locQuest.getOrDefault(q.name(), q.name()),
                q.relation(), q.flag(), q.mode()))
            .toList();
    }

    /**
     * Prev/next along the main-quest spine — every scene with a recovered main
     * number, ordered (chapter, main, name). Replaces the old SQL neighbour
     * lookup, which depended on the (unseeded) chapter/main_num columns.
     */
    private SceneDetail.Neighbour[] spineNeighbours(String id) {
        List<SceneLite> spine = repo.allScenes().stream()
            .filter(s -> s.mainNum() != null)
            .sorted(Comparator
                .comparing(SceneLite::chapter, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SceneLite::mainNum)
                .thenComparing(SceneLite::name, Comparator.nullsLast(String::compareToIgnoreCase)))
            .toList();
        for (int i = 0; i < spine.size(); i++) {
            if (spine.get(i).sceneId().equals(id)) {
                return new SceneDetail.Neighbour[] {
                    i > 0 ? toNeighbour(spine.get(i - 1)) : null,
                    i < spine.size() - 1 ? toNeighbour(spine.get(i + 1)) : null,
                };
            }
        }
        return null;
    }

    private static SceneDetail.Neighbour toNeighbour(SceneLite s) {
        return new SceneDetail.Neighbour(s.sceneId(), s.name());
    }

    /**
     * Dialogue lines are stored raw (Italian) with their loc key:
     * {@code {k: "DIALOGUE.[scene].speaker.mood.<guid>", t: "..."}}. Resolve each
     * line's text through the localization tables (English by default), keeping
     * the raw text when no translation exists.
     */
    private void localizeDialogue(JsonNode nodes, String lang) {
        if (nodes == null || !nodes.isArray()) return;
        // NAMES m_keys are the Italian/dev NPC names, some with stray whitespace
        // (" Kapan") — index them trimmed for the speaker match.
        Map<String, String> npcNames = new java.util.HashMap<>();
        loc.table("NAMES", lang).forEach((k, v) -> npcNames.put(k.trim(), v));
        for (JsonNode n : nodes) {
            if (!"dialogue".equals(n.path("kind").asText())) continue;
            // Speaker labels are stored as the Italian/dev NPC name ("Contadino",
            // "Guardia"); the NAMES loc table maps them to the display language.
            if (n instanceof ObjectNode on) {
                String sp = on.path("speaker").asText("").trim();
                if (!sp.isBlank()) {
                    String tr = npcNames.get(sp);
                    if (tr != null && !tr.isBlank()) on.put("speaker", tr.trim());
                }
            }
            JsonNode lines = n.path("lines");
            if (!lines.isArray()) continue;
            for (JsonNode line : lines) {
                if (!(line instanceof ObjectNode o)) continue;
                String k = o.path("k").asText(null);
                String raw = o.path("t").asText(null);
                if (k != null) o.put("t", resolveLine(lang, k, raw));
            }
        }
    }

    /** Inject the English item name into "Give Item" nodes (item_guid backfilled
     *  from the raw Add Item Actions — see fix_story_item_guids.py). */
    private void nameItems(JsonNode nodes, String lang) {
        if (nodes == null || !nodes.isArray()) return;
        for (JsonNode n : nodes) {
            if (!(n instanceof ObjectNode o) || !"item".equals(n.path("kind").asText())) continue;
            String guid = n.path("item_guid").asText(null);
            if (guid == null) continue;
            String name = repo.itemName(guid, lang);
            if (name != null) o.put("item_name", name);
        }
    }

    /**
     * Resolve one dialogue line. The scene keys and the loc-table keys drifted in
     * format ("DIALOGUE.[Scene].…" vs "DIALOGUE.Scene.…", renamed speaker/mood
     * segments), so try: the exact key, the de-bracketed key, then a match on the
     * line's trailing GUID (the only stable part). Lines with no translation at
     * all keep the raw Italian — that content was never localized in the game.
     */
    private String resolveLine(String lang, String k, String raw) {
        String t = loc.display(lang, k, null);
        if (t == null) t = loc.display(lang, k.replace("[", "").replace("]", ""), null);
        if (t == null && k.length() >= 36) t = dialogueByGuid(lang).get(k.substring(k.length() - 36));
        return t != null ? t : raw;
    }

    // {trailing line guid → text} per language, derived once from the loc map.
    private final java.util.concurrent.ConcurrentHashMap<String, Map<String, String>> dlgByGuid =
        new java.util.concurrent.ConcurrentHashMap<>();

    private Map<String, String> dialogueByGuid(String lang) {
        return dlgByGuid.computeIfAbsent(lang, l -> {
            Map<String, String> out = new java.util.HashMap<>(12000);
            for (var e : loc.all(l).entrySet()) {
                String key = e.getKey();
                if (key.startsWith("DIALOGUE.") && key.length() > 36) {
                    out.put(key.substring(key.length() - 36), e.getValue());
                }
            }
            return out;
        });
    }

    private JsonNode parse(String json, String id) {
        if (json == null) return null;
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt scene flow JSON for scene_id=" + id, e);
        }
    }
}
