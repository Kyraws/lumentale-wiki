package com.lumentale.wiki.camp;

import com.lumentale.wiki.camp.dto.CampDetail;
import com.lumentale.wiki.camp.dto.CampSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Camp endpoints on the redesigned schema.
 *
 *   GET /api/camps          — camp list
 *   GET /api/camps/{guid}   — camp detail (+ target forms / unlocked tasks)
 */
@RestController
@RequestMapping("/api")
public class CampController {

    private final CampService camps;

    public CampController(CampService camps) { this.camps = camps; }

    @GetMapping("/camps")
    public List<CampSummary> camps() {
        return camps.list();
    }

    @GetMapping("/camps/{guid}")
    public CampDetail camp(@PathVariable String guid, @RequestParam(required = false) String lang) {
        return camps.detail(guid, lang);
    }
}
