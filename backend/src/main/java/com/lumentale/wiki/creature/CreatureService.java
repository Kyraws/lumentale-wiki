package com.lumentale.wiki.creature;

import com.fasterxml.jackson.databind.JsonNode;
import com.lumentale.wiki.common.Guids;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.creature.CreatureRepository.Display;
import com.lumentale.wiki.creature.dto.CreatureDetail;
import com.lumentale.wiki.creature.dto.CreatureSummary;
import com.lumentale.wiki.error.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Assembles creature responses from the repository, the startup indexes
 * ({@link RegionIndex}, {@link StatGradeService}) and the cross-cutting services
 * ({@link EvolutionService}, {@link CrossReferenceService}, and the new
 * {@link TypeChartService} via the repository).
 *
 * The only orchestration layer — the controller stays pure routing, each
 * collaborator stays independently testable, and guid validation happens here so
 * a malformed guid is a 400 before any query runs.
 */
@Service
public class CreatureService {

    private final CreatureRepository repo;
    private final RegionIndex regions;
    private final StatGradeService stats;
    private final EvolutionService evolution;
    private final CrossReferenceService xref;
    private final LocalizationResolver loc;

    public CreatureService(CreatureRepository repo, RegionIndex regions, StatGradeService stats,
                           EvolutionService evolution, CrossReferenceService xref, LocalizationResolver loc) {
        this.repo = repo;
        this.regions = regions;
        this.stats = stats;
        this.evolution = evolution;
        this.xref = xref;
        this.loc = loc;
    }

    public List<CreatureSummary> dexGrid() {
        return repo.dexGrid(regions::regionsFor);
    }

    public CreatureDetail detail(String guidStr, String lang) {
        UUID guid = Guids.require(guidStr);
        JsonNode form = repo.rawForm(guid).orElseThrow(() -> new NotFoundException("creature", guidStr));
        String l = loc.normalize(lang);
        Display d = repo.display(guid, l).orElseThrow(() -> new NotFoundException("creature", guidStr));
        return new CreatureDetail(
            form,
            d.species(),
            d.variant(),
            d.dex(),
            d.ele(),
            d.description(),
            repo.learnset(guid, l),
            regions.regionsFor(guid.toString()),
            stats.gradesFor(guid),
            repo.typeChart(guid),
            xref.spawnsForForm(guid),
            xref.usedByForm(guid),
            evolution.evolvesFrom(guid),
            evolution.evoChain(guid));
    }

    public List<JsonNode> variants(String speciesGuid) {
        return repo.variantsOfSpecies(Guids.require(speciesGuid));
    }
}
