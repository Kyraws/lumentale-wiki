-- ============================================================================
-- Module 4: trading cards. uuid PKs. Source: data/cards.json, card_pools.json.
-- ============================================================================

-- ------------------------------------------------------------------- card --
-- form_guid nullable (a few cards have no form). art/mask/holo are 32-hex
-- Addressables GUIDs (→ asset_guid, V11). form FK in V13.
CREATE TABLE card (
    guid               uuid PRIMARY KEY,
    name_raw           text,
    form_guid          uuid,
    rarity             text,
    ele_type_code      int REFERENCES ele_type (code),
    emotion_attribute  int,
    artist_name        text,
    can_be_kickstarter boolean,
    art_guid           text,
    mask_guid          text,
    holo_guid          text,
    raw                jsonb NOT NULL DEFAULT '{}'
);
CREATE INDEX card_form_idx ON card (form_guid);

-- -------------------------------------------------------------- card_pool --
-- guid is empty in source for all 5 pools → `name` is the natural key.
CREATE TABLE card_pool (
    name                text PRIMARY KEY,
    is_kickstarter_pool boolean,
    card_count          int,
    raw                 jsonb NOT NULL DEFAULT '{}'
);

-- -------------------------------------------------------- card_pool_entry --
CREATE TABLE card_pool_entry (
    id         bigserial PRIMARY KEY,
    pool_name  text NOT NULL REFERENCES card_pool (name),
    card_guid  uuid NOT NULL,                 -- FK in V13
    weight     real,
    card_level int,
    ord        int
);
CREATE INDEX card_pool_entry_pool_idx ON card_pool_entry (pool_name);
CREATE INDEX card_pool_entry_card_idx ON card_pool_entry (card_guid);
