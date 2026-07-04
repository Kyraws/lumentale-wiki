package com.lumentale.wiki.achievement;

import com.lumentale.wiki.achievement.dto.AchievementSummary;
import com.lumentale.wiki.common.AssetResolver;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.common.ReferenceIndex;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import org.springframework.dao.EmptyResultDataAccessException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for achievements: the summary list. The detail (raw pruned record)
 * is served by {@code RawRecordService}, not here.
 *
 * The name prefers {@code name_en} (English label column), falling back to
 * {@code name_raw}. {@code rarity} is an int code resolved through
 * {@link ReferenceIndex#achievementRarity(Integer)} — but the
 * {@code achievement_rarity} lookup is not always seeded, so the raw code is also
 * returned and the label may be null. The icon resolves through the hybrid
 * {@link AssetResolver}.
 */
@Repository
public class AchievementRepository {

    private final JdbcTemplate jdbc;
    private final ReferenceIndex ref;
    private final AssetResolver assets;
    private final LocalizationResolver loc;

    public AchievementRepository(JdbcTemplate jdbc, ReferenceIndex ref, AssetResolver assets,
                                 LocalizationResolver loc) {
        this.jdbc = jdbc;
        this.ref = ref;
        this.assets = assets;
        this.loc = loc;
    }

    /**
     * List rows; names English-resolved (achi_name_&lt;guid&gt; via name_key, then the
     * {@code name_en} column, then the Italian {@code name_raw}).
     */
    public List<AchievementSummary> summaries(String lang) {
        return jdbc.query(
            "SELECT guid, name_raw, name_en, name_key, rarity, steps FROM achievement",
            (rs, i) -> {
                UUID guid = (UUID) rs.getObject("guid");
                Integer rarityCode = (Integer) rs.getObject("rarity");
                String fallback = rs.getString("name_en") != null ? rs.getString("name_en") : rs.getString("name_raw");
                String name = loc.display(lang, rs.getString("name_key"), fallback);
                return new AchievementSummary(guid.toString(), name,
                    ref.achievementRarity(rarityCode), rarityCode,
                    (Integer) rs.getObject("steps"),
                    assets.art("achievement", guid, "icon"));
            }).stream()
            .sorted(Comparator.comparing(AchievementSummary::name, Comparator.nullsLast(String::compareToIgnoreCase)))
            .toList();
    }

    /**
     * The localization keys + Italian raw fallbacks for one achievement, used by the
     * detail endpoint to English-resolve the name/description (the raw jsonb record
     * stores only the Italian source text). Empty if no such row.
     */
    public Optional<LocKeys> locKeys(UUID guid) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT name_key, name_raw, name_en, desc_key, description_it, description_en " +
                "FROM achievement WHERE guid = ?",
                (rs, i) -> new LocKeys(
                    rs.getString("name_key"),
                    rs.getString("name_en") != null ? rs.getString("name_en") : rs.getString("name_raw"),
                    rs.getString("desc_key"),
                    rs.getString("description_en") != null ? rs.getString("description_en") : rs.getString("description_it")),
                guid));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** Localization keys + Italian-source fallbacks for one achievement. */
    public record LocKeys(String nameKey, String nameFallback, String descKey, String descFallback) {}
}
