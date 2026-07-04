package com.lumentale.wiki.move.dto;

/** A form that learns a move (the "who can learn this" list, with a thumbnail). */
public record MoveLearner(String guid, String species, String variant, Integer dex, Integer level, String menuArt) {}
