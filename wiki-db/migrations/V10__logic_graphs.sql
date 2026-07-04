-- ============================================================================
-- Module 10 (NEW): logic-as-data graphs — boss battle graphs, AI behavior
-- trees, cutscene timelines, minigames. HYBRID storage: each whole graph is a
-- jsonb document (the wiki renders one graph per page = one row read), with
-- small rollup tables only for the cross-cutting links worth a join (graph
-- strong-skill → move; minigame prize → item). path_ids are signed int64.
-- Source: logic-graphs/{battle_graphs,behavior_trees,timelines,minigames}.json
-- (+ timelines_crossbundle_resolved.json). See LOGIC.md.
-- ============================================================================

-- ===== W2 — boss battle / scripted-encounter graphs =========================
-- boss_guid FK in V13. 0..1 graph per boss (14 bosses use default AI).
--   nodes jsonb: [{path_id, battle_node_type, node(label), next?, true?,
--                  ai_skill_rotation?, skill?, skill_guid?, target_form?,
--                  target_formula?, HP?, Operator?, Percentage?, ...}]
--   edges jsonb: [{from, to, kind}]  (kind: 'next' | 'true')
CREATE TABLE boss_battle_graph (
    boss_guid     uuid PRIMARY KEY,
    graph_name    text,
    graph_path_id bigint,
    asset_guid    text,                    -- 32-hex Addressables GUID
    graph_bundle  text,
    node_count    int,
    nodes         jsonb NOT NULL DEFAULT '[]',
    edges         jsonb NOT NULL DEFAULT '[]',
    note          text                     -- why no resolved graph (default-AI, stripped, …)
);

-- Rollup: strong-skill telegraphs in a graph → move (the one cross-cut worth a
-- join: "which bosses telegraph skill X, at what target"). move_guid FK in V13.
CREATE TABLE boss_graph_skill (
    id             bigserial PRIMARY KEY,
    boss_guid      uuid NOT NULL REFERENCES boss_battle_graph (boss_guid),
    move_guid      uuid NOT NULL,
    target_form    text,
    target_formula text                    -- 'species:Nuclheart;side:1'
);
CREATE INDEX boss_graph_skill_boss_idx ON boss_graph_skill (boss_guid);
CREATE INDEX boss_graph_skill_move_idx ON boss_graph_skill (move_guid);

-- ===== W3 — BehaviorDesigner AI behavior trees ==============================
-- Whole tree as jsonb (small: 25 trees / 269 nodes total).
--   nodes jsonb: [{id, name, type, label, category, instant, pos, params, root_kind}]
--   edges jsonb: [{from, to, kind}]
CREATE TABLE behavior_tree (
    path_id           bigint PRIMARY KEY,
    bundle            text,
    cab               text,
    object_name       text,
    behavior_name     text,
    behavior_desc     text,
    bd_version        text,
    kind              text,
    task_count        int,
    flags             jsonb NOT NULL DEFAULT '{}',
    external_behavior jsonb,
    nodes             jsonb NOT NULL DEFAULT '[]',
    edges             jsonb NOT NULL DEFAULT '[]'
);
CREATE INDEX behavior_tree_bundle_idx ON behavior_tree (bundle);

-- ===== W4a — cutscene timelines (PlayableDirector) ==========================
-- The track tree nests recursively (groups → children → clips), so the whole
-- tracks structure is one jsonb document per director — far cleaner than a
-- relational track/clip split.
--   tracks jsonb: [{track_type, path_id, muted, locked, clips:[{display_name,
--     start, duration, clip_in, time_scale, ease_in, ease_out, asset_type, ...}],
--     children:[...]}]
CREATE TABLE timeline_director (
    director_path_id  bigint PRIMARY KEY,
    bundle            text,
    gameobject        text,
    playable_asset_id bigint,
    timeline_name     text,
    wrap_mode         int,
    initial_state     int,
    update_mode       int,
    n_scene_bindings  int,
    n_tracks          int,
    n_clips           int,
    crossbundle       boolean NOT NULL DEFAULT false,
    src_bundle        text,
    bundle_found      text,
    tracks            jsonb NOT NULL DEFAULT '[]'
);
CREATE INDEX timeline_director_bundle_idx ON timeline_director (bundle);

-- ===== W4b — minigames ======================================================
CREATE TABLE minigame_instance (
    path_id         bigint PRIMARY KEY,
    class_name      text NOT NULL,
    bundle          text,
    gameobject_name text,
    fields          jsonb NOT NULL DEFAULT '{}'
);
CREATE INDEX minigame_instance_class_idx ON minigame_instance (class_name);

-- Rollup: prize tables → item (item_guid FK in V13).
CREATE TABLE minigame_prize (
    id               bigserial PRIMARY KEY,
    instance_path_id bigint NOT NULL REFERENCES minigame_instance (path_id),
    tier             text,                 -- 'AcePrize','EasyPrize','TopPrizes',...
    item_guid        uuid,
    amount           int
);
CREATE INDEX minigame_prize_inst_idx ON minigame_prize (instance_path_id);
CREATE INDEX minigame_prize_item_idx ON minigame_prize (item_guid);
