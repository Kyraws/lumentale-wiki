package com.lumentale.wiki.card;

import com.fasterxml.jackson.databind.JsonNode;
import com.lumentale.wiki.card.CardRepository.ArtGuids;
import com.lumentale.wiki.card.dto.CardDetail;
import com.lumentale.wiki.card.dto.CardPoolSummary;
import com.lumentale.wiki.card.dto.CardSummary;
import com.lumentale.wiki.common.AssetResolver;
import com.lumentale.wiki.common.Guids;
import com.lumentale.wiki.common.RawRecordService;
import com.lumentale.wiki.common.RawRecordService.RawTable;
import com.lumentale.wiki.error.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Assembles card responses: the list from {@link CardRepository}; the detail is
 * the card's pruned {@code raw} record (via the shared {@link RawRecordService})
 * plus its resolved art/mask/holo textures, the depicted form, and the pools it
 * belongs to. Guid validation → 400; missing card → 404.
 */
@Service
public class CardService {

    private final CardRepository repo;
    private final RawRecordService raw;
    private final AssetResolver assets;

    public CardService(CardRepository repo, RawRecordService raw, AssetResolver assets) {
        this.repo = repo;
        this.raw = raw;
        this.assets = assets;
    }

    public List<CardSummary> list() {
        return repo.summaries();
    }

    public CardDetail detail(String guidStr) {
        UUID guid = Guids.require(guidStr);
        JsonNode record = raw.find(RawTable.CARD, guid)
            .orElseThrow(() -> new NotFoundException("card", guidStr));
        ArtGuids art = repo.artGuids(guid).orElseThrow(() -> new NotFoundException("card", guidStr));
        String mask = assets.art("card", guid, "card_mask");
        String holo = assets.art("card", guid, "card_holo");
        return new CardDetail(
            record,
            repo.cardArt(guid, art.art()),
            mask != null ? mask : assets.fileForAddressable(art.mask()),
            holo != null ? holo : assets.fileForAddressable(art.holo()),
            repo.form(guid),
            repo.pools(guid));
    }

    public List<CardPoolSummary> pools() {
        return repo.poolSummaries();
    }
}
