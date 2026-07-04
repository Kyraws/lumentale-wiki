package com.lumentale.wiki.tutorial;

import com.lumentale.wiki.common.AssetResolver;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.tutorial.dto.TutorialDetail.Page;
import com.lumentale.wiki.tutorial.dto.TutorialSummary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for tutorials: the summary list, the curated base record, and the
 * ordered page list (the intra-module {@code tutorial_page} child).
 *
 * Each page's {@code asset_guid} is a 32-hex Addressables GUID held directly on the
 * row, so it resolves through {@link AssetResolver#fileForAddressable(String)} (the
 * two-hop manifest core) rather than the {@code entity_asset} role lookup.
 */
@Repository
public class TutorialRepository {

    private final JdbcTemplate jdbc;
    private final AssetResolver assets;
    private final LocalizationResolver loc;

    public TutorialRepository(JdbcTemplate jdbc, AssetResolver assets, LocalizationResolver loc) {
        this.jdbc = jdbc;
        this.assets = assets;
        this.loc = loc;
    }

    /** Curated base fields of a tutorial. */
    public record Base(String guid, String internalName, String title, String titleKey, Integer pageCount) {}

    /** List rows; {@code title} English-resolved via the TUTORIAL loc table (key = title_key). */
    public List<TutorialSummary> summaries(String lang) {
        return jdbc.query(
            "SELECT guid, internal_name, title_key, page_count FROM tutorial",
            (rs, i) -> new TutorialSummary(((UUID) rs.getObject("guid")).toString(),
                rs.getString("internal_name"),
                loc.display(lang, rs.getString("title_key"), rs.getString("internal_name")),
                rs.getString("title_key"),
                (Integer) rs.getObject("page_count")))
            .stream()
            .sorted(Comparator.comparing(TutorialSummary::internalName, Comparator.nullsLast(String::compareToIgnoreCase)))
            .toList();
    }

    public Optional<Base> base(UUID guid, String lang) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT guid, internal_name, title_key, page_count FROM tutorial WHERE guid=?",
                (rs, i) -> new Base(guid.toString(), rs.getString("internal_name"),
                    loc.display(lang, rs.getString("title_key"), rs.getString("internal_name")),
                    rs.getString("title_key"), (Integer) rs.getObject("page_count")),
                guid));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * The tutorial's pages, ordered by ord; {@code text} English-resolved via the
     * TUTORIAL loc table (key = text_key); each page's asset resolves two-hop.
     */
    public List<Page> pages(UUID tutorialGuid, String lang) {
        return jdbc.query(
            "SELECT ord, text_key, asset_guid FROM tutorial_page WHERE tutorial_guid=? ORDER BY ord",
            (rs, i) -> new Page(rs.getInt("ord"),
                loc.display(lang, rs.getString("text_key"), null),
                rs.getString("text_key"),
                assets.fileForAddressable(rs.getString("asset_guid"))),
            tutorialGuid);
    }
}
