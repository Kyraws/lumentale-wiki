-- ============================================================================
-- Module 8: standalone catalogue — achievements, furniture, store products.
-- uuid PKs; achievement enums FK their lookup tables (Module 1).
-- Source: data/achievements.json, furniture.json, products.json.
-- ============================================================================

-- ------------------------------------------------------------- achievement -
CREATE TABLE achievement (
    guid            uuid PRIMARY KEY,
    internal_id     text,
    name_raw        text,
    name_en         text,
    name_key        text,
    desc_key        text,
    description_it  text,
    description_en  text,
    rarity          int REFERENCES achievement_rarity (code),
    visibility_type int REFERENCES achievement_visibility (code),
    steps           int,
    store_id        text,
    icon_guid       text,                 -- 32-hex Addressables GUID
    raw             jsonb NOT NULL DEFAULT '{}'
);

-- --------------------------------------------------------------- furniture -
CREATE TABLE furniture (
    guid         uuid PRIMARY KEY,
    name_raw     text,
    name_key     text,
    price        int,
    rarity       int,                     -- no clean game enum; label kept inline
    rarity_label text,
    size         int,
    size_x       int,
    size_y       int,
    is_carpet    boolean,
    model_guid   text,                    -- 32-hex Addressables GUID
    sprite_guid  text,
    raw          jsonb NOT NULL DEFAULT '{}'
);

-- ----------------------------------------------------------------- product -
CREATE TABLE product (
    identifier text NOT NULL,             -- 'PREORDER-DLC','DELUXE-DLC'
    platform   text NOT NULL,             -- 'steam' | 'switch'
    product_id text,
    redeem     jsonb NOT NULL DEFAULT '{}',
    PRIMARY KEY (identifier, platform)
);
