package com.lumentale.wiki.logicgraph;

import com.lumentale.wiki.logicgraph.dto.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Logic-graph endpoints (M10) — the three logic-as-data families, each rendered
 * one whole graph per page.
 *
 *   GET /api/behavior-trees           /{pathId}   — AI behavior trees
 *   GET /api/timelines                /{pathId}   — cutscene timeline directors
 *   GET /api/minigames                /{pathId}   — minigame instances (+fields)
 */
@RestController
@RequestMapping("/api")
public class LogicGraphController {

    private final LogicGraphService graphs;

    public LogicGraphController(LogicGraphService graphs) { this.graphs = graphs; }

    @GetMapping("/behavior-trees")
    public List<BehaviorTreeSummary> behaviorTrees() { return graphs.behaviorTrees(); }

    @GetMapping("/behavior-trees/{pathId}")
    public BehaviorTreeDetail behaviorTree(@PathVariable long pathId) { return graphs.behaviorTree(pathId); }

    @GetMapping("/timelines")
    public List<TimelineSummary> timelines() { return graphs.timelines(); }

    @GetMapping("/timelines/{pathId}")
    public TimelineDetail timeline(@PathVariable long pathId) { return graphs.timeline(pathId); }

    @GetMapping("/minigames")
    public List<MinigameSummary> minigames() { return graphs.minigames(); }

    @GetMapping("/minigames/{pathId}")
    public MinigameDetail minigame(@PathVariable long pathId) { return graphs.minigame(pathId); }
}
