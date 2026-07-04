package com.lumentale.wiki.squadron;

import com.lumentale.wiki.common.Guids;
import com.lumentale.wiki.error.NotFoundException;
import com.lumentale.wiki.squadron.SquadronRepository.Base;
import com.lumentale.wiki.squadron.dto.SquadronDetail;
import com.lumentale.wiki.squadron.dto.SquadronSummary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Assembles squadron responses: the curated base + resolved logo/texture art +
 * the camp boss (a trainer) + the member roster, composed into one
 * {@link SquadronDetail}. Guid validation → 400; missing squadron → 404.
 */
@Service
public class SquadronService {

    private final SquadronRepository repo;

    public SquadronService(SquadronRepository repo) { this.repo = repo; }

    public List<SquadronSummary> list() {
        return repo.summaries();
    }

    public SquadronDetail detail(String guidStr) {
        UUID guid = Guids.require(guidStr);
        Base b = repo.base(guid).orElseThrow(() -> new NotFoundException("squadron", guidStr));
        var members = repo.members(guid);
        // The roster (placeholder "--- UNUSED ---" entries filtered out) is the real
        // member count; the stored member_count column includes those placeholders.
        // logo: per-squadron export on disk first, then the Addressables two-hop
        String logo = repo.diskLogo(guid);
        if (logo == null) logo = repo.assetUrl(b.logoGuid());
        return new SquadronDetail(
            b.guid(), b.name(), SquadronNaming.rankLabel(b.rank()), b.rank(), members.size(),
            logo,
            repo.assetUrl(b.textureGuid()),
            repo.campBoss(b.campBossGuid()),
            members);
    }
}
