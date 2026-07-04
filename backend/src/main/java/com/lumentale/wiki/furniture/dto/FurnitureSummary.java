package com.lumentale.wiki.furniture.dto;

/**
 * One row of the furniture list (mirrors the v2 contract). {@code rarity} is the
 * inline {@code rarity_label} when present, else the raw rarity code as text (no
 * clean game enum exists for furniture rarity). {@code size} is the footprint
 * "WxH" from {@code size_x}/{@code size_y}; {@code carpet} flags floor coverings.
 * {@code icon} is the hybrid-resolved sprite URL. Null fields are omitted.
 *
 * Provenance flags drive list-level filtering ("where do I get this?"):
 *   - {@code sold}        — appears in at least one {@code map_shop_entry}.
 *   - {@code questReward} — granted by at least one {@code quest_item_reward}.
 * Furniture with neither has no known in-game source (starter/DLC/reward set).
 */
public record FurnitureSummary(String guid, String name, String nameKey, String rarity,
                               Integer price, String size, Boolean carpet, String icon,
                               boolean sold, boolean questReward) {}
