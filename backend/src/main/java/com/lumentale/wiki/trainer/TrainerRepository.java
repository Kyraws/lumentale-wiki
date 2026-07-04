package com.lumentale.wiki.trainer;

import com.lumentale.wiki.common.AssetResolver;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.common.ReferenceIndex;
import com.lumentale.wiki.trainer.dto.PartyMember;
import com.lumentale.wiki.trainer.dto.TrainerDetail.MapRef;
import com.lumentale.wiki.trainer.dto.TrainerDetail.SceneRef;
import com.lumentale.wiki.trainer.dto.TrainerDetail.SquadronRef;
import com.lumentale.wiki.trainer.dto.TrainerSummary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for trainers on the redesigned schema: the list (parties batched in
 * ONE query to avoid N+1), the base record, the team, and the "where found"
 * cross-links (maps, story scenes, squadron).
 *
 * Ported to the redesign: party {@code emo}/{@code ele} are int codes on
 * {@code form} resolved via {@link ReferenceIndex}; art resolves through the
 * hybrid {@link AssetResolver} ({@code trainer}/{@code idle_sprite},
 * {@code form}/{@code menu_art}, {@code map}/{@code tile}); the cross-links read
 * the new TYPED {@code trainer_guid} columns ({@code map_battle},
 * {@code story_scene_battle}, {@code squadron_member}) instead of v2's polymorphic
 * {@code ref_guid}/{@code ref_type}. Those three return {@code []} until the
 * world / story / squadron slices seed. Dev placeholders ({@code internal_name
 * LIKE '%UNUSED%'}) are excluded from the list (still reachable by guid).
 */
@Repository
public class TrainerRepository {

    private final JdbcTemplate jdbc;
    private final ReferenceIndex ref;
    private final AssetResolver assets;
    private final LocalizationResolver loc;

    public TrainerRepository(JdbcTemplate jdbc, ReferenceIndex ref, AssetResolver assets, LocalizationResolver loc) {
        this.jdbc = jdbc;
        this.ref = ref;
        this.assets = assets;
        this.loc = loc;
    }

    /** Curated base fields of a trainer (name resolved, idle art resolved). */
    public record Base(String guid, String name, String display, Integer rank, Integer levelCap,
                       Integer money, Integer lumenClass, String idle) {}

    private static final String NAME_EXPR = "COALESCE(curated_display(t.guid), NULLIF(t.name_raw,''), t.internal_name)";

    private static final String PARTY_COLS =
        "tp.ord, f.guid AS form_guid, s.name AS species, f.variant_name, tp.level, " +
        "f.emotion_code, f.ele_type_code, tp.nickname, i.name_raw AS item, i.name_key AS item_key, tp.quirk_class ";
    private static final String PARTY_JOIN =
        "FROM trainer_party tp JOIN form f ON f.guid=tp.form_guid JOIN species s ON s.guid=f.species_guid " +
        "LEFT JOIN item i ON i.guid=tp.item_guid ";

    /** List rows; '%UNUSED%' dev placeholders hidden; parties attached from one batched read. */
    public List<TrainerSummary> summaries(String lang) {
        // All parties in ONE query, grouped in memory by trainer_guid (not a
        // per-trainer subquery), then attached to each trainer.
        Map<String, List<PartyMember>> parties = new HashMap<>();
        jdbc.query("SELECT tp.trainer_guid, " + PARTY_COLS + PARTY_JOIN + "ORDER BY tp.trainer_guid, tp.ord",
            (RowCallbackHandler) rs -> parties
                .computeIfAbsent(((UUID) rs.getObject("trainer_guid")).toString(), k -> new ArrayList<>())
                .add(partyMember(rs, lang)));
        return jdbc.query(
            "SELECT t.guid, " + NAME_EXPR + " AS name, NULLIF(t.name_raw,'') AS display, " +
            "t.level_cap, t.money_drop, t.idle_sprite_guid " +
            "FROM trainer t WHERE t.internal_name NOT LIKE '%UNUSED%' ORDER BY name",
            (rs, i) -> {
                UUID guid = (UUID) rs.getObject("guid");
                return new TrainerSummary(guid.toString(), rs.getString("name"), rs.getString("display"),
                    (Integer) rs.getObject("level_cap"), (Integer) rs.getObject("money_drop"),
                    assets.art("trainer", guid, "idle_sprite"),
                    parties.getOrDefault(guid.toString(), List.of()));
            });
    }

