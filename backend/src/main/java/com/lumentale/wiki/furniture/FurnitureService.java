package com.lumentale.wiki.furniture;

import com.lumentale.wiki.common.Guids;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.error.NotFoundException;
import com.lumentale.wiki.furniture.dto.FurnitureDetail;
import com.lumentale.wiki.furniture.dto.FurnitureSummary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates furniture responses. The list comes from {@link FurnitureRepository};
 * the detail is the curated base + its two provenance sections (shops + quest
 * rewards) composed into one {@link FurnitureDetail}. Guid validation → 400; a
 * missing row → 404. Names English-resolved (default {@code en}, honouring {@code ?lang=}).
 */
@Service
public class FurnitureService {

    private final FurnitureRepository repo;
    private final LocalizationResolver loc;

    public FurnitureService(FurnitureRepository repo, LocalizationResolver loc) {
        this.repo = repo;
        this.loc = loc;
    }

    public List<FurnitureSummary> list(String lang) {
        return repo.summaries(loc.normalize(lang));
    }

    public FurnitureDetail detail(String guidStr, String lang) {
        UUID guid = Guids.require(guidStr);
        String l = loc.normalize(lang);
        FurnitureDetail b = repo.base(guid, l)
            .orElseThrow(() -> new NotFoundException("furniture", guidStr));

        List<FurnitureDetail.SoldAt> soldAt = repo.soldAt(guid, b.price());
        List<FurnitureDetail.QuestReward> questRewards = repo.questRewards(guid, l);
        boolean obtainable = !soldAt.isEmpty() || !questRewards.isEmpty();

        return new FurnitureDetail(
            b.guid(), b.name(), b.nameKey(), b.rarity(), b.rarityLabel(), b.price(),
            b.size(), b.sizeX(), b.sizeY(), b.carpet(), b.icon(),
            obtainable, soldAt, questRewards);
    }
}
