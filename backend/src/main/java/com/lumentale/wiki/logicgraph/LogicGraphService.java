package com.lumentale.wiki.logicgraph;

import com.lumentale.wiki.error.NotFoundException;
import com.lumentale.wiki.logicgraph.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the three logic-graph families. Thin — the repository does the
 * jsonb-document reads; this layer maps "no such graph" to a 404. (path_ids are
 * Unity int64s, validated by Spring's {@code long} path-var binding, so a
 * non-numeric id is a framework 400 before reaching here.)
 */
@Service
public class LogicGraphService {

    private final LogicGraphRepository repo;

    public LogicGraphService(LogicGraphRepository repo) { this.repo = repo; }

    public List<BehaviorTreeSummary> behaviorTrees() { return repo.behaviorTrees(); }

    public BehaviorTreeDetail behaviorTree(long pathId) {
        return repo.behaviorTree(pathId).orElseThrow(() -> new NotFoundException("behavior tree", String.valueOf(pathId)));
    }

    public List<TimelineSummary> timelines() { return repo.timelines(); }

    public TimelineDetail timeline(long pathId) {
        return repo.timeline(pathId).orElseThrow(() -> new NotFoundException("timeline", String.valueOf(pathId)));
    }

    public List<MinigameSummary> minigames() { return repo.minigames(); }

    public MinigameDetail minigame(long pathId) {
        return repo.minigame(pathId).orElseThrow(() -> new NotFoundException("minigame", String.valueOf(pathId)));
    }
}
