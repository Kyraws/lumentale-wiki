-- ============================================================================
-- Module 13: cross-module foreign keys, declared last so every target exists.
-- This is the layer that connects everything. With this redesign, the only
-- remaining non-FK links are NAME-keyed soft refs (text join) and the asset
-- GUID soft join (non-unique). The old polymorphic ref_guid columns are gone —
-- replaced by typed FK columns + CHECKs in their own modules.
-- Declare as NOT VALID + VALIDATE if seeding after DDL.
-- ============================================================================

-- creatures → battle reference / world / items
ALTER TABLE form_skill     ADD CONSTRAINT fk_form_skill_move FOREIGN KEY (move_guid)        REFERENCES move (guid);
ALTER TABLE form_evolution ADD CONSTRAINT fk_form_evo_target FOREIGN KEY (target_form_guid) REFERENCES form (guid);
ALTER TABLE form_spawn     ADD CONSTRAINT fk_form_spawn_map  FOREIGN KEY (map_guid)         REFERENCES game_map (guid);
ALTER TABLE form_drop      ADD CONSTRAINT fk_form_drop_item  FOREIGN KEY (item_guid)        REFERENCES item (guid);
ALTER TABLE form           ADD CONSTRAINT fk_form_xp_curve   FOREIGN KEY (exp_curve_type)   REFERENCES xp_curve (curve_type);

-- crafting → item
ALTER TABLE crafting_recipe     ADD CONSTRAINT fk_recipe_result   FOREIGN KEY (result_item_guid) REFERENCES item (guid);
ALTER TABLE crafting_ingredient ADD CONSTRAINT fk_ingredient_item FOREIGN KEY (item_guid)        REFERENCES item (guid);

-- cards
ALTER TABLE card            ADD CONSTRAINT fk_card_form       FOREIGN KEY (form_guid) REFERENCES form (guid);
ALTER TABLE card_pool_entry ADD CONSTRAINT fk_pool_entry_card FOREIGN KEY (card_guid) REFERENCES card (guid);

-- world (self refs, reachability, placement)
ALTER TABLE game_map        ADD CONSTRAINT fk_map_parent     FOREIGN KEY (parent_guid)    REFERENCES game_map (guid);
ALTER TABLE map_sibling     ADD CONSTRAINT fk_sibling_target FOREIGN KEY (sibling_guid)   REFERENCES game_map (guid);
ALTER TABLE map_graph_edge  ADD CONSTRAINT fk_edge_from      FOREIGN KEY (from_map_guid)  REFERENCES game_map (guid);
ALTER TABLE map_graph_edge  ADD CONSTRAINT fk_edge_to        FOREIGN KEY (to_map_guid)    REFERENCES game_map (guid);
ALTER TABLE map_exit        ADD CONSTRAINT fk_exit_source    FOREIGN KEY (source_map_guid) REFERENCES game_map (guid);
ALTER TABLE map_exit        ADD CONSTRAINT fk_exit_target    FOREIGN KEY (target_map_guid) REFERENCES game_map (guid);
ALTER TABLE map_pickup      ADD CONSTRAINT fk_pickup_map     FOREIGN KEY (map_guid)  REFERENCES game_map (guid);
ALTER TABLE map_pickup      ADD CONSTRAINT fk_pickup_item    FOREIGN KEY (item_guid) REFERENCES item (guid);
ALTER TABLE map_spawner     ADD CONSTRAINT fk_spawner_map    FOREIGN KEY (map_guid)  REFERENCES game_map (guid);
ALTER TABLE map_spawn       ADD CONSTRAINT fk_spawn_map      FOREIGN KEY (map_guid)  REFERENCES game_map (guid);
ALTER TABLE map_spawn       ADD CONSTRAINT fk_spawn_form     FOREIGN KEY (form_guid) REFERENCES form (guid);
ALTER TABLE map_shop        ADD CONSTRAINT fk_shop_map       FOREIGN KEY (map_guid)  REFERENCES game_map (guid);
ALTER TABLE map_battle      ADD CONSTRAINT fk_battle_map     FOREIGN KEY (map_guid)  REFERENCES game_map (guid);
ALTER TABLE map_battle_form ADD CONSTRAINT fk_battle_form    FOREIGN KEY (form_guid) REFERENCES form (guid);

-- world: typed refs that replaced the old polymorphic ref_guid columns
ALTER TABLE map_shop_entry ADD CONSTRAINT fk_shop_entry_item FOREIGN KEY (item_guid)      REFERENCES item (guid);
ALTER TABLE map_shop_entry ADD CONSTRAINT fk_shop_entry_furn FOREIGN KEY (furniture_guid) REFERENCES furniture (guid);
ALTER TABLE map_shop_entry ADD CONSTRAINT fk_shop_entry_rcp  FOREIGN KEY (recipe_guid)    REFERENCES crafting_recipe (guid);
ALTER TABLE map_shop_entry ADD CONSTRAINT fk_shop_entry_move FOREIGN KEY (move_guid)      REFERENCES move (guid);
ALTER TABLE map_battle     ADD CONSTRAINT fk_battle_trainer  FOREIGN KEY (trainer_guid)   REFERENCES trainer (guid);
ALTER TABLE map_battle     ADD CONSTRAINT fk_battle_boss     FOREIGN KEY (boss_guid)      REFERENCES boss (guid);