    public Optional<Base> base(UUID guid) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT t.guid, " + NAME_EXPR + " AS name, NULLIF(t.name_raw,'') AS display, " +
                "(t.raw->>'Rank')::int AS rank, t.level_cap, t.money_drop, t.lumen_class, t.idle_sprite_guid " +
                "FROM trainer t WHERE t.guid=?",
                (rs, i) -> new Base(guid.toString(), rs.getString("name"), rs.getString("display"),
                    (Integer) rs.getObject("rank"), (Integer) rs.getObject("level_cap"),
                    (Integer) rs.getObject("money_drop"), (Integer) rs.getObject("lumen_class"),
                    assets.art("trainer", guid, "idle_sprite")),
                guid));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<PartyMember> party(UUID trainerGuid, String lang) {
        return jdbc.query("SELECT " + PARTY_COLS + PARTY_JOIN + "WHERE tp.trainer_guid=? ORDER BY tp.ord",
            (rs, i) -> partyMember(rs, lang), trainerGuid);
    }

    /** Maps the trainer is placed on (typed map_battle.trainer_guid). Empty until world seed. */
    public List<MapRef> foundOnMaps(UUID trainerGuid) {
        return jdbc.query(
            "SELECT DISTINCT gm.guid, COALESCE(curated_display(gm.guid), NULLIF(gm.map_name,''), gm.internal_name) name, gm.region " +
            "FROM map_battle b JOIN game_map gm ON gm.guid=b.map_guid " +
            "WHERE b.trainer_guid=? ORDER BY name",
            (rs, i) -> {
                UUID g = (UUID) rs.getObject("guid");
                return new MapRef(g.toString(), rs.getString("name"), rs.getString("region"),
                    assets.art("map", g, "tile"));
            },
            trainerGuid);
    }

    /** Story scenes the trainer appears in (typed story_scene_battle.trainer_guid). Empty until story seed. */
    public List<SceneRef> foundInScenes(UUID trainerGuid) {
        return jdbc.query(
            "SELECT DISTINCT s.scene_id, s.name, s.region, s.chapter FROM story_scene_battle b " +
            "JOIN story_scene s ON s.scene_id=b.scene_id WHERE b.trainer_guid=? ORDER BY s.name",
            (rs, i) -> new SceneRef(rs.getString("scene_id"), rs.getString("name"),
                rs.getString("region"), (Integer) rs.getObject("chapter")),
            trainerGuid);
    }

    /** Squadron membership (typed squadron_member.trainer_guid). Empty until squadron seed. */
    public List<SquadronRef> squadrons(UUID trainerGuid) {
        return jdbc.query(
            "SELECT sq.guid, COALESCE(curated_display(sq.guid), NULLIF(sq.name_raw,''), sq.internal_name) name, sm.role " +
            "FROM squadron_member sm JOIN squadron sq ON sq.guid=sm.squadron_guid " +
            "WHERE sm.trainer_guid=? ORDER BY sm.ord, sm.role",
            (rs, i) -> new SquadronRef(((UUID) rs.getObject("guid")).toString(),
                rs.getString("name"), rs.getString("role")),
            trainerGuid);
    }

    private PartyMember partyMember(ResultSet rs, String lang) throws SQLException {
        UUID formGuid = (UUID) rs.getObject("form_guid");
        String item = loc.display(lang, rs.getString("item_key"), rs.getString("item"));
        return new PartyMember(rs.getInt("ord"), formGuid.toString(), rs.getString("species"),
            rs.getString("variant_name"), (Integer) rs.getObject("level"),
            ref.emotion((Integer) rs.getObject("emotion_code")),
            ref.ele((Integer) rs.getObject("ele_type_code")),
            rs.getString("nickname"), item,
            rs.getString("quirk_class"), assets.art("form", formGuid, "menu_art"));
    }
}
