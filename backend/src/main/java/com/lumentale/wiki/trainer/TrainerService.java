package com.lumentale.wiki.trainer;

import com.lumentale.wiki.common.Guids;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.error.NotFoundException;
import com.lumentale.wiki.trainer.TrainerRepository.Base;
import com.lumentale.wiki.trainer.dto.TrainerDetail;
import com.lumentale.wiki.trainer.dto.TrainerSummary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Assembles trainer responses: the base identity + the team and the three
 * "where found" cross-link sections, composed into one {@link TrainerDetail}.
 * Guid validation → 400; missing trainer → 404. Controller stays pure routing.
 */
@Service
public class TrainerService {

    private final TrainerRepository repo;
    private final LocalizationResolver loc;

    public TrainerService(TrainerRepository repo, LocalizationResolver loc) {
        this.repo = repo;
        this.loc = loc;
    }

    public List<TrainerSummary> list(String lang) {
        return repo.summaries(loc.normalize(lang));
    }

    public TrainerDetail detail(String guidStr, String lang) {
        UUID guid = Guids.require(guidStr);
        String l = loc.normalize(lang);
        Base b = repo.base(guid).orElseThrow(() -> new NotFoundException("trainer", guidStr));
        return new TrainerDetail(
            b.guid(), b.name(), b.display(), b.rank(), b.levelCap(), b.money(), b.lumenClass(), b.idle(),
            repo.party(guid, l),
            repo.foundOnMaps(guid),
            repo.foundInScenes(guid),
            repo.squadrons(guid));
    }
}
