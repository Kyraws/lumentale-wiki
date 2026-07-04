-- ============================================================================
-- LumenTale Wiki DB — REDESIGN (from-scratch). Module 1: reference & charts.
-- Postgres. Entity PKs are native `uuid` (dashed game GUIDs); Unity asset GUIDs
-- (32-hex) stay `text`. Typed columns for queryable fields; `raw` jsonb for the
-- long tail. This module holds the lookup/reference tables every other module
-- joins to: the two combat axes (elemental + emotion) and the per-domain game
-- enum lookups (one table per domain, each with a real FK from its users —
-- NOT a single generic enum table), plus the emotion chart, difficulty scalars,
-- and the quirk catalogue.
--
-- Source: engine/data/_enums.json (game domains only), native/FORMULAS.md +
-- native/decompiled/ (charts/scalars), engine build_quirks.py.
-- ============================================================================

-- ------------------------------------------------------------------ ele_type -
-- Elemental type. _enums.json EleTypes {NONE:0,CHAKRA:1,ELECTRIC:2,AURA:3,
--   ANOMALOUS:4,GEO:5,VIRUS:6,ICE:7,ANCIENT:8,FIRE:9,DEMON:10,WATER:11,DATA:12,GRASS:13}.
CREATE TABLE ele_type (
    code int  PRIMARY KEY,
    name text NOT NULL UNIQUE
);

-- -------------------------------------------------------------- emotion_type -
-- Emotion axis. Forms/bosses carry Latin names; the native chart indexes them
-- 1..5 (0 = no emotion → ×1.0).
CREATE TABLE emotion_type (
    code int  PRIMARY KEY,        -- 1..5
    name text NOT NULL UNIQUE     -- FELICIS,FUROR,HORRENS,MESTUS,SEREUM
);

-- ------------------------------------------------ per-domain game-enum lookups
-- One small table per game enum domain (from _enums.json). Each gives a real,
-- domain-scoped FK target + a localizable label — no OTLT. Only domains that an
-- entity column actually references are materialized here.
CREATE TABLE skill_category (        -- moves: SkillCategory
    code int PRIMARY KEY, name text NOT NULL UNIQUE   -- 0 PHYSICAL,1 SPECIAL,2 STATUS
);
CREATE TABLE skill_target_type (     -- moves: SkillTargetType
    code int PRIMARY KEY, name text NOT NULL UNIQUE   -- 0 Foe,1 Ally,2 AllyOnly,3 Any,4 Self
);
CREATE TABLE skill_aoe_type (        -- moves: SkillAOEType
    code int PRIMARY KEY, name text NOT NULL UNIQUE   -- 0 SingleTarget,1 TargetAOE,2 EveryoneAOE,3 AdjacentAOE
);
CREATE TABLE item_material (         -- items: ItemMaterial
    code int PRIMARY KEY, name text NOT NULL UNIQUE   -- 0 Plastic,1 Glass,2 Organic,3 Metal
);
CREATE TABLE item_target_type (      -- items: ItemTargetType
    code int PRIMARY KEY, name text NOT NULL UNIQUE   -- 0 Single,1 Multiple,2 None
);
CREATE TABLE item_battle_target (    -- items: ItemBattleTarget
    code int PRIMARY KEY, name text NOT NULL UNIQUE   -- 0 Current,1 Party
);
CREATE TABLE quest_type (            -- quests: QuestType
    code int PRIMARY KEY, name text NOT NULL UNIQUE   -- 0 Main,1 Side,2 Task
);
CREATE TABLE achievement_rarity (    -- achievements: AchievementRarity
    code int PRIMARY KEY, name text NOT NULL UNIQUE
);
CREATE TABLE achievement_visibility (-- achievements: AchievementVisibilityType
    code int PRIMARY KEY, name text NOT NULL UNIQUE
);

-- ------------------------------------------------------------- emotion_chart -
-- Global emotion-effectiveness chart (5×5) from
-- native/decompiled/BattleMath__GetEmotionalTypeEffectivenessMultiplier.c.
-- Multiplier ∈ {1.2 super-effective, 0.8 resisted, 1.0 neutral}.
CREATE TABLE emotion_chart (
    attacker_code int  NOT NULL REFERENCES emotion_type (code),
    defender_code int  NOT NULL REFERENCES emotion_type (code),
    multiplier    real NOT NULL,
    PRIMARY KEY (attacker_code, defender_code)
);

-- The ELEMENTAL chart is data-driven per form (Weaknesses[13]) → form_weakness
-- (Module 2). A canonical element×element view is derivable; see 00-OVERVIEW §5.

-- --------------------------------------------------------- difficulty_scalar -
-- GameState$$ModifyDamageByDifficulty (FORMULAS.md). Normal = no row (×1.0).
CREATE TABLE difficulty_scalar (
    difficulty text NOT NULL,     -- 'EASY' | 'HARD'
    direction  text NOT NULL,     -- 'player_out' | 'enemy_out'
    multiplier real NOT NULL,     -- EASY player_out 1.24 / enemy_out 0.75; HARD enemy_out 1.20
    PRIMARY KEY (difficulty, direction)
);

-- ---------------------------------------------------------------------- quirk -
CREATE TABLE quirk (
    quirk_class text PRIMARY KEY,   -- 'SeaSun','Hypermight'
    name_raw    text,
    name_key    text,
    desc_key    text,
    raw         jsonb NOT NULL DEFAULT '{}'
);
