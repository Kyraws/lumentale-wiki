package com.lumentale.wiki.logicgraph.dto;

/** One AI behavior tree in the list view ({@code behavior_tree}). */
public record BehaviorTreeSummary(
    long pathId,
    String behaviorName,
    String objectName,
    String bundle,
    String kind,
    Integer taskCount
) {}
