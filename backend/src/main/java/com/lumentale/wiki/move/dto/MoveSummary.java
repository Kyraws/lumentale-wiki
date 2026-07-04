package com.lumentale.wiki.move.dto;

import java.util.List;

/**
 * One row of the moves list, with a count of how many forms learn it.
 * {@code type}/{@code category}/{@code target}/{@code aoe} are resolved labels
 * (the redesign stores int codes). {@code learnerGuids} is a dex-ordered preview
 * (capped) of forms that learn the move, for thumbnail strips — art resolves
 * client-side as {@code /data/forms/<guid>/menu.png}.
 */
public record MoveSummary(String guid, String name, String description,
                          Integer power, Integer accuracy, Integer cost,
                          String category, String type, String target, String aoe, int learners,
                          List<String> learnerGuids,
                          /** True for internal implementation skills shipped in the game data
                           *  (DoT/EoT ticks, charge stages, dev tests): no form learns them and
                           *  no boss, battle graph or shop references them. */
                          boolean system) {}
