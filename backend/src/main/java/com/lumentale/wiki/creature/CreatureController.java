package com.lumentale.wiki.creature;

import com.fasterxml.jackson.databind.JsonNode;
import com.lumentale.wiki.creature.dto.CreatureDetail;
import com.lumentale.wiki.creature.dto.CreatureSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Creature (anidex) endpoints — the reference slice. Pure routing; all work is
 * delegated to {@link CreatureService}. Not-found / bad-guid are signalled by
 * thrown exceptions and rendered by the global handler, so there are no
 * try/catch or ResponseEntity ceremonies here.
 *
 *   GET /api/creatures                 — dex grid (one row per form)
 *   GET /api/creatures/{guid}          — full creature detail + cross-links
 *   GET /api/species/{guid}/variants   — sibling forms of a species
 */
@RestController
@RequestMapping("/api")
public class CreatureController {

    private final CreatureService creatures;

    public CreatureController(CreatureService creatures) { this.creatures = creatures; }

    @GetMapping("/creatures")
    public List<CreatureSummary> creatures() {
        return creatures.dexGrid();
    }

    @GetMapping("/creatures/{guid}")
    public CreatureDetail creature(@PathVariable String guid,
                                   @RequestParam(required = false) String lang) {
        return creatures.detail(guid, lang);
    }

    @GetMapping("/species/{guid}/variants")
    public List<JsonNode> variants(@PathVariable String guid) {
        return creatures.variants(guid);
    }
}
