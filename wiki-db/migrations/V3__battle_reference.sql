-- ============================================================================
-- Module 3: battle reference — moves (skills), items, crafting. uuid PKs;
-- enum columns FK their per-domain lookup tables (Module 1).
-- Source: data/moves.json, data/items.json.
-- ============================================================================

-- ------------------------------------------------------------------- move --
CREATE TABLE move (
    guid           uuid PRIMARY KEY,
    name_raw       text,
    name_key       text,
    desc_key       text,
    description    text,
    ele_type_code  int REFERENCES ele_type (code),
    category_code  int REFERENCES skill_category (code),
    target_code    int REFERENCES skill_target_type (code),
    aoe_code       int REFERENCES skill_aoe_type (code),
    power          int,
    accuracy       int,
    sp_cost        int,
    cooldown       int,
    player_turn_cd int,
    is_contact     boolean,
    is_dot         boolean,
    is_eot         boolean,
    num_effects    int,
    effects        jsonb NOT NULL DEFAULT '[]',
    raw            jsonb NOT NULL
);
CREATE INDEX move_ele_idx ON move (ele_type_code);
CREATE INDEX move_cat_idx ON move (category_code);

-- ------------------------------------------------------------------- item --
CREATE TABLE item (
    guid           uuid PRIMARY KEY,
    name_raw       text,
    name_en        text,
    name_key       text,
    desc_key       text,
    description_it text,
    description_en text,
    type_code      int,                  -- raw Type 0..4 (no clean game enum)
    type_label     text,
    material_code  int REFERENCES item_material (code),
    target_type    int REFERENCES item_target_type (code),
    battle_target  int REFERENCES item_battle_target (code),
    price          int,
    max_stack      int,
    sellable       boolean,
    givable        boolean,
    is_collectible boolean,
    unbreakable    boolean,
    untossable     boolean,
    icon_guid      text,                 -- 32-hex Addressables GUID (→ asset_guid, V11)
    effects        jsonb NOT NULL DEFAULT '[]',
    raw            jsonb NOT NULL
);
CREATE INDEX item_type_idx ON item (type_code);

-- -------------------------------------------------------- crafting_recipe --
CREATE TABLE crafting_recipe (
    guid             uuid PRIMARY KEY,
    name_raw         text,
    result_item_guid uuid,                -- FK in V13
    project_type     int,
    success_rate     int,
    preferred_actor  text,
    raw              jsonb NOT NULL DEFAULT '{}'
);

-- ---------------------------------------------------- crafting_ingredient --
CREATE TABLE crafting_ingredient (
    id          bigserial PRIMARY KEY,
    recipe_guid uuid NOT NULL REFERENCES crafting_recipe (guid),
    item_guid   uuid NOT NULL,            -- FK in V13
    amount      int
);
CREATE INDEX crafting_ingredient_recipe_idx ON crafting_ingredient (recipe_guid);
CREATE INDEX crafting_ingredient_item_idx   ON crafting_ingredient (item_guid);
