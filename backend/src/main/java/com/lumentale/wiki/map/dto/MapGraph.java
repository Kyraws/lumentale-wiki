package com.lumentale.wiki.map.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * The world-map connectivity graph: map nodes with directed edges to other maps.
 * Each edge carries its {@code conditions} as the raw resolved jsonb array stored
 * in {@code map_graph_edge.conditions} (story-flag gates), so the UI can show what
 * unlocks passage; an empty array means an always-open edge.
 */
public record MapGraph(List<Node> nodes) {

    public record Node(String mapGuid, String name, List<Edge> edges) {}

    public record Edge(String toGuid, String toName, JsonNode conditions) {}
}
