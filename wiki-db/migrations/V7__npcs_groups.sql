-- ============================================================================
-- Module 7: NPCs & groups — trainers, bosses, camps, squadrons. uuid PKs.
-- Source: data/trainers.json, bosses.json, camps.json, squadrons.json.
-- ============================================================================

-- ----------------------------------------------------------------- trainer -
CREATE TABLE trainer (
    guid              uuid PRIMARY KEY,
    internal_name     text NOT NULL,
    name_raw          text,
    prefix            text,
    region            text,
    lumen_class       int,
    level_cap         int,
    money_drop        int,
    evolve_with_level boolean,
    scale_with_powers boolean,
    scale_levels      boolean,
    ai                jsonb NOT NULL DEFAULT '{}',
    sprite_anim_guid  text,                          -- 32-hex Addressables GUID
    idle_sprite_guid  text,
    raw               jsonb NOT NULL
);

-- ----------------------------------------------------------- trainer_party -
CREATE TABLE trainer_party (
    id              bigserial PRIMARY KEY,
    trainer_guid    uuid NOT NULL REFERENCES trainer (guid),
    ord             int NOT NULL,
    form_guid       uuid NOT NULL,                   -- FK in V13
    level           int,
    level_offset    int,
    nickname        text,
    item_guid       uuid,                            -- held item, FK in V13
    quirk_class     text,                            -- FK in V13
    is_hidden_quirk boolean NOT NULL DEFAULT false,
    raw             jsonb NOT NULL DEFAULT '{}'
);
CREATE INDEX trainer_party_trainer_idx ON trainer_party (trainer_guid);
CREATE INDEX trainer_party_form_idx    ON trainer_party (form_guid);

-- ------------------------------------------------------- trainer_inventory -
CREATE TABLE trainer_inventory (
    id           bigserial PRIMARY KEY,
    trainer_guid uuid NOT NULL REFERENCES trainer (guid),
    item_guid    uuid NOT NULL,                      -- FK in V13
    amount       int
);
CREATE INDEX trainer_inventory_trainer_idx ON trainer_inventory (trainer_guid);
CREATE INDEX trainer_inventory_item_idx    ON trainer_inventory (item_guid);

-- -------------------------------------------------------------------- boss -
CREATE TABLE boss (
    guid                    uuid PRIMARY KEY,
    internal_name           text NOT NULL,
    display                 text,
    origin_species_guid     uuid,                    -- FK in V13
    form_guid               uuid,                    -- FK in V13
    form_label              text,
    ele_type_code           int REFERENCES ele_type (code),
    emotion_code            int REFERENCES emotion_type (code),
    hidden_type_code        int REFERENCES ele_type (code),
    level                   int,
    exp_given               int,
    target_bst              int,
    sp_override             int,
    extra_health_bars       int,
    stats_override          jsonb,
    ai                      jsonb NOT NULL DEFAULT '{}',
    battle_graph_asset_guid text,                    -- 32-hex Addressables GUID
    raw                     jsonb NOT NULL
);

-- --------------------------------------------------------------- boss_skill -
CREATE TABLE boss_skill (
    id          bigserial PRIMARY KEY,
    boss_guid   uuid NOT NULL REFERENCES boss (guid),
    move_guid   uuid NOT NULL,                       -- FK in V13
    skill_level int,
    ord         int
);
CREATE INDEX boss_skill_boss_idx ON boss_skill (boss_guid);
CREATE INDEX boss_skill_move_idx ON boss_skill (move_guid);

-- -------------------------------------------------------------------- camp -
CREATE TABLE camp (
    guid               uuid PRIMARY KEY,
    name               text,
    effect_class       text,
    effect_description text,
    effect_duration    int,
    effect_increment   real,
    influence          int,
    lumen_amount       int,
    raw                jsonb NOT NULL DEFAULT '{}'
);

-- ------------------------------------------------------------- camp_target -
CREATE TABLE camp_target (
    id        bigserial PRIMARY KEY,
    camp_guid uuid NOT NULL REFERENCES camp (guid),
    form_guid uuid NOT NULL                          -- FK in V13
);
CREATE INDEX camp_target_camp_idx ON camp_target (camp_guid);
CREATE INDEX camp_target_form_idx ON camp_target (form_guid);

-- --------------------------------------------------------------- camp_task -
CREATE TABLE camp_task (
    id         bigserial PRIMARY KEY,
    camp_guid  uuid NOT NULL REFERENCES camp (guid),
    quest_guid uuid NOT NULL                         -- FK in V13
);
CREATE INDEX camp_task_camp_idx  ON camp_task (camp_guid);
CREATE INDEX camp_task_quest_idx ON camp_task (quest_guid);

-- ---------------------------------------------------------------- squadron -
CREATE TABLE squadron (
    guid           uuid PRIMARY KEY,
    internal_name  text NOT NULL,
    name_raw       text,
    display_name   text,
    rank           int,
    member_count   int,
    color          jsonb,
    camp_boss_guid uuid,                             -- FK→trainer in V13
    logo_guid      text,                             -- 32-hex Addressables GUID
    texture_guid   text,
    raw            jsonb NOT NULL DEFAULT '{}'
);

-- ------------------------------------------------------- squadron_member --
CREATE TABLE squadron_member (
    id            bigserial PRIMARY KEY,
    squadron_guid uuid NOT NULL REFERENCES squadron (guid),
    trainer_guid  uuid NOT NULL,                     -- FK in V13
    role          text NOT NULL,                     -- 'member' | 'rank'
    ord           int
);
CREATE INDEX squadron_member_squad_idx   ON squadron_member (squadron_guid);
CREATE INDEX squadron_member_trainer_idx ON squadron_member (trainer_guid);
