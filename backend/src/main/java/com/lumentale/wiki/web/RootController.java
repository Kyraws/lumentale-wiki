package com.lumentale.wiki.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Root index. This is an API, not a UI — {@code GET /} returns a JSON map of the
 * available endpoints, grouped by page. The full backend is implemented over the
 * redesigned schema; the only deferred endpoint is the asset resolver (its
 * Addressables GUID cache isn't extracted yet — see ARCHITECTURE §6).
 */
@RestController
public class RootController {

    @GetMapping("/")
    public Map<String,Object> index() {
        Map<String,Object> root = new LinkedHashMap<>();
        root.put("name", "LumenTale Wiki API (backend-v3)");
        root.put("schema", "wiki-db redesign (anidex3)");
        root.put("health", "/actuator/health");

        Map<String,List<String>> api = new LinkedHashMap<>();
        api.put("creatures", List.of("/api/creatures", "/api/creatures/{guid}", "/api/species/{guid}/variants"));
        api.put("moves",     List.of("/api/moves", "/api/moves/{guid}", "/api/moves/{guid}/learners"));
        api.put("items",     List.of("/api/items", "/api/items/{guid}"));
        api.put("cards",     List.of("/api/cards", "/api/cards/{guid}", "/api/card-pools"));
        api.put("furniture", List.of("/api/furniture", "/api/furniture/{guid}"));
        api.put("bosses",    List.of("/api/bosses", "/api/bosses/{guid}", "/api/bosses/{guid}/graph"));
        api.put("trainers",  List.of("/api/trainers", "/api/trainers/{guid}"));
        api.put("camps",     List.of("/api/camps", "/api/camps/{guid}"));
        api.put("squadrons", List.of("/api/squadrons", "/api/squadrons/{guid}"));
        api.put("world",     List.of("/api/maps", "/api/maps/{guid}", "/api/map-graph"));
        api.put("quests",    List.of("/api/quests", "/api/quests/{guid}", "/api/quests/{guid}/graph?lang="));
        api.put("story",     List.of("/api/story/cities?path=", "/api/story/scene?id="));
        api.put("catalogue", List.of("/api/achievements", "/api/achievements/{guid}", "/api/tutorials", "/api/tutorials/{guid}"));
        api.put("mechanics", List.of("/api/mechanics", "/api/mechanics/constants", "/api/mechanics/formulas/{key}", "/api/mechanics/xp-curves/{curveType}"));
        api.put("logicGraphs", List.of("/api/behavior-trees", "/api/behavior-trees/{pathId}", "/api/timelines", "/api/timelines/{pathId}", "/api/minigames", "/api/minigames/{pathId}"));
        api.put("types",     List.of("/api/types/coverage", "/api/types/defenders?limit=", "/api/types/offense", "/api/quirks?lang="));
        api.put("localization", List.of("/api/loc/{lang}", "/api/loc/{lang}/{table}"));
        api.put("meta",      List.of("/api/meta"));
        root.put("api", api);

        root.put("deferred", List.of("asset resolver (Addressables GUID cache not yet extracted)"));
        return root;
    }
}
