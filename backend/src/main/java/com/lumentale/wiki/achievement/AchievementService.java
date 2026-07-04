package com.lumentale.wiki.achievement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumentale.wiki.achievement.AchievementRepository.LocKeys;
import com.lumentale.wiki.achievement.dto.AchievementSummary;
import com.lumentale.wiki.common.Guids;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.common.RawRecordService;
import com.lumentale.wiki.common.RawRecordService.RawTable;
import com.lumentale.wiki.error.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates achievement responses. The list comes from
 * {@link AchievementRepository}; the detail is the achievement's pruned {@code raw}
 * record via the shared {@link RawRecordService}. Guid validation happens here
 * (→ 400); a missing achievement → 404.
 */
@Service
public class AchievementService {

    private final AchievementRepository repo;
    private final RawRecordService raw;
    private final LocalizationResolver loc;

    public AchievementService(AchievementRepository repo, RawRecordService raw, LocalizationResolver loc) {
        this.repo = repo;
        this.raw = raw;
        this.loc = loc;
    }

    public List<AchievementSummary> list(String lang) {
        return repo.summaries(loc.normalize(lang));
    }

    public JsonNode detail(String guidStr) {
        return detail(guidStr, null);
    }

    /**
     * The raw pruned achievement record, with its {@code Description} (and name)
     * English-resolved. The stored jsonb keeps only the Italian source text, so the
     * detail endpoint resolves {@code desc_key}/{@code name_key} through the shared
     * {@link LocalizationResolver} (English by default, Italian source as fallback)
     * and overwrites the {@code Description} field in place.
     */
    public JsonNode detail(String guidStr, String lang) {
        UUID guid = Guids.require(guidStr);
        JsonNode record = raw.find(RawTable.ACHIEVEMENT, guid)
            .orElseThrow(() -> new NotFoundException("achievement", guidStr));

        if (record instanceof ObjectNode obj) {
            repo.locKeys(guid).ifPresent(keys -> {
                String descFallback = keys.descFallback() != null ? keys.descFallback()
                    : (obj.hasNonNull("Description") ? obj.get("Description").asText() : null);
                String desc = loc.display(lang, keys.descKey(), descFallback);
                if (desc != null) obj.put("Description", desc);

                String nameFallback = keys.nameFallback() != null ? keys.nameFallback()
                    : (obj.hasNonNull("m_Name") ? obj.get("m_Name").asText() : null);
                String name = loc.display(lang, keys.nameKey(), nameFallback);
                if (name != null) obj.put("Name", name);
            });
        }
        return record;
    }
}
