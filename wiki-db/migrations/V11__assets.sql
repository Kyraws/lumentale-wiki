-- ============================================================================
-- Module 11 (NEW): unified asset manifest + Addressables GUID resolution +
-- a generic entity→asset link.
--
-- Two-hop resolution: entity art GUID (32-hex Addressables, `text`)
--   --asset_guid--> (bundle, path_id) --asset--> file.
-- Source: assets/manifest.jsonl (asset), .guid_to_bundle.cache (asset_guid).
--
-- GUID namespaces: game-entity GUIDs are `uuid` (the entity PKs). Unity ASSET
-- GUIDs are 32-char hex (no dashes) → stored as `text` in the `*_guid` art
-- columns and resolved here. Distinct namespaces; never mixed.
-- ============================================================================

-- ------------------------------------------------------------------- asset -
-- One row per exported Unity object (~88,722). Identity = (bundle, path_id).
CREATE TABLE asset (
    id       bigserial PRIMARY KEY,
    bundle   text   NOT NULL,
    path_id  bigint NOT NULL,
    type     text   NOT NULL,             -- Mesh|Sprite|Texture2D|AnimationClip|Material|
                                          -- Shader|TerrainData|Font|Cubemap|TextAsset|VideoClip
    name     text,
    file     text,
    width    int,
    height   int,
    error    text,
    note     text,
    UNIQUE (bundle, path_id)
);
CREATE INDEX asset_type_idx   ON asset (type);
CREATE INDEX asset_name_idx   ON asset (name);
CREATE INDEX asset_bundle_idx ON asset (bundle);

-- -------------------------------------------------------------- asset_guid -
-- Addressables layer: GUID → (bundle, path_id), one-to-many (~23,217 rows).
-- asset_id resolution is the composite FK in V13.
CREATE TABLE asset_guid (
    guid     text   NOT NULL,             -- 32-hex Addressables GUID (or asset path)
    bundle   text   NOT NULL,
    path_id  bigint NOT NULL,
    PRIMARY KEY (guid, bundle, path_id)
);
CREATE INDEX asset_guid_guid_idx ON asset_guid (guid);
CREATE INDEX asset_guid_obj_idx  ON asset_guid (bundle, path_id);

-- ------------------------------------------------------------ entity_asset -
-- Generic optional link: any entity → an asset GUID, with a role. Lets an
-- entity enumerate all its art in one join, complementing the typed `*_guid`
-- columns. entity_guid is the entity's uuid PK; asset_guid is the 32-hex GUID
-- (soft join to asset_guid.guid — non-unique there, so not a hard FK).
CREATE TABLE entity_asset (
    id          bigserial PRIMARY KEY,
    entity_type text NOT NULL,            -- 'form'|'item'|'card'|'furniture'|'trainer'|...
    entity_guid uuid NOT NULL,
    role        text NOT NULL,            -- 'menu_art'|'front_sprite'|'icon'|'card_art'|'model'|...
    asset_guid  text NOT NULL
);
CREATE INDEX entity_asset_entity_idx ON entity_asset (entity_type, entity_guid);
CREATE INDEX entity_asset_guid_idx   ON entity_asset (asset_guid);
