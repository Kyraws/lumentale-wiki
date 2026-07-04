package com.lumentale.wiki.boss;

import com.lumentale.wiki.boss.dto.BossDetail;
import com.lumentale.wiki.boss.dto.BossGraph;
import com.lumentale.wiki.boss.dto.BossSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Boss endpoints — the second reference slice, exercising the new logic-graph
 * layer (whole-graph jsonb read + the strong-skill rollup) and the cross-links
 * the redesign wires up.
 *
 *   GET /api/bosses               — boss list (level-ordered)
 *   GET /api/bosses/{guid}        — boss detail: stats, kit, cross-links, graph pointer
 *   GET /api/bosses/{guid}/graph  — the whole scripted battle graph (one jsonb row)
 */
@RestController
@RequestMapping("/api")
public class BossController {

    private final BossService bosses;

    public BossController(BossService bosses) { this.bosses = bosses; }

    @GetMapping("/bosses")
    public List<BossSummary> bosses() {
        return bosses.list();
    }

    @GetMapping("/bosses/{guid}")
    public BossDetail boss(@PathVariable String guid, @RequestParam(required = false) String lang) {
        return bosses.detail(guid, lang);
    }

    @GetMapping("/bosses/{guid}/graph")
    public BossGraph graph(@PathVariable String guid, @RequestParam(required = false) String lang) {
        return bosses.graph(guid, lang);
    }
}
