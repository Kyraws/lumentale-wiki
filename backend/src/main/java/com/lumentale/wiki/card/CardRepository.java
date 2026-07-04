package com.lumentale.wiki.card;

import com.lumentale.wiki.card.dto.CardDetail.*;
import com.lumentale.wiki.card.dto.CardPoolSummary;
import com.lumentale.wiki.card.dto.CardSummary;
import com.lumentale.wiki.common.AssetResolver;
import com.lumentale.wiki.common.ReferenceIndex;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for cards: the summary list, the curated art-guid base (so the
 * service can resolve the three Addressables textures), each card's pools, the
 * depicted form, and the card-pool catalogue.
 *
 * The card's {@code ele_type_code} is an int code resolved through
 * {@link ReferenceIndex}; the art/mask/holo columns hold 32-hex Addressables
 * GUIDs resolved two-hop through {@link AssetResolver#fileForAddressable}, with
 * {@link AssetResolver#art} as the filesystem-first fallback. The detail's raw
 * record is served by {@code RawRecordService}, not here.
 */
@Repository
public class CardRepository {

    private final JdbcTemplate jdbc;
    private final ReferenceIndex ref;
    private final AssetResolver assets;

    public CardRepository(JdbcTemplate jdbc, ReferenceIndex ref, AssetResolver assets) {
        this.jdbc = jdbc;
        this.ref = ref;
        this.assets = assets;
    }

    /** The three Addressables art guids of a card (for two-hop resolution). */
    public record ArtGuids(String art, String mask, String holo) {}

    /** List rows; pools = how many card pools the card appears in. */
    public List<CardSummary> summaries() {
        return jdbc.query(
            "SELECT c.guid, c.name_raw, c.rarity, c.ele_type_code, c.form_guid, c.art_guid, " +
            "       (c.raw->'HoloTextureTiling'->>'x')::float8 AS tile_x, " +
            "       (c.raw->'HoloTextureTiling'->>'y')::float8 AS tile_y, " +
            "       (SELECT count(*) FROM card_pool_entry e WHERE e.card_guid = c.guid) AS pools " +
            "FROM card c ORDER BY c.name_raw",
            (rs, i) -> {
                UUID guid = (UUID) rs.getObject("guid");
                UUID form = (UUID) rs.getObject("form_guid");
                return new CardSummary(
                    guid.toString(), rs.getString("name_raw"), rs.getString("rarity"),
                    ref.ele((Integer) rs.getObject("ele_type_code")),
                    form == null ? null : form.toString(),
                    cardArt(guid, rs.getString("art_guid")),
                    (int) rs.getLong("pools"),
                    // the in-game holo pipeline's per-card foil + artwork mask
                    assets.art("card", guid, "card_holo"),
                    assets.art("card", guid, "card_mask"),
                    (Double) rs.getObject("tile_x"), (Double) rs.getObject("tile_y"));
            });
    }

    /** The card's art guids, or empty if no such card. */
    public Optional<ArtGuids> artGuids(UUID guid) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT art_guid, mask_guid, holo_guid FROM card WHERE guid = ?",
                (rs, i) -> new ArtGuids(rs.getString("art_guid"), rs.getString("mask_guid"), rs.getString("holo_guid")),
                guid));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** Resolve the card art: filesystem-first, else the stored Addressables guid two-hop. */
    public String cardArt(UUID guid, String artGuid) {
        String onDisk = assets.diskUrl("card", guid, "card_art");
        if (onDisk != null) return onDisk;
        return assets.fileForAddressable(artGuid);
    }

    /** The depicted form (guid/species/variant + thumbnail), or null when form_guid unset. */
    public Form form(UUID cardGuid) {
        return jdbc.query(
            "SELECT f.guid, s.name AS species, f.variant_name " +
            "FROM card c JOIN form f ON f.guid = c.form_guid JOIN species s ON s.guid = f.species_guid " +
            "WHERE c.guid = ?",
            (rs, i) -> {
                UUID g = (UUID) rs.getObject("guid");
                return new Form(g.toString(), rs.getString("species"), rs.getString("variant_name"),
                    assets.art("form", g, "menu_art"));
            },
            cardGuid).stream().findFirst().orElse(null);
    }

    /** Pools this card belongs to (with the entry's weight/level/ord). */
    public List<InPool> pools(UUID cardGuid) {
        return jdbc.query(
            "SELECT e.pool_name, p.is_kickstarter_pool, e.weight, e.card_level, e.ord " +
            "FROM card_pool_entry e JOIN card_pool p ON p.name = e.pool_name " +
            "WHERE e.card_guid = ? ORDER BY e.pool_name, e.ord",
            (rs, i) -> {
                Object w = rs.getObject("weight");
                return new InPool(rs.getString("pool_name"), (Boolean) rs.getObject("is_kickstarter_pool"),
                    w == null ? null : ((Number) w).floatValue(),
                    (Integer) rs.getObject("card_level"), (Integer) rs.getObject("ord"));
            },
            cardGuid);
    }

    /** The card-pool catalogue, with live entry counts. */
    public List<CardPoolSummary> poolSummaries() {
        return jdbc.query(
            "SELECT p.name, p.is_kickstarter_pool, p.card_count, " +
            "       (SELECT count(*) FROM card_pool_entry e WHERE e.pool_name = p.name) AS entries " +
            "FROM card_pool p ORDER BY p.name",
            (rs, i) -> new CardPoolSummary(rs.getString("name"), (Boolean) rs.getObject("is_kickstarter_pool"),
                (Integer) rs.getObject("card_count"), (Long) rs.getObject("entries")));
    }
}
