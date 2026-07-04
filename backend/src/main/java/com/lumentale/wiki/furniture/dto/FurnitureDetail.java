package com.lumentale.wiki.furniture.dto;

import java.util.List;

/**
 * Full furniture page: the curated base fields + provenance ("where do I get
 * this?"). Furniture has no clean game category enum, so {@code rarityLabel}
 * carries the inline label when present and {@code rarity} the raw 1–3 code.
 *
 * Provenance is assembled from the typed cross-link columns that already exist
 * in the redesigned schema:
 *   - {@code soldAt}      — {@code map_shop_entry.furniture_guid} (shops on a map).
 *   - {@code questRewards} — {@code quest_item_reward.furniture_guid} (quest payouts).
 *
 * There are no crafting recipes whose result is a furniture guid, and no
 * furniture appears as a {@code map_pickup} or a story "Give Item" node (those
 * are item-only), so the only two provenance channels are shops and quests.
 * {@code obtainable} is the convenience OR over the two lists (false ⇒ no known
 * source — e.g. starter/DLC/reward furniture not sold or quest-granted).
 */
public record FurnitureDetail(
    String guid,
    String name,
    String nameKey,
    Integer rarity,
    String rarityLabel,
    Integer price,
    Integer size,
    Integer sizeX,
    Integer sizeY,
    Boolean carpet,
    String icon,
    boolean obtainable,
    List<SoldAt> soldAt,
    List<QuestReward> questRewards
) {
    /** One shop that stocks this furniture; price falls back to the base price. */
    public record SoldAt(String mapGuid, String mapName, String shop, String npc,
                         Integer price, Integer limitAmount) {}

    /** One quest that rewards this furniture (Italian quest name; no loc source). */
    public record QuestReward(String questGuid, String questName, String internalName,
                              Integer amount) {}
}