-- quests / story (typed reward refs + scene rollups)
ALTER TABLE quest_transition    ADD CONSTRAINT fk_transition_to   FOREIGN KEY (to_pathid)      REFERENCES quest_node (pathid);
ALTER TABLE quest_item_reward   ADD CONSTRAINT fk_reward_item     FOREIGN KEY (item_guid)      REFERENCES item (guid);
ALTER TABLE quest_item_reward   ADD CONSTRAINT fk_reward_furn     FOREIGN KEY (furniture_guid) REFERENCES furniture (guid);
ALTER TABLE story_scene_battle  ADD CONSTRAINT fk_ssb_trainer     FOREIGN KEY (trainer_guid)   REFERENCES trainer (guid);
ALTER TABLE story_scene_battle  ADD CONSTRAINT fk_ssb_boss        FOREIGN KEY (boss_guid)      REFERENCES boss (guid);
ALTER TABLE story_scene_trigger ADD CONSTRAINT fk_trigger_map     FOREIGN KEY (map_guid)       REFERENCES game_map (guid);

-- NPCs / groups
ALTER TABLE trainer_party     ADD CONSTRAINT fk_party_form     FOREIGN KEY (form_guid)   REFERENCES form (guid);
ALTER TABLE trainer_party     ADD CONSTRAINT fk_party_item     FOREIGN KEY (item_guid)   REFERENCES item (guid);
ALTER TABLE trainer_party     ADD CONSTRAINT fk_party_quirk    FOREIGN KEY (quirk_class) REFERENCES quirk (quirk_class);
ALTER TABLE trainer_inventory ADD CONSTRAINT fk_inv_item       FOREIGN KEY (item_guid)   REFERENCES item (guid);
ALTER TABLE boss              ADD CONSTRAINT fk_boss_species   FOREIGN KEY (origin_species_guid) REFERENCES species (guid);
ALTER TABLE boss              ADD CONSTRAINT fk_boss_form      FOREIGN KEY (form_guid)   REFERENCES form (guid);
ALTER TABLE boss_skill        ADD CONSTRAINT fk_boss_skill_move FOREIGN KEY (move_guid)  REFERENCES move (guid);
ALTER TABLE camp_target       ADD CONSTRAINT fk_camp_target_form FOREIGN KEY (form_guid)  REFERENCES form (guid);
ALTER TABLE camp_task         ADD CONSTRAINT fk_camp_task_quest  FOREIGN KEY (quest_guid) REFERENCES quest (guid);
ALTER TABLE squadron          ADD CONSTRAINT fk_squad_boss     FOREIGN KEY (camp_boss_guid) REFERENCES trainer (guid);
ALTER TABLE squadron_member   ADD CONSTRAINT fk_squad_member   FOREIGN KEY (trainer_guid)  REFERENCES trainer (guid);

-- logic graphs → bosses / moves / items
ALTER TABLE boss_battle_graph ADD CONSTRAINT fk_bbg_boss     FOREIGN KEY (boss_guid) REFERENCES boss (guid);
ALTER TABLE boss_graph_skill  ADD CONSTRAINT fk_bgs_move     FOREIGN KEY (move_guid) REFERENCES move (guid);
ALTER TABLE minigame_prize    ADD CONSTRAINT fk_prize_item   FOREIGN KEY (item_guid) REFERENCES item (guid);

-- assets
ALTER TABLE asset_guid ADD CONSTRAINT fk_asset_guid_obj
    FOREIGN KEY (bundle, path_id) REFERENCES asset (bundle, path_id);

-- ----------------------------------------------------------------------------
-- Remaining intentional non-FK links (no hard FK by design):
--   NAME-keyed soft refs (text join):
--     quest.quest_giver / story_scene_trigger.npc / map_shop.npc_name /
--       map_battle.npc_name → NPC instance names
--     story_scene_flag.flag → variable.name
--   ASSET GUID soft join (asset_guid.guid is non-unique → indexed join, not FK):
--     item.icon_guid, card.art_guid/mask_guid/holo_guid, furniture.model_guid/
--       sprite_guid, game_map.tile_guid/skybox_guid, trainer.sprite_anim_guid/
--       idle_sprite_guid, boss.battle_graph_asset_guid, squadron.logo_guid/
--       texture_guid, tutorial_page.asset_guid, map_exit.target_asset_guid,
--       entity_asset.asset_guid → asset_guid.guid
-- ----------------------------------------------------------------------------
