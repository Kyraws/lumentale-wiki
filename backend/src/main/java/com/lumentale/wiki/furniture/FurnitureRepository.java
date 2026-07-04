package com.lumentale.wiki.furniture;

import com.lumentale.wiki.common.AssetResolver;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.furniture.dto.FurnitureDetail;
import com.lumentale.wiki.furniture.dto.FurnitureSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Data access for furniture: the summary list + the curated detail base and its
 * two provenance cross-link sections.
 *
 * Provenance reads the typed nullable {@code furniture_guid} columns added in the
 * redesign: {@code map_shop_entry} (shops) and {@code quest_item_reward} (quest
 * payouts). There are no crafting recipes producing furniture, and furniture
 * never appears as a {@code map_pickup} or story "Give Item" node, so these two
 * are the complete set of provenance channels (verified against the DB).
 *
 * Furniture carries 32-hex Addressables art GUIDs ({@code model_guid}/
 * {@code sprite_guid}) rather than entity_asset roles, so the icon resolves
 * through the hybrid {@link AssetResolver} on the {@code "furniture"} kind
 * ({@code "icon"} sprite role, filesystem-first then manifest).
 */
@Repository
public class FurnitureRepository {

    private final JdbcTemplate jdbc;
    private final AssetResolver assets;
    private final LocalizationResolver loc;

    public FurnitureRepository(JdbcTemplate jdbc, AssetResolver assets, LocalizationResolver loc) {
        this.jdbc = jdbc;
        this.assets = assets;
        this.loc = loc;
    }

    /**
     * List rows, ordered by resolved name. Names English-resolved
     * (furniture_name_&lt;guid&gt; via name_key, fallback name_raw); size is "WxH".
     * Two batched provenance lookups stamp the {@code sold}/{@code questReward}
     * flags so the list can filter by source without N round-trips.
     */
    public List<FurnitureSummary> summaries(String lang) {
        Set<String> soldGuids = new HashSet<>(jdbc.queryForList(
            "SELECT DISTINCT furniture_guid::text FROM map_shop_entry WHERE furniture_guid IS NOT NULL",
            String.class));
        Set<String> questGuids = new HashSet<>(jdbc.queryForList(
            "SELECT DISTINCT furniture_guid::text FROM quest_item_reward WHERE furniture_guid IS NOT NULL",
            String.class));

        Map<String, String> nameByItalian = loc.sourceToLang("FURNITURE_NAMES", lang);
        return jdbc.query(
            "SELECT guid, name_raw, name_key, rarity, rarity_label, price, size_x, size_y, is_carpet " +
            "FROM furniture",
            (rs, i) -> {
                UUID guid = (UUID) rs.getObject("guid");
                String g = guid.toString();
                return new FurnitureSummary(
                    g,
                    name(lang, rs.getString("name_key"), rs.getString("name_raw"), nameByItalian),
                    rs.getString("name_key"),
                    rarity(rs.getString("rarity_label"), (Integer) rs.getObject("rarity")),
                    (Integer) rs.getObject("price"),
                    size((Integer) rs.getObject("size_x"), (Integer) rs.getObject("size_y")),
                    (Boolean) rs.getObject("is_carpet"),
                    assets.art("furniture", guid, "icon"),
                    soldGuids.contains(g),
                    questGuids.contains(g));
            }).stream()
            .sorted(Comparator.comparing(FurnitureSummary::name, Comparator.nullsLast(String::compareToIgnoreCase)))
            .toList();
    }

    /** The curated base record for a single piece (no provenance), or empty if absent. */
    public Optional<FurnitureDetail> base(UUID guid, String lang) {
        Map<String, String> nameByItalian = loc.sourceToLang("FURNITURE_NAMES", lang);
        return jdbc.query(
            "SELECT guid, name_raw, name_key, rarity, rarity_label, price, size, size_x, size_y, is_carpet " +
            "FROM furniture WHERE guid = ?",
            (rs, i) -> new FurnitureDetail(
                guid.toString(),
                name(lang, rs.getString("name_key"), rs.getString("name_raw"), nameByItalian),
                rs.getString("name_key"),
                (Integer) rs.getObject("rarity"),
                blankToNull(rs.getString("rarity_label")),
                (Integer) rs.getObject("price"),
                (Integer) rs.getObject("size"),
                (Integer) rs.getObject("size_x"),
                (Integer) rs.getObject("size_y"),
                (Boolean) rs.getObject("is_carpet"),
                assets.art("furniture", guid, "icon"),
                false,                 // obtainable computed in the service after the joins
                List.of(), List.of()),
            guid).stream().findFirst();
    }

