-- ============================================================================
-- Module 12: localization — the 8-language string tables. Source text is
-- Italian; unresolved strings fall back to the Italian source.
-- Langs: it (source), en, de, es, fr, pt, ja, zh.
-- Source: phase2 raw/loc/, engine loc_en.json, data/_enums.json loc tables.
-- ============================================================================

-- Every translatable string, keyed by (lang, table, string_id). Entity *_key
-- columns (name_key, desc_key, title_key, objectives_key, page text_key, …)
-- join here as string_id within their loc table.
CREATE TABLE localization (
    lang       text NOT NULL,             -- 'it','en','de','es','fr','pt','ja','zh'
    table_name text NOT NULL,             -- loc table the string belongs to
    string_id  text NOT NULL,
    text       text,
    PRIMARY KEY (lang, table_name, string_id)
);
CREATE INDEX localization_key_idx ON localization (table_name, string_id);

-- Maps an entity's m_key (the loc reference it stores) to a string_id, when the
-- two differ. (table_name, m_key) → string_id.
CREATE TABLE loc_key (
    table_name text NOT NULL,
    m_key      text NOT NULL,
    string_id  text NOT NULL,
    PRIMARY KEY (table_name, m_key)
);
