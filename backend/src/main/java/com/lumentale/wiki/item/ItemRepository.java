package com.lumentale.wiki.item;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumentale.wiki.common.AssetResolver;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.common.ReferenceIndex;
import com.lumentale.wiki.item.dto.ItemDetail.*;
import com.lumentale.wiki.item.dto.ItemSummary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for items: the summary list, the curated base record, and each
 * cross-link section (recipe, drops, shops, pickups).
 *
 * Ported to the redesigned schema: {@code material} is an int code resolved via
 * {@link ReferenceIndex}; the icon resolves through the hybrid
 * {@link AssetResolver}; and {@code soldAt} reads the new typed
 * {@code map_shop_entry.item_guid} column instead of v2's polymorphic
 * {@code ref_guid}/{@code ref_type}.
 */
@Repository
public class ItemRepository {

    private final JdbcTemplate jdbc;
    private final ReferenceIndex ref;
    private final AssetResolver assets;
    private final ObjectMapper mapper;
    private final LocalizationResolver loc;

    public ItemRepository(JdbcTemplate jdbc, ReferenceIndex ref, AssetResolver assets,
                          ObjectMapper mapper, LocalizationResolver loc) {
        this.jdbc = jdbc;
        this.ref = ref;
        this.assets = assets;
        this.mapper = mapper;
        this.loc = loc;
    }

    /** Curated base fields of an item (material/icon resolved, effects parsed). */
    public record Base(String guid, String name, String nameKey, String descKey, String description,
                       String type, String material, Integer price, Integer maxStack, String icon,
                       JsonNode effects) {}

    /**
     * List rows; names English-resolved (item_name_&lt;guid&gt; via name_key, fallback
     * name_raw). Items whose icon resolves to nothing are hidden (still reachable by guid).
     */
    public List<ItemSummary> summaries(String lang) {
        // items handed out by a story "Give Item" event (guids backfilled in the seed)
        java.util.Set<String> storyGiven = new java.util.HashSet<>(jdbc.queryForList(
            "SELECT DISTINCT n->>'item_guid' FROM story_scene s, jsonb_array_elements(s.nodes) n " +
            "WHERE jsonb_typeof(s.nodes)='array' AND n->>'kind'='item' AND n->>'item_guid' IS NOT NULL",
            String.class));
        return jdbc.query(
            "SELECT guid, name_raw, name_key, type_label, material_code, price, max_stack " +
            "FROM item",
            (rs, i) -> {
                UUID guid = (UUID) rs.getObject("guid");
                return new ItemSummary(guid.toString(),
                    loc.display(lang, rs.getString("name_key"), rs.getString("name_raw")),
                    rs.getString("name_key"),
                    rs.getString("type_label"), ref.itemMaterial((Integer) rs.getObject("material_code")),
                    (Integer) rs.getObject("price"), (Integer) rs.getObject("max_stack"),
                    assets.art("item", guid, "icon"),
                    storyGiven.contains(guid.toString()));
            }).stream().filter(s -> s.icon() != null)
            .sorted(Comparator.comparing(ItemSummary::name, Comparator.nullsLast(String::compareToIgnoreCase)))
            .toList();
    }

