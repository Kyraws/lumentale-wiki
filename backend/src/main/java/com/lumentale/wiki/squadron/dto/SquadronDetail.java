package com.lumentale.wiki.squadron.dto;

import java.util.List;

/**
 * Full squadron page: curated base fields + the squadron's camp boss (a trainer,
 * nullable) + its members ({@code squadron_member} → {@code trainer}, with the
 * member/rank role and ordering). The boss/member trainer joins stay empty until
 * the trainer slice seeds.
 */
public record SquadronDetail(
    String guid,
    String name,
    String rankLabel,
    Integer rank,
    Integer memberCount,
    String logo,
    String texture,
    CampBoss campBoss,
    List<Member> members
) {
    public record CampBoss(String guid, String name) {}

    /**
     * One roster entry. {@code name} is the resolved display name (proper name, or
     * a readable role label for anonymous fighters); {@code isCodename} flags the
     * generic roster fighters so the UI can de-emphasize them; {@code role} is the
     * raw {@code squadron_member.role} ('member' | 'rank').
     */
    public record Member(String trainerGuid, String name, boolean isCodename, String role, Integer ord) {}
}
