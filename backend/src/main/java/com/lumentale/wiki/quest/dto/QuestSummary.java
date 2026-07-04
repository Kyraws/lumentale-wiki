package com.lumentale.wiki.quest.dto;

/**
 * One row of the quests list. {@code name} is the internal id (name_raw); when a
 * localized title resolves it is carried in {@code title} (null-omitted otherwise).
 * {@code giver} is the NPC display name, {@code type} the quest_type int code, and
 * {@code nodes} the count of quest_node rows.
 *
 * <p>v2's {@code city}/{@code track} are deliberately OMITTED — they were a
 * StoryGeography concern, not a property of the quest record.
 */
public record QuestSummary(String guid, String name, String title, String giver, Integer type, int nodes) {}
