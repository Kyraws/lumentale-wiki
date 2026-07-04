package com.lumentale.wiki.logicgraph.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A whole AI behavior tree — one jsonb-document read (nodes/edges), matching the
 * design's whole-graph-per-page pattern.
 */
public record BehaviorTreeDetail(
    long pathId,
    String bundle,
    String cab,
    String objectName,
    String behaviorName,
    String behaviorDesc,
    String bdVersion,
    String kind,
    Integer taskCount,
    JsonNode flags,
    JsonNode externalBehavior,
    JsonNode nodes,
    JsonNode edges
) {}
