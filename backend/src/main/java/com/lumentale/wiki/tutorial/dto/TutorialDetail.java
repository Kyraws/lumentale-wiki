package com.lumentale.wiki.tutorial.dto;

import java.util.List;

/**
 * Full tutorial page: the base fields + its ordered pages. Each page carries its
 * sequence {@code ord}, the localization {@code textKey}, and the hybrid-resolved
 * {@code asset} URL for its illustration (null-omitted when neither asset leg
 * resolves the page's Addressables GUID).
 */
public record TutorialDetail(
    String guid,
    String internalName,
    String title,
    String titleKey,
    Integer pageCount,
    List<Page> pages
) {
    public record Page(int ord, String text, String textKey, String asset) {}
}
