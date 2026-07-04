package com.lumentale.wiki.common;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * One home for every localization lookup against the Module-12 tables
 * ({@code loc_key}, {@code localization}). Entity {@code *_key} columns
 * (name_key, desc_key, title_key, …) join {@code loc_key(table_name, m_key)} →
 * {@code string_id} → {@code localization(lang, table_name, string_id)}.
 *
 * Every method is {@code @Cacheable}: the localization tables are static
 * post-seed, so each (table, lang) pair is computed at most once. Result maps are
 * treated as read-only by callers. Source text is Italian; unresolved strings
 * fall back to the Italian source upstream.
 */
@Service
public class LocalizationResolver {

    public static final Set<String> LANGS = Set.of("en","it","de","es","fr","pt","ja","zh");
    public static final String DEFAULT_LANG = "en";

    private final JdbcTemplate jdbc;

    // Per-language {m_key → text} maps, memoized in a field. The DB is static
    // post-seed, so each language's ~14k-row map is loaded ONCE. Critically, this
    // is a real field cache (not @Cacheable): display()/all() call it internally,
    // and @Cacheable is bypassed on self-invocation — which previously re-ran the
    // 16k-row query for every entity row (furniture list took 20s).
    private final java.util.concurrent.ConcurrentHashMap<String, Map<String,String>> allCache =
        new java.util.concurrent.ConcurrentHashMap<>();

    public LocalizationResolver(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean isSupported(String lang) { return LANGS.contains(lang); }

    /** Normalize a {@code ?lang=} param to a supported language (default {@code en}). */
    public String normalize(String lang) {
        return (lang != null && isSupported(lang)) ? lang : DEFAULT_LANG;
    }

    /**
     * Resolve a single {@code m_key} to display text in {@code lang}, falling back
     * to {@code raw} (the Italian source) when no translation exists. {@code key}
     * may be null (e.g. a table without a {@code *_key} column) — then a derived
     * key won't help and we return {@code raw}. Backed by the cached {@link #all}
     * map, so this is an O(1) lookup per call after the first.
     */
    public String display(String lang, String key, String raw) {
        if (key != null) {
            String t = all(normalize(lang)).get(key);
            if (t != null && !t.isBlank()) return t;
        }
        return raw;
    }

    /** Flat {m_key → text} for an entire language. Memoized in a field (see allCache). */
    public Map<String,String> all(String lang) {
        if (!isSupported(lang)) return Map.of();
        return allCache.computeIfAbsent(lang, this::loadAll);
    }

    private Map<String,String> loadAll(String lang) {
        Map<String,String> out = new HashMap<>(16000);
        jdbc.query(
            "SELECT lk.m_key, l.text FROM loc_key lk " +
            "JOIN localization l ON l.table_name = lk.table_name AND l.string_id = lk.string_id " +
            "WHERE l.lang = ?",
            (RowCallbackHandler) rs -> out.put(rs.getString("m_key"), rs.getString("text")),
            lang);
        return out;
    }

    /** {m_key → text} for one loc table in one language. */
    @Cacheable("loc.table")
    public Map<String,String> table(String table, String lang) {
        Map<String,String> out = new HashMap<>();
        jdbc.query(
            "SELECT lk.m_key, l.text FROM loc_key lk " +
            "JOIN localization l ON l.table_name = lk.table_name AND l.string_id = lk.string_id " +
            "WHERE lk.table_name = ? AND l.lang = ?",
            (RowCallbackHandler) rs -> out.put(rs.getString("m_key"), rs.getString("text")),
            table, lang);
        return out;
    }

    /**
     * {Italian source text → target-lang text} for a table keyed by source text
     * rather than a code (quest names/descriptions bridge IT → string_id → lang).
     */
    @Cacheable("loc.sourceToLang")
    public Map<String,String> sourceToLang(String table, String lang) {
        Map<String,String> out = new HashMap<>();
        jdbc.query(
            "SELECT l_it.text AS src, l_dst.text AS dst FROM localization l_it " +
            "JOIN localization l_dst ON l_dst.table_name = l_it.table_name " +
            "  AND l_dst.string_id = l_it.string_id AND l_dst.lang = ? " +
            "WHERE l_it.lang = 'it' AND l_it.table_name = ?",
            (RowCallbackHandler) rs -> out.put(rs.getString("src"), rs.getString("dst")),
            lang, table);
        return out;
    }
}