    public Optional<Base> base(UUID guid, String lang) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT guid,name_raw,name_key,desc_key,description_en,description_it," +
                "type_label,material_code,price,max_stack," +
                "effects::text AS effects FROM item WHERE guid=?",
                (rs, i) -> {
                    String descRaw = rs.getString("description_en") != null
                        ? rs.getString("description_en") : rs.getString("description_it");
                    return new Base(guid.toString(),
                        loc.display(lang, rs.getString("name_key"), rs.getString("name_raw")),
                        rs.getString("name_key"),
                        rs.getString("desc_key"),
                        loc.display(lang, rs.getString("desc_key"), descRaw),
                        rs.getString("type_label"),
                        ref.itemMaterial((Integer) rs.getObject("material_code")),
                        (Integer) rs.getObject("price"), (Integer) rs.getObject("max_stack"),
                        assets.art("item", guid, "icon"), parseEffects(rs.getString("effects")));
                },
                guid));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** The recipe whose result is this item (with ingredient names), or null. */
    public Recipe recipe(UUID itemGuid, String lang) {
        return jdbc.query(
            "SELECT guid, success_rate, preferred_actor FROM crafting_recipe WHERE result_item_guid=?",
            (rs, i) -> new Recipe((Integer) rs.getObject("success_rate"), rs.getString("preferred_actor"),
                ingredients((UUID) rs.getObject("guid"), lang)),
            itemGuid).stream().findFirst().orElse(null);
    }

    private List<Ingredient> ingredients(UUID recipeGuid, String lang) {
        return jdbc.query(
            "SELECT ci.amount, i.name_raw, i.name_key, i.guid FROM crafting_ingredient ci " +
            "LEFT JOIN item i ON i.guid=ci.item_guid WHERE ci.recipe_guid=? ORDER BY ci.id",
            (rs, i) -> {
                Object g = rs.getObject("guid");
                return new Ingredient(loc.display(lang, rs.getString("name_key"), rs.getString("name_raw")),
                    g == null ? null : g.toString(),
                    (Integer) rs.getObject("amount"));
            }, recipeGuid);
    }

    /** Story scenes whose flow gives this item ("Give Item" nodes, guid backfilled). */
    public List<GivenIn> givenInStory(UUID itemGuid) {
        return jdbc.query(
            "SELECT scene_id, name FROM story_scene WHERE nodes @> ?::jsonb ORDER BY name",
            (rs, i) -> new GivenIn(rs.getString("scene_id"), rs.getString("name")),
            "[{\"kind\":\"item\",\"item_guid\":\"" + itemGuid + "\"}]");
    }

    /** Recipes this item is an ingredient of, linking to the crafted result item. */
    public List<UsedIn> usedIn(UUID itemGuid, String lang) {
        return jdbc.query(
            "SELECT cr.result_item_guid, i.name_raw, i.name_key, ci.amount " +
            "FROM crafting_ingredient ci " +
            "JOIN crafting_recipe cr ON cr.guid = ci.recipe_guid " +
            "LEFT JOIN item i ON i.guid = cr.result_item_guid " +
            "WHERE ci.item_guid=? ORDER BY i.name_raw",
            (rs, i) -> {
                Object g = rs.getObject("result_item_guid");
                return new UsedIn(g == null ? null : g.toString(),
                    loc.display(lang, rs.getString("name_key"), rs.getString("name_raw")),
                    (Integer) rs.getObject("amount"));
            }, itemGuid);
    }

    /** Forms that drop this item. */
    public List<Drop> droppedBy(UUID itemGuid) {
        return jdbc.query(
            "SELECT f.guid, s.name AS species, f.variant_name, fd.amount_min, fd.amount_max " +
            "FROM form_drop fd JOIN form f ON f.guid=fd.form_guid JOIN species s ON s.guid=f.species_guid " +
            "WHERE fd.item_guid=? ORDER BY f.dex",
            (rs, i) -> {
                UUID g = (UUID) rs.getObject("guid");
                return new Drop(g.toString(), rs.getString("species"), rs.getString("variant_name"),
                    (Integer) rs.getObject("amount_min"), (Integer) rs.getObject("amount_max"),
                    assets.art("form", g, "menu_art"));
            }, itemGuid);
    }

    /** Shops that sell this item (typed item_guid column); price falls back to base. */
    public List<SoldAt> soldAt(UUID itemGuid, Integer basePrice) {
        return jdbc.query(
            "SELECT sh.map_guid, COALESCE(curated_display(gm.guid), NULLIF(gm.map_name,''), gm.internal_name) map_name, " +
            "  sh.npc_name, sh.graph_name, e.price_override " +
            "FROM map_shop_entry e JOIN map_shop sh ON sh.id=e.shop_id JOIN game_map gm ON gm.guid=sh.map_guid " +
            "WHERE e.item_guid=? ORDER BY map_name",
            (rs, i) -> {
                Integer ov = (Integer) rs.getObject("price_override");
                return new SoldAt(((UUID) rs.getObject("map_guid")).toString(), rs.getString("map_name"),
                    rs.getString("graph_name"), rs.getString("npc_name"),
                    (ov != null && ov > 0) ? ov : basePrice);
            }, itemGuid);
    }

    /** Maps where this item is a world pickup, aggregated. */
    public List<FoundOn> foundOn(UUID itemGuid) {
        return jdbc.query(
            "SELECT p.map_guid, COALESCE(curated_display(gm.guid), NULLIF(gm.map_name,''), gm.internal_name) map_name, " +
            "  count(*) spots, sum(p.amount) total FROM map_pickup p JOIN game_map gm ON gm.guid=p.map_guid " +
            "WHERE p.item_guid=? GROUP BY p.map_guid, gm.map_name, gm.internal_name ORDER BY map_name",
            (rs, i) -> new FoundOn(((UUID) rs.getObject("map_guid")).toString(), rs.getString("map_name"),
                (Long) rs.getObject("spots"), (Long) rs.getObject("total")),
            itemGuid);
    }

    /** Item effects jsonb → JsonNode; an empty array if absent or malformed. */
    private JsonNode parseEffects(String json) {
        if (json == null || json.isBlank()) return mapper.createArrayNode();
        try { return mapper.readTree(json); }
        catch (Exception e) { return mapper.createArrayNode(); }
    }
}
