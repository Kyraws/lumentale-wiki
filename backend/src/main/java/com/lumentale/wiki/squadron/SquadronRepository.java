package com.lumentale.wiki.squadron;

import com.lumentale.wiki.common.AssetResolver;
import com.lumentale.wiki.squadron.dto.SquadronDetail.CampBoss;
import com.lumentale.wiki.squadron.dto.SquadronDetail.Member;
import com.lumentale.wiki.squadron.dto.SquadronSummary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for squadrons: the summary list, the curated base record, the camp
 * boss (a trainer, nullable), and the member roster.
 *
 * The squadron name is COALESCE(name_raw, display_name, internal_name) — the
 * extracted data leaves name_raw/display_name empty for most rows, so the
 * internal_name carries it. The logo/texture are 32-hex Addressables guids
 * resolved two-hop through the hybrid {@link AssetResolver}. The camp-boss and
 * member joins read the typed {@code trainer} guids and stay empty until the
 * trainer slice seeds.
 */
@Repository
public class SquadronRepository {

    private static final String NAME =
        "COALESCE(curated_display(guid), NULLIF(name_raw,''), NULLIF(display_name,''), internal_name)";

    private final JdbcTemplate jdbc;
    private final AssetResolver assets;

    public SquadronRepository(JdbcTemplate jdbc, AssetResolver assets) {
        this.jdbc = jdbc;
        this.assets = assets;
    }

    /** Curated base fields of a squadron. */
    public record Base(String guid, String name, Integer rank, Integer memberCount,
                       String logoGuid, String textureGuid, UUID campBossGuid) {}

    public List<SquadronSummary> summaries() {
        return jdbc.query(
            "SELECT guid, " + NAME + " AS name, rank, logo_guid, " +
            "  (SELECT count(*) FROM squadron_member sm JOIN trainer t ON t.guid = sm.trainer_guid " +
            "     WHERE sm.squadron_guid = s.guid " +
            "       AND COALESCE(NULLIF(t.name_raw,''), t.internal_name) NOT LIKE '---%') AS members " +
            "FROM squadron s ORDER BY name",
            (rs, i) -> {
                Integer rank = (Integer) rs.getObject("rank");
                UUID g = (UUID) rs.getObject("guid");
                // logo: per-squadron export on disk first, then the Addressables two-hop
                String logo = assets.art("squadron", g, "logo");
                if (logo == null) logo = assets.fileForAddressable(rs.getString("logo_guid"));
                return new SquadronSummary(
                    g.toString(), rs.getString("name"),
                    rank, SquadronNaming.rankLabel(rank), rs.getLong("members"), logo);
            });
    }

    public Optional<Base> base(UUID guid) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT guid, " + NAME + " AS name, rank, member_count, logo_guid, texture_guid, camp_boss_guid " +
                "FROM squadron WHERE guid=?",
                (rs, i) -> new Base(guid.toString(), rs.getString("name"), (Integer) rs.getObject("rank"),
                    (Integer) rs.getObject("member_count"), rs.getString("logo_guid"),
                    rs.getString("texture_guid"), (UUID) rs.getObject("camp_boss_guid")),
                guid));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** /data URL for a 32-hex Addressables guid, or null. */
    public String assetUrl(String addressableGuid) {
        return assets.fileForAddressable(addressableGuid);
    }

    /** The on-disk per-squadron logo export ({@code squadrons/<guid>/logo.png}), or null. */
    public String diskLogo(UUID squadronGuid) {
        return assets.art("squadron", squadronGuid, "logo");
    }

    /** The squadron's camp boss trainer (guid/name), or null when absent/unseeded. */
    public CampBoss campBoss(UUID campBossGuid) {
        if (campBossGuid == null) return null;
        return jdbc.query(
            "SELECT guid, COALESCE(curated_display(guid), NULLIF(name_raw,''), internal_name) AS name FROM trainer WHERE guid=?",
            (rs, i) -> new CampBoss(((UUID) rs.getObject("guid")).toString(),
                SquadronNaming.of(rs.getString("name")).name()),
            campBossGuid).stream().findFirst().orElse(null);
    }

    /** The squadron's member roster (member + rank trainers), role/ord ordered. */
    public List<Member> members(UUID squadronGuid) {
        return jdbc.query(
            "SELECT sm.trainer_guid, COALESCE(curated_display(t.guid), NULLIF(t.name_raw,''), t.internal_name) AS name, " +
            "  sm.role, sm.ord FROM squadron_member sm JOIN trainer t ON t.guid=sm.trainer_guid " +
            "WHERE sm.squadron_guid=? " +
            "  AND COALESCE(NULLIF(t.name_raw,''), t.internal_name) NOT LIKE '---%' " +
            "ORDER BY sm.role, sm.ord",
            (rs, i) -> {
                SquadronNaming.Display d = SquadronNaming.of(rs.getString("name"));
                return new Member(((UUID) rs.getObject("trainer_guid")).toString(),
                    d.name(), d.isCodename(), rs.getString("role"), (Integer) rs.getObject("ord"));
            },
            squadronGuid);
    }
}
