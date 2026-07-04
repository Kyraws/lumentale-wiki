package com.lumentale.wiki.move;

import com.fasterxml.jackson.databind.JsonNode;
import com.lumentale.wiki.move.dto.MoveLearner;
import com.lumentale.wiki.move.dto.MoveSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Move endpoints (ported from v2 onto the redesigned schema).
 *
 *   GET /api/moves                 — move list (+ learner counts)
 *   GET /api/moves/{guid}          — move detail (raw pruned record)
 *   GET /api/moves/{guid}/learners — forms that learn it
 */
@RestController
@RequestMapping("/api")
public class MoveController {

    private final MoveService moves;

    public MoveController(MoveService moves) { this.moves = moves; }

    @GetMapping("/moves")
    public List<MoveSummary> moves(@RequestParam(required = false) String lang) {
        return moves.list(lang);
    }

    @GetMapping("/moves/{guid}")
    public JsonNode move(@PathVariable String guid) {
        return moves.detail(guid);
    }

    @GetMapping("/moves/{guid}/learners")
    public List<MoveLearner> learners(@PathVariable String guid) {
        return moves.learners(guid);
    }
}
