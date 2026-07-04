package com.lumentale.wiki.trainer.dto;

import java.util.List;

/**
 * One row of the trainers list, with the team shown inline. {@code name} is the
 * display label (COALESCE of name_raw / internal_name); {@code idle} is the
 * hybrid-resolved idle sprite. Dev placeholders ({@code internal_name LIKE
 * '%UNUSED%'}) are excluded from the list but still resolve by guid.
 */
public record TrainerSummary(String guid, String name, String display, Integer levelCap,
                             Integer money, String idle, List<PartyMember> party) {}
