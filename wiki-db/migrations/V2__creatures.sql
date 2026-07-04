-- ============================================================================
-- Module 2: creatures. Variant-keyed — `form` is the creature PK; `species` is
-- the parent. PKs are native uuid. Promotes the old jsonb blobs (hidden_types,
-- weaknesses) into real relations. Source: data/forms.json, lumen_species.json,
-- readable/evolutions.json.
-- ============================================================================

-- ---------------------------------------------------------------- species --
CREATE TABLE species (
    guid        uuid PRIMARY KEY,
    name        text NOT NULL,
    dex         int,
    lost_locked boolean NOT NULL DEFAULT false,
    raw         jsonb NOT NULL DEFAULT '{}'
);
CREATE INDEX species_dex_idx ON species (dex);

-- ------------------------------------------------------------------- form --
CREATE TABLE form (
    guid            uuid PRIMARY KEY,
    species_guid    uuid NOT NULL REFERENCES species (guid),
    variant_name    text NOT NULL,
    form_variant    text,
    dex             int,
    ele_type_code   int REFERENCES ele_type (code),
    emotion_code    int REFERENCES emotion_type (code),
    stat_hp  int, stat_atk int, stat_def int,
    stat_spa int, stat_spd int, stat_spe int,
    bst             int,
    catch_rate      int,
    base_affection  int,
    sp_amount       int,
    exp_curve       text,                     -- label ('MediumSlow')
    exp_curve_type  int,                      -- AniCurve curveType (→ xp_curve, V9; FK in V13)
    exp_given_mult  real,
    battle_weight   real,
    range_height_m  numrange,
    range_weight_kg numrange,
    kind            text,
    description     text,
    can_follow      boolean NOT NULL DEFAULT false,
    lost_locked     boolean NOT NULL DEFAULT false,
    raw             jsonb NOT NULL
);
CREATE INDEX form_species_idx ON form (species_guid);
CREATE INDEX form_dex_idx     ON form (dex);
CREATE INDEX form_ele_idx     ON form (ele_type_code);
CREATE INDEX form_emo_idx     ON form (emotion_code);

-- --------------------------------------------------------- form_hidden_type -
CREATE TABLE form_hidden_type (
    form_guid     uuid NOT NULL REFERENCES form (guid),
    ele_type_code int  NOT NULL REFERENCES ele_type (code),
    PRIMARY KEY (form_guid, ele_type_code)
);

-- ------------------------------------------------------------ form_weakness -
-- The ELEMENTAL effectiveness axis, promoted from each form's Weaknesses[13].
CREATE TABLE form_weakness (
    form_guid     uuid NOT NULL REFERENCES form (guid),
    attacker_code int  NOT NULL REFERENCES ele_type (code),
    effectiveness text NOT NULL,   -- WEAKNESS | RESISTANCE | NORMAL | IMMUNITY
    PRIMARY KEY (form_guid, attacker_code)
);
CREATE INDEX form_weakness_atk_idx ON form_weakness (attacker_code);

-- -------------------------------------------------------------- form_skill --
-- Learnset (form ↔ move). move_guid FK in V13.
CREATE TABLE form_skill (
    id         bigserial PRIMARY KEY,
    form_guid  uuid NOT NULL REFERENCES form (guid),
    move_guid  uuid NOT NULL,
    method     text,
    level      int
);
CREATE INDEX form_skill_form_idx ON form_skill (form_guid);
CREATE INDEX form_skill_move_idx ON form_skill (move_guid);

-- ---------------------------------------------------------- form_evolution -
CREATE TABLE form_evolution (
    id               bigserial PRIMARY KEY,
    form_guid        uuid NOT NULL REFERENCES form (guid),
    target_form_guid uuid,                       -- FK in V13
    method           text,
    method_class     text,
    level            int,
    conditions_text  text,
    params           jsonb NOT NULL DEFAULT '{}'
);
CREATE INDEX form_evolution_form_idx   ON form_evolution (form_guid);
CREATE INDEX form_evolution_target_idx ON form_evolution (target_form_guid);

-- -------------------------------------------------------------- form_quirk --
CREATE TABLE form_quirk (
    id          bigserial PRIMARY KEY,
    form_guid   uuid NOT NULL REFERENCES form (guid),
    quirk_class text NOT NULL REFERENCES quirk (quirk_class),
    is_hidden   boolean NOT NULL DEFAULT false
);
CREATE INDEX form_quirk_form_idx ON form_quirk (form_guid);

-- -------------------------------------------------------------- form_spawn --
-- SO-derived form↔map (spawn_maps). map_guid FK in V13.
CREATE TABLE form_spawn (
    id        bigserial PRIMARY KEY,
    form_guid uuid NOT NULL REFERENCES form (guid),
    map_guid  uuid NOT NULL
);
CREATE INDEX form_spawn_form_idx ON form_spawn (form_guid);
CREATE INDEX form_spawn_map_idx  ON form_spawn (map_guid);

-- --------------------------------------------------------------- form_drop --
CREATE TABLE form_drop (
    id         bigserial PRIMARY KEY,
    form_guid  uuid NOT NULL REFERENCES form (guid),
    item_guid  uuid NOT NULL,                    -- FK in V13
    amount_min int,
    amount_max int
);
CREATE INDEX form_drop_form_idx ON form_drop (form_guid);
CREATE INDEX form_drop_item_idx ON form_drop (item_guid);
