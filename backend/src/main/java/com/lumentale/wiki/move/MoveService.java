package com.lumentale.wiki.move;

import com.fasterxml.jackson.databind.JsonNode;
import com.lumentale.wiki.common.Guids;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.common.RawRecordService;
import com.lumentale.wiki.common.RawRecordService.RawTable;
import com.lumentale.wiki.error.NotFoundException;
import com.lumentale.wiki.move.dto.MoveLearner;
import com.lumentale.wiki.move.dto.MoveSummary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates move responses. The list/learners come from {@link MoveRepository};
 * the detail is the move's pruned {@code raw} record via the shared
 * {@link RawRecordService}. Guid validation happens here (→ 400).
 */
@Service
public class MoveService {

    private final MoveRepository repo;
    private final RawRecordService raw;
    private final LocalizationResolver loc;

    public MoveService(MoveRepository repo, RawRecordService raw, LocalizationResolver loc) {
        this.repo = repo;
        this.raw = raw;
        this.loc = loc;
    }

    public List<MoveSummary> list(String lang) {
        return repo.summaries(loc.normalize(lang));
    }

    public JsonNode detail(String guidStr) {
        UUID guid = Guids.require(guidStr);
        return raw.find(RawTable.MOVE, guid).orElseThrow(() -> new NotFoundException("move", guidStr));
    }

    public List<MoveLearner> learners(String guidStr) {
        return repo.learners(Guids.require(guidStr));
    }
}
