-- ============================================================================
-- Module 6: quests, story scene graphs, variables, tutorials. uuid entity PKs;
-- bigint graph path_ids. Story graphs are stored HYBRID: the whole graph as
-- jsonb on story_scene (read-whole-per-page), with small cross-ref rollup tables
-- for the joins. Polymorphic refs → typed FK columns + CHECK.
-- Source: phase2 raw/quests.json, story_scenes.json, story_links.json,
-- variables.json, tutorials.json.
-- ============================================================================

-- -------------------------------------------------------------------- quest -
CREATE TABLE quest (
    guid            uuid PRIMARY KEY,
    internal_name   text NOT NULL,
    name_raw        text,
    description_raw text,
    quest_giver     text,                  -- NPC display name (not a GUID)
    quest_type      int REFERENCES quest_type (code),
    money_reward    int,
    exp_reward      int,
    card_level      int,
    raw             jsonb NOT NULL
);

-- -------------------------------------------------------- quest_item_reward -
-- Was polymorphic. Now typed FK per target kind + CHECK exactly one (FKs V13).
CREATE TABLE quest_item_reward (
    id             bigserial PRIMARY KEY,
    quest_guid     uuid NOT NULL REFERENCES quest (guid),
    item_guid      uuid,
    furniture_guid uuid,
    amount         int,
    CONSTRAINT quest_item_reward_one_ref
      CHECK (num_nonnulls(item_guid, furniture_guid) = 1)
);
CREATE INDEX quest_item_reward_quest_idx ON quest_item_reward (quest_guid);
CREATE INDEX quest_item_reward_item_idx  ON quest_item_reward (item_guid);

-- --------------------------------------------------------------- quest_node -
CREATE TABLE quest_node (
    pathid            bigint PRIMARY KEY,
    quest_guid        uuid NOT NULL REFERENCES quest (guid),
    kind              text NOT NULL,        -- 'state' | 'branch'
    state_id          text,
    state_name        text,
    mission_label_raw text,
    objectives_key    text,
    conditions        jsonb NOT NULL DEFAULT '[]',
    objectives        jsonb NOT NULL DEFAULT '[]',
    raw               jsonb NOT NULL
);
CREATE INDEX quest_node_quest_idx ON quest_node (quest_guid);

-- --------------------------------------------------------- quest_transition -
CREATE TABLE quest_transition (
    id          bigserial PRIMARY KEY,
    from_pathid bigint NOT NULL REFERENCES quest_node (pathid),
    to_pathid   bigint NOT NULL,            -- FK in V13
    port        text
);
CREATE INDEX quest_transition_from_idx ON quest_transition (from_pathid);
CREATE INDEX quest_transition_to_idx   ON quest_transition (to_pathid);

-- ----------------------------------------------------------------- variable -
CREATE TABLE variable (
    pathid        bigint PRIMARY KEY,
    name          text NOT NULL,
    kind          text,                     -- switch | variable | sticky
    default_value int
);
CREATE INDEX variable_name_idx ON variable (name);

-- -------------------------------------------------------------- story_scene -
-- 1,214 XNode event graphs. HYBRID storage: nodes/edges/entries are the whole
-- graph as jsonb (a page renders one scene in one row read). The small rollup
-- tables below carry only what's queried ACROSS scenes.
--   nodes  jsonb: [{id, kind, speaker?, lines?, answers?, conditions?, flag?,
--                   value?, forms?, guid?, item_guid?, amount?, ...}]
--   edges  jsonb: [{from, to, label}]
CREATE TABLE story_scene (
    scene_id     text PRIMARY KEY,          -- '<region>:<graph_pathid>'
    graph_pathid bigint,
    region       text,
    name         text,
    main_num     double precision,          -- NULL = side scene
    chapter      int,
    n_dialogue   int,
    n_nodes      int,
    nodes        jsonb NOT NULL DEFAULT '[]',
    edges        jsonb NOT NULL DEFAULT '[]',
    entries      jsonb NOT NULL DEFAULT '[]'
);
CREATE INDEX story_scene_region_idx ON story_scene (region);
CREATE INDEX story_scene_main_idx   ON story_scene (chapter, main_num);

-- ---------------------------------------------------------- story_scene_flag -
-- Rollup: which variables a scene checks/sets (joins variable.name).
CREATE TABLE story_scene_flag (
    scene_id text NOT NULL REFERENCES story_scene (scene_id),
    flag     text NOT NULL,
    mode     text NOT NULL,                 -- 'set' | 'check'
    PRIMARY KEY (scene_id, flag, mode)
);
CREATE INDEX story_scene_flag_flag_idx ON story_scene_flag (flag);

-- ------------------------------------------------------- story_scene_battle -
-- Rollup: scene → trainer/boss (typed FKs in V13; CHECK exactly one).
CREATE TABLE story_scene_battle (
    id           bigserial PRIMARY KEY,
    scene_id     text NOT NULL REFERENCES story_scene (scene_id),
    trainer_guid uuid,
    boss_guid    uuid,
    CONSTRAINT story_scene_battle_one_ref
      CHECK (num_nonnulls(trainer_guid, boss_guid) = 1)
);
CREATE INDEX story_scene_battle_scene_idx   ON story_scene_battle (scene_id);
CREATE INDEX story_scene_battle_trainer_idx ON story_scene_battle (trainer_guid);
CREATE INDEX story_scene_battle_boss_idx    ON story_scene_battle (boss_guid);

-- ------------------------------------------------------ story_scene_trigger -
-- Rollup: where a scene fires (story_links.json). map_guid FK in V13 (nullable).
CREATE TABLE story_scene_trigger (
    id        bigserial PRIMARY KEY,
    scene_id  text NOT NULL REFERENCES story_scene (scene_id),
    map_guid  uuid,
    npc       text,                         -- NPC instance name (joins map NPC names)
    pos_x double precision, pos_y double precision, pos_z double precision
);
CREATE INDEX story_scene_trigger_scene_idx ON story_scene_trigger (scene_id);
CREATE INDEX story_scene_trigger_map_idx   ON story_scene_trigger (map_guid);

-- ----------------------------------------------------------------- tutorial -
CREATE TABLE tutorial (
    guid          uuid PRIMARY KEY,
    internal_name text,
    title_key     text,
    page_count    int,
    raw           jsonb NOT NULL DEFAULT '{}'
);

-- ------------------------------------------------------------ tutorial_page -
CREATE TABLE tutorial_page (
    id            bigserial PRIMARY KEY,
    tutorial_guid uuid NOT NULL REFERENCES tutorial (guid),
    ord           int NOT NULL,
    text_key      text,
    asset_guid    text                      -- 32-hex Addressables GUID
);
CREATE INDEX tutorial_page_tut_idx ON tutorial_page (tutorial_guid);