    /** Shops that stock this furniture (typed furniture_guid column); price falls back to base. */
    public List<FurnitureDetail.SoldAt> soldAt(UUID guid, Integer basePrice) {
        return jdbc.query(
            "SELECT sh.map_guid, COALESCE(curated_display(gm.guid), NULLIF(gm.map_name,''), gm.internal_name) map_name, " +
            "  sh.npc_name, sh.graph_name, e.price_override, e.limit_amount " +
            "FROM map_shop_entry e JOIN map_shop sh ON sh.id=e.shop_id " +
            "JOIN game_map gm ON gm.guid=sh.map_guid " +
            "WHERE e.furniture_guid=? ORDER BY map_name, sh.graph_name",
            (rs, i) -> {
                Integer ov = (Integer) rs.getObject("price_override");
                Integer lim = (Integer) rs.getObject("limit_amount");
                return new FurnitureDetail.SoldAt(
                    ((UUID) rs.getObject("map_guid")).toString(),
                    rs.getString("map_name"),
                    rs.getString("graph_name"),
                    rs.getString("npc_name"),
                    (ov != null && ov > 0) ? ov : basePrice,
                    (lim != null && lim > 0) ? lim : null);
            }, guid);
    }

    /**
     * Quests that reward this furniture. Quest names resolve via the QUEST loc table
     * keyed by the Italian source text (the same bridge {@code QuestService} uses),
     * falling back to the Italian {@code name_raw} when no translation exists.
     */
    public List<FurnitureDetail.QuestReward> questRewards(UUID guid, String lang) {
        Map<String, String> questNames = loc.sourceToLang("QUEST", lang);
        return jdbc.query(
            "SELECT q.guid, q.name_raw, q.internal_name, r.amount " +
            "FROM quest_item_reward r JOIN quest q ON q.guid=r.quest_guid " +
            "WHERE r.furniture_guid=? ORDER BY q.name_raw",
            (rs, i) -> {
                String raw = rs.getString("name_raw");
                return new FurnitureDetail.QuestReward(
                    ((UUID) rs.getObject("guid")).toString(),
                    raw == null ? null : questNames.getOrDefault(raw, raw),
                    rs.getString("internal_name"),
                    (Integer) rs.getObject("amount"));
            },
            guid);
    }

    /**
     * Resolve a furniture display name: prefer the per-guid {@code name_key} lookup
     * (the established {@code FURNITURE_NAMES} key path); when that misses — it does
     * for 562/871 rows whose per-guid key isn't in the loc table — fall back to the
     * Italian-source-text → target-lang map for the same table; finally the raw
     * Italian {@code name_raw}.
     */
    private String name(String lang, String nameKey, String nameRaw, Map<String, String> nameByItalian) {
        String byKey = loc.display(lang, nameKey, null);
        if (byKey != null && !byKey.isBlank()) return byKey;
        if (nameRaw != null) {
            String bySource = nameByItalian.get(nameRaw);
            if (bySource != null && !bySource.isBlank()) return bySource;
        }
        return nameRaw;
    }

    /** Inline label if present (no clean game enum), else the raw rarity code as text. */
    private static String rarity(String label, Integer code) {
        if (label != null && !label.isBlank()) return label;
        return code == null ? null : code.toString();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** Footprint "WxH" from size_x/size_y, or null when both are absent. */
    private static String size(Integer x, Integer y) {
        if (x == null && y == null) return null;
        return (x == null ? 0 : x) + "x" + (y == null ? 0 : y);
    }
}
