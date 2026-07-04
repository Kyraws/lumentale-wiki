package com.lumentale.wiki.squadron;

import com.lumentale.wiki.squadron.dto.SquadronDetail;
import com.lumentale.wiki.squadron.dto.SquadronSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Squadron endpoints on the redesigned schema.
 *
 *   GET /api/squadrons          — squadron list
 *   GET /api/squadrons/{guid}   — squadron detail (+ camp boss / member roster)
 */
@RestController
@RequestMapping("/api")
public class SquadronController {

    private final SquadronService squadrons;

    public SquadronController(SquadronService squadrons) { this.squadrons = squadrons; }

    @GetMapping("/squadrons")
    public List<SquadronSummary> squadrons() {
        return squadrons.list();
    }

    @GetMapping("/squadrons/{guid}")
    public SquadronDetail squadron(@PathVariable String guid) {
        return squadrons.detail(guid);
    }
}
