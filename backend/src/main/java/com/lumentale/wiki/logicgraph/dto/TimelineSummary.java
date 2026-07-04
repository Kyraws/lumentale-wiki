package com.lumentale.wiki.logicgraph.dto;

/** One cutscene timeline director in the list view ({@code timeline_director}). */
public record TimelineSummary(
    long directorPathId,
    String timelineName,
    String gameobject,
    String bundle,
    Integer nTracks,
    Integer nClips,
    boolean crossbundle
) {}
