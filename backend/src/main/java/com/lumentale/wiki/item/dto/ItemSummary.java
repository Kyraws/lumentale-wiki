package com.lumentale.wiki.item.dto;

/**
 * One row of the items list. {@code material} is a resolved label (int code in the
 * schema); {@code icon} is the hybrid-resolved URL (items with no icon are hidden
 * from the list but resolve by guid).
 */
public record ItemSummary(String guid, String name, String nameKey, String type,
                          String material, Integer price, Integer maxStack, String icon,
                          /** True when a story scene's "Give Item" event hands it out. */
                          boolean storyGiven) {}
