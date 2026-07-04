package com.lumentale.wiki.item.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Full item page: curated base fields + the "everything connects" cross-links —
 * the recipe that crafts it, forms that drop it, shops that sell it, maps it's
 * found on. (soldAt/foundOn read the redesigned typed {@code item_guid} columns,
 * not the old polymorphic ref; they're empty until the world tables seed.)
 */
public record ItemDetail(
    String guid,
    String name,
    String nameKey,
    String descKey,
    /** English-resolved flavour text (desc_key via loc, fallback to extracted en/it columns). */
    String description,
    String type,
    String material,
    Integer price,
    Integer maxStack,
    String icon,
    JsonNode effects,
    Recipe recipe,
    List<Drop> droppedBy,
    List<SoldAt> soldAt,
    List<FoundOn> foundOn,
    /** Recipes this item is an ingredient of — links to the crafted result item. */
    List<UsedIn> usedIn,
    /** Story scenes that hand the player this item ("Give Item" event nodes). */
    List<GivenIn> givenIn
) {
    public record Recipe(Integer successRate, String preferredActor, List<Ingredient> ingredients) {}
    public record UsedIn(String resultGuid, String resultName, Integer amount) {}
    public record GivenIn(String sceneId, String sceneName) {}
    public record Ingredient(String name, String guid, Integer amount) {}
    public record Drop(String guid, String species, String variant, Integer min, Integer max, String menuArt) {}
    public record SoldAt(String mapGuid, String mapName, String shop, String npc, Integer price) {}
    public record FoundOn(String mapGuid, String mapName, Long spots, Long total) {}
}
