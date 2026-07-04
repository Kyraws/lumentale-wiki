package com.lumentale.wiki.tutorial;

import com.lumentale.wiki.common.Guids;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.error.NotFoundException;
import com.lumentale.wiki.tutorial.TutorialRepository.Base;
import com.lumentale.wiki.tutorial.dto.TutorialDetail;
import com.lumentale.wiki.tutorial.dto.TutorialSummary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Assembles tutorial responses: the base record plus its ordered pages, composed
 * into one {@link TutorialDetail}. Guid validation → 400; missing tutorial → 404.
 */
@Service
public class TutorialService {

    private final TutorialRepository repo;
    private final LocalizationResolver loc;

    public TutorialService(TutorialRepository repo, LocalizationResolver loc) {
        this.repo = repo;
        this.loc = loc;
    }

    public List<TutorialSummary> list(String lang) {
        return repo.summaries(loc.normalize(lang));
    }

    public TutorialDetail detail(String guidStr, String lang) {
        UUID guid = Guids.require(guidStr);
        String l = loc.normalize(lang);
        Base b = repo.base(guid, l).orElseThrow(() -> new NotFoundException("tutorial", guidStr));
        return new TutorialDetail(b.guid(), b.internalName(), b.title(), b.titleKey(), b.pageCount(),
            repo.pages(guid, l));
    }
}
