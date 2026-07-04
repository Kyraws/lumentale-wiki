package com.lumentale.wiki.camp;

import com.lumentale.wiki.camp.dto.CampDetail.Target;
import com.lumentale.wiki.camp.dto.CampDetail.Task;
import com.lumentale.wiki.camp.dto.CampSummary;
import com.lumentale.wiki.common.AssetResolver;
import com.lumentale.wiki.common.LocalizationResolver;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for camps: the summary list, the curated base record, and each
 * cross-link section (target forms, unlocked tasks/quests).
 *
 * The base detail's {@code raw} record is served by {@code RawRecordService}
 * (RawTable.CAMP) from the service, not here. Target thumbnails resolve through
 * the hybrid {@link AssetResolver}; the task join reads the typed
 * {@code quest_guid} column and is empty until the quest slice seeds.
 */
@Repository
public class CampRepository {

    private final JdbcTemplate jdbc;
    private final AssetResolver assets;
    private final LocalizationResolver loc;

    public CampRepository(JdbcTemplate jdbc, AssetResolver assets, LocalizationResolver loc) {
        this.jdbc = jdbc;
        this.assets = assets;
        this.loc = loc;
    }

    /** Curated base fields of a camp (effect block + progression numbers). */
    public record Base(String guid, String name, String effectClass, String effectDescription,
                       Integer effectDuration, Double effectIncrement, Integer influence, Integer lumenAmount) {}

    public List<CampSummary> summaries() {
        return jdbc.query(
            "SELECT guid, name, effect_class, effect_increment, influence, lumen_amount FROM camp ORDER BY name",
            (rs, i) -> {
                String codename = rs.getString("name");
                CampNaming.Naming nm = CampNaming.of(codename);
                String effectClass = rs.getString("effect_class");
                return new CampSummary(
                    ((UUID) rs.getObject("guid")).toString(), codename,
                    nm.displayName(), nm.region(), nm.area(),
                    effectClass, CampNaming.effectLabel(effectClass),
                    floatToDouble(rs.getObject("effect_increment")),
                    (Integer) rs.getObject("influence"),
                    (Integer) rs.getObject("lumen_amount"));
            });
    }

    public Optional<Base> base(UUID guid) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT guid, name, effect_class, effect_description, effect_duration, " +
                "effect_increment, influence, lumen_amount FROM camp WHERE guid=?",
                (rs, i) -> new Base(guid.toString(), rs.getString("name"), rs.getString("effect_class"),
                    rs.getString("effect_description"), (Integer) rs.getObject("effect_duration"),
                    floatToDouble(rs.getObject("effect_increment")), (Integer) rs.getObject("influence"),
                    (Integer) rs.getObject("lumen_amount")),
                guid));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** A {@code real} column comes back as a {@link Float} from JDBC; widen null-safe. */
    private static Double floatToDouble(Object v) {
        return (v == null) ? null : ((Number) v).doubleValue();
    }

    /** Forms this camp can attract (with thumbnails). */
    public List<Target> targets(UUID campGuid) {
        return jdbc.query(
            "SELECT f.guid, s.name AS species, f.variant_name " +
            "FROM camp_target ct JOIN form f ON f.guid=ct.form_guid " +
            "JOIN species s ON s.guid=f.species_guid WHERE ct.camp_guid=? ORDER BY f.dex",
            (rs, i) -> {
                UUID g = (UUID) rs.getObject("guid");
                return new Target(g.toString(), rs.getString("species"), rs.getString("variant_name"),
                    assets.art("form", g, "menu_art"));
            }, campGuid);
    }

    /** Quests/tasks this camp unlocks; quest names English-resolved (QUEST loc, IT→lang). */
    public List<Task> tasks(UUID campGuid, String lang) {
        Map<String, String> questLoc = loc.sourceToLang("QUEST", lang);
        return jdbc.query(
            "SELECT q.guid, q.name_raw, q.internal_name " +
            "FROM camp_task ct JOIN quest q ON q.guid=ct.quest_guid WHERE ct.camp_guid=? ORDER BY q.name_raw",
            (rs, i) -> {
                String raw = rs.getString("name_raw");
                String name = (raw == null || raw.isBlank())
                    ? rs.getString("internal_name")
                    : questLoc.getOrDefault(raw, raw);
                return new Task(((UUID) rs.getObject("guid")).toString(), name);
            },
            campGuid);
    }
}
