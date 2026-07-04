package com.lumentale.wiki.achievement;

import com.fasterxml.jackson.databind.JsonNode;
import com.lumentale.wiki.achievement.dto.AchievementSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Achievement endpoints (Module 8 catalogue).
 *
 *   GET /api/achievements          — achievement list (name, rarity, steps, icon)
 *   GET /api/achievements/{guid}    — achievement detail (raw pruned record)
 */
@RestController
@RequestMapping("/api")
public class AchievementController {

    private final AchievementService achievements;

    public AchievementController(AchievementService achievements) { this.achievements = achievements; }

    @GetMapping("/achievements")
    public List<AchievementSummary> achievements(@RequestParam(required = false) String lang) {
        return achievements.list(lang);
    }

    @GetMapping("/achievements/{guid}")
    public JsonNode achievement(@PathVariable String guid, @RequestParam(required = false) String lang) {
        return achievements.detail(guid, lang);
    }
}
