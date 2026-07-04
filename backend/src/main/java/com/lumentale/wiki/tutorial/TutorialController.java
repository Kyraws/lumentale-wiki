package com.lumentale.wiki.tutorial;

import com.lumentale.wiki.tutorial.dto.TutorialDetail;
import com.lumentale.wiki.tutorial.dto.TutorialSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tutorial endpoints (Module 6 catalogue).
 *
 *   GET /api/tutorials          — tutorial list (internalName, titleKey, pageCount)
 *   GET /api/tutorials/{guid}    — tutorial detail (+ ordered pages)
 */
@RestController
@RequestMapping("/api")
public class TutorialController {

    private final TutorialService tutorials;

    public TutorialController(TutorialService tutorials) { this.tutorials = tutorials; }

    @GetMapping("/tutorials")
    public List<TutorialSummary> tutorials(@RequestParam(required = false) String lang) {
        return tutorials.list(lang);
    }

    @GetMapping("/tutorials/{guid}")
    public TutorialDetail tutorial(@PathVariable String guid, @RequestParam(required = false) String lang) {
        return tutorials.detail(guid, lang);
    }
}
