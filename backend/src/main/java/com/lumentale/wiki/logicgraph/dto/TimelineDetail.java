package com.lumentale.wiki.logicgraph.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A whole cutscene timeline — the recursive track tree as one jsonb document
 * ({@code tracks}: groups → children → clips).
 */
public record TimelineDetail(
    long directorPathId,
    String bundle,
    String gameobject,
    Long playableAssetId,
    String timelineName,
    Integer wrapMode,
    Integer initialState,
    Integer updateMode,
    Integer nSceneBindings,
    Integer nTracks,
    Integer nClips,
    boolean crossbundle,
    JsonNode tracks
) {}
