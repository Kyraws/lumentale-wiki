package com.lumentale.wiki.quest;

import com.fasterxml.jackson.databind.JsonNode;
import com.lumentale.wiki.quest.dto.QuestGraph;
import com.lumentale.wiki.quest.dto.QuestSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Quest endpoints (ported from v2 onto the redesigned schema).
 *
 *   GET /api/quests              — quest list (+ localized title, node counts)
 *   GET /api/quests/{guid}       — quest detail (raw pruned record)
 *   GET /api/quests/{guid}/graph — the quest state-machine graph (localized ?lang=)
 */
@RestController
@RequestMapping("/api")
public class QuestController {

    private final QuestService quests;

    public QuestController(QuestService quests) { this.quests = quests; }

    @GetMapping("/quests")
    public List<QuestSummary> quests(@RequestParam(name = "lang", required = false) String lang) {
        return quests.list(lang);
    }

    @GetMapping("/quests/{guid}")
    public JsonNode quest(@PathVariable String guid) {
        return quests.detail(guid);
    }

    @GetMapping("/quests/{guid}/graph")
    public QuestGraph graph(@PathVariable String guid,
                            @RequestParam(name = "lang", required = false) String lang) {
        return quests.graph(guid, lang);
    }
}
