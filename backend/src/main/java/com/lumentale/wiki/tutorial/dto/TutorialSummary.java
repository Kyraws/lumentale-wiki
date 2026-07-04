package com.lumentale.wiki.tutorial.dto;

/**
 * One row of the tutorials list. {@code title} is the English-resolved title (via
 * the TUTORIAL loc table keyed by {@code titleKey}, falling back to the internal
 * name); {@code titleKey} is the underlying localization key (kept for the
 * frontend's own resolution); {@code pageCount} is the number of pages.
 */
public record TutorialSummary(String guid, String internalName, String title, String titleKey,
                              Integer pageCount) {}
