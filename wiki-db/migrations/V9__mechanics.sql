-- ============================================================================
-- Module 9 (NEW): game mechanics — the recovered formulas, tuning constants,
-- and XP curves. Powers a "Mechanics / Guides" wiki section.
-- Source: native/FORMULAS.md, native/constants.json, native/decompiled*/,
-- AniCurve curve assets. These are documentation-as-data rows.
-- ============================================================================

-- ----------------------------------------------------------------- formula -
-- Each recovered battle/progression formula as a citable row.
CREATE TABLE formula (
    key         text PRIMARY KEY,         -- 'damage', 'crit_chance', 'stat', 'hit_rate', ...
    name        text NOT NULL,            -- human title
    signature   text,                     -- 'BattleMath$$DamageFormula(power,level,atk,def,x)'
    expression  text,                     -- the recovered arithmetic (multiline)
    description text,
    confidence  text,                     -- 'verified' | 'structural' | 'partial'
    source_file text,                     -- native/decompiled/<fn>.c
    raw         jsonb NOT NULL DEFAULT '{}' -- inputs/outputs/constants used, structured
);
COMMENT ON TABLE formula IS
  'Damage, crit (base 8.70% @ stage 0), stat-stage multiplier, stat formula, hit '
  'rate, escape, ultimate charge, skill cost, catch-rate chain. See FORMULAS.md.';

-- ----------------------------------------------------------- game_constant -
-- Named tuning constants recovered from the PE .rdata (the *meaningful* ones;
-- the full raw float dump stays in native/constants.json, optionally loaded as
-- raw jsonb if exhaustive lookup is ever needed).
CREATE TABLE game_constant (
    name        text PRIMARY KEY,         -- 'crit_base_pct', 'easy_player_out_mult', ...
    value       double precision,
    kind        text,                     -- 'multiplier' | 'additive' | 'percent' | 'divisor'
    va          text,                     -- virtual address in constants.json (provenance)
    formula_key text REFERENCES formula (key),
    description text
);

-- --------------------------------------------------------------- xp_curve -
-- The level→exp curves. Polynomial branches recovered closed-form; the rest are
-- designer AnimationCurve keyframes (data) — kept as keyframes jsonb.
-- form.exp_curve_type references curve_type.
CREATE TABLE xp_curve (
    curve_type  int PRIMARY KEY,          -- AniCurve curveType switch value
    name        text,                     -- 'fast cubic-ish', 'piecewise', 'MediumSlow'...
    kind        text NOT NULL,            -- 'polynomial' | 'animation_curve'
    expression  text,                     -- closed form for polynomial kinds
    keyframes   jsonb NOT NULL DEFAULT '[]', -- AnimationCurve keyframes for data-driven kinds
    source_file text
);

-- ------------------------------------------------------------- xp_level_exp -
-- OPTIONAL precomputed lookup: exp required at each level per curve (handy for
-- the wiki to render a table without evaluating AnimationCurves client-side).
-- Populate by evaluating xp_curve (polynomial) / the keyframes (animation_curve).
CREATE TABLE xp_level_exp (
    curve_type int NOT NULL REFERENCES xp_curve (curve_type),
    level      int NOT NULL,
    exp        bigint NOT NULL,
    PRIMARY KEY (curve_type, level)
);
