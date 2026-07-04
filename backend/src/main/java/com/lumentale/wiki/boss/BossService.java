package com.lumentale.wiki.boss;

import com.lumentale.wiki.boss.dto.BossDetail;
import com.lumentale.wiki.boss.dto.BossGraph;
import com.lumentale.wiki.boss.dto.BossSummary;
import com.lumentale.wiki.common.Guids;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.error.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates boss responses. Thin by design — the repository does the joins and
 * jsonb reads; this layer validates the guid (→ 400) and maps "no such boss /
 * graph" to a 404.
 */
@Service
public class BossService {

    private final BossRepository repo;
    private final LocalizationResolver loc;

    public BossService(BossRepository repo, LocalizationResolver loc) {
        this.repo = repo;
        this.loc = loc;
    }

    public List<BossSummary> list() {
        return repo.list();
    }

    public BossDetail detail(String guidStr, String lang) {
        UUID guid = Guids.require(guidStr);
        return repo.detail(guid, loc.normalize(lang)).orElseThrow(() -> new NotFoundException("boss", guidStr));
    }

    public BossGraph graph(String guidStr, String lang) {
        UUID guid = Guids.require(guidStr);
        return repo.graph(guid, loc.normalize(lang))
            .orElseThrow(() -> new NotFoundException("boss battle graph", guidStr));
    }
}
