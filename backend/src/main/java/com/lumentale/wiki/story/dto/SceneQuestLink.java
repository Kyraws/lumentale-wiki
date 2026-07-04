package com.lumentale.wiki.story.dto;

/**
 * A quest linked to a story scene by a shared flag. Lets the scene view show a
 * "Starts quest X" / "Completes quest X" badge.
 *
 * @param relation {@code starts} when the scene sets the quest's {@code _QuestStart}
 *                 flag, {@code completes} when it sets an {@code _END}/{@code _Completed}
 *                 flag, otherwise {@code related} (touches one of the quest's flags)
 */
public record SceneQuestLink(String guid, String internalName, String name,
                             String relation, String flag, String mode) {}
