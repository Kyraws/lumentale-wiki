-- ============================================================================
-- Module 5: world — maps + physical placement layer + reachability graph.
-- uuid PKs. Polymorphic shop/battle refs are replaced by typed nullable FK
-- columns + a CHECK (real referential integrity). Source: data/maps_full.json,
-- maps_mini.json, phase2 raw/map_{graph,placements,spawns,shops,npcs}.json.
-- ============================================================================

-- --------------------------------------------------------------- game_map --
CREATE TABLE game_map (
    guid          uuid PRIMARY KEY,
    internal_name text NOT NULL,
    map_name      text,
    parent_guid   uuid,                 -- self-FK in V13
    region        text,
    region_side   int,
    is_interior   boolean,
    tile_guid     text,                 -- 32-hex Addressables GUID
    skybox_guid   text,
    raw           jsonb NOT NULL
);
CREATE INDEX game_map_region_idx ON game_map (region);
CREATE INDEX game_map_parent_idx ON game_map (parent_guid);

-- ------------------------------------------------------------ map_sibling --
CREATE TABLE map_sibling (
    map_guid     uuid NOT NULL REFERENCES game_map (guid),
    sibling_guid uuid NOT NULL,         -- FK in V13
    PRIMARY KEY (map_guid, sibling_guid)
);

-- ---------------------------------------------------------------- mini_map --
CREATE TABLE mini_map (
    guid        uuid PRIMARY KEY,
    name        text,
    region_side int,
    is_interior boolean,
    raw         jsonb NOT NULL DEFAULT '{}'
);

-- --------------------------------------------------------- map_graph_edge --
CREATE TABLE map_graph_edge (
    id            bigserial PRIMARY KEY,
    from_map_guid uuid NOT NULL,        -- FK in V13
    to_map_guid   uuid NOT NULL,        -- FK in V13
    conditions    jsonb NOT NULL DEFAULT '[]'
);
CREATE INDEX map_graph_edge_from_idx ON map_graph_edge (from_map_guid);
CREATE INDEX map_graph_edge_to_idx   ON map_graph_edge (to_map_guid);

-- ---------------------------------------------------------------- map_exit --
CREATE TABLE map_exit (
    id                bigserial PRIMARY KEY,
    source_map_guid   uuid NOT NULL,    -- FK in V13
    target_map_guid   uuid,             -- FK in V13 (nullable: unresolved)
    target_asset_guid text,             -- 32-hex destination prefab GUID
    name              text,
    exit_direction    int,
    pos_x  double precision, pos_y double precision, pos_z double precision,
    target_pos_x double precision, target_pos_y double precision, target_pos_z double precision,
    resolved_by       text
);
CREATE INDEX map_exit_source_idx ON map_exit (source_map_guid);
CREATE INDEX map_exit_target_idx ON map_exit (target_map_guid);

-- -------------------------------------------------------------- map_pickup --
CREATE TABLE map_pickup (
    id        bigserial PRIMARY KEY,
    map_guid  uuid NOT NULL,            -- FK in V13
    item_guid uuid,                     -- FK in V13 (nullable)
    name      text,
    amount    int,
    pos_x double precision, pos_y double precision, pos_z double precision
);
CREATE INDEX map_pickup_map_idx  ON map_pickup (map_guid);
CREATE INDEX map_pickup_item_idx ON map_pickup (item_guid);

-- ------------------------------------------------------------- map_spawner --
CREATE TABLE map_spawner (
    id                 bigint PRIMARY KEY,
    map_guid           uuid NOT NULL,    -- FK in V13
    name               text,
    respawn_time       real,
    spawn_limit        int,
    level_scale_offset int,
    side_scale_var     text,             -- VariableGUID (may be empty)
    pos_x double precision, pos_y double precision, pos_z double precision
);
CREATE INDEX map_spawner_map_idx ON map_spawner (map_guid);

-- --------------------------------------------------------------- map_spawn --
CREATE TABLE map_spawn (
    id          bigint PRIMARY KEY,
    spawner_id  bigint NOT NULL REFERENCES map_spawner (id),
    map_guid    uuid NOT NULL,           -- FK in V13
    form_guid   uuid NOT NULL,           -- FK in V13
    level_min   int,
    level_max   int,
    chance      double precision,
    time_band   int,
    kind        text,
    max_enemies int
);
CREATE INDEX map_spawn_map_idx     ON map_spawn (map_guid);
CREATE INDEX map_spawn_form_idx    ON map_spawn (form_guid);
CREATE INDEX map_spawn_spawner_idx ON map_spawn (spawner_id);

-- ---------------------------------------------------------------- map_shop --
CREATE TABLE map_shop (
    id          bigint PRIMARY KEY,
    map_guid    uuid NOT NULL,           -- FK in V13
    npc_name    text,
    graph_name  text,
    identifier  text,
    pos_x double precision, pos_y double precision, pos_z double precision
);
CREATE INDEX map_shop_map_idx ON map_shop (map_guid);

-- ----------------------------------------------------------- map_shop_entry -
-- Was polymorphic (ref_guid + ref_type). Now: one typed nullable FK column per
-- target kind + a CHECK that exactly one is set. FKs declared in V13.
CREATE TABLE map_shop_entry (
    id             bigint PRIMARY KEY,
    shop_id        bigint NOT NULL REFERENCES map_shop (id),
    item_guid      uuid,
    furniture_guid uuid,
    recipe_guid    uuid,
    move_guid      uuid,                 -- SkillData sale (rare)
    price_override int,                  -- 0 → use base price
    limit_amount   int,                  -- 0 → unlimited
    CONSTRAINT map_shop_entry_one_ref
      CHECK (num_nonnulls(item_guid, furniture_guid, recipe_guid, move_guid) = 1)
);
CREATE INDEX map_shop_entry_shop_idx ON map_shop_entry (shop_id);
CREATE INDEX map_shop_entry_item_idx ON map_shop_entry (item_guid);

-- -------------------------------------------------------------- map_battle --
-- kind ∈ {trainer, boss, scripted}. trainer/boss are typed FKs (V13); scripted
-- carries neither. CHECK ties the populated column to kind.
CREATE TABLE map_battle (
    id           bigint PRIMARY KEY,
    map_guid     uuid NOT NULL,          -- FK in V13
    npc_name     text,
    kind         text NOT NULL,          -- 'trainer' | 'boss' | 'scripted'
    trainer_guid uuid,
    boss_guid    uuid,
    pos_x double precision, pos_y double precision, pos_z double precision,
    CONSTRAINT map_battle_kind_ref CHECK (
        (kind = 'trainer'  AND trainer_guid IS NOT NULL AND boss_guid IS NULL) OR
        (kind = 'boss'     AND boss_guid    IS NOT NULL AND trainer_guid IS NULL) OR
        (kind = 'scripted' AND trainer_guid IS NULL     AND boss_guid IS NULL)
    )
);
CREATE INDEX map_battle_map_idx     ON map_battle (map_guid);
CREATE INDEX map_battle_trainer_idx ON map_battle (trainer_guid);
CREATE INDEX map_battle_boss_idx    ON map_battle (boss_guid);

-- --------------------------------------------------------- map_battle_form -
-- Animon of a scripted battle. form FK in V13.
CREATE TABLE map_battle_form (
    id         bigint PRIMARY KEY,
    battle_id  bigint NOT NULL REFERENCES map_battle (id),
    form_guid  uuid NOT NULL,
    level_min  int,
    level_max  int
);
CREATE INDEX map_battle_form_battle_idx ON map_battle_form (battle_id);
