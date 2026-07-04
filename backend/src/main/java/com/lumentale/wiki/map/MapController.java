package com.lumentale.wiki.map;

import com.lumentale.wiki.map.dto.MapDetail;
import com.lumentale.wiki.map.dto.MapGraph;
import com.lumentale.wiki.map.dto.MapSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * World-map endpoints (ported from v2 onto the redesigned schema). Pure routing —
 * all work is delegated to {@link MapService}; a malformed guid is a clean 400
 * (via {@code Guids.require}) and a missing map/graph a typed 404.
 *
 *   GET /api/maps          — map list (one row per map + spawn count)
 *   GET /api/maps/{guid}    — full map page (spawns, points, shops, battles, exits, pickups)
 *   GET /api/map-graph      — the world connectivity graph
 */
@RestController
@RequestMapping("/api")
public class MapController {

    private final MapService maps;

    public MapController(MapService maps) { this.maps = maps; }

    @GetMapping("/maps")
    public List<MapSummary> maps(@RequestParam(name = "lang", required = false) String lang) {
        return maps.list(lang);
    }

    @GetMapping("/maps/{guid}")
    public MapDetail map(@PathVariable String guid,
                         @RequestParam(name = "lang", required = false) String lang) {
        return maps.detail(guid, lang);
    }

    @GetMapping("/map-graph")
    public MapGraph mapGraph() {
        return maps.graph();
    }
}
