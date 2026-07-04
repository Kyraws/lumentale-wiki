package com.lumentale.wiki.map.dto;

/** A world position; any axis may be null. Null overall when no coords exist. */
public record Pos(Double x, Double y, Double z) {}
