package com.lumentale.wiki.item;

import com.lumentale.wiki.common.Guids;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.error.NotFoundException;
import com.lumentale.wiki.item.ItemRepository.Base;
import com.lumentale.wiki.item.dto.ItemDetail;
import com.lumentale.wiki.item.dto.ItemSummary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Assembles item responses: the curated base + its four cross-link sections,
 * composed into one {@link ItemDetail}. Guid validation → 400; missing item → 404.
 * Names are English-resolved (default {@code en}, honouring {@code ?lang=}).
 */
@Service
public class ItemService {

    private final ItemRepository repo;
    private final LocalizationResolver loc;

    public ItemService(ItemRepository repo, LocalizationResolver loc) {
        this.repo = repo;
        this.loc = loc;
    }

    public List<ItemSummary> list(String lang) {
        return repo.summaries(loc.normalize(lang));
    }

    public ItemDetail detail(String guidStr, String lang) {
        UUID guid = Guids.require(guidStr);
        String l = loc.normalize(lang);
        Base b = repo.base(guid, l).orElseThrow(() -> new NotFoundException("item", guidStr));
        return new ItemDetail(
            b.guid(), b.name(), b.nameKey(), b.descKey(), b.description(), b.type(), b.material(),
            b.price(), b.maxStack(), b.icon(), b.effects(),
            repo.recipe(guid, l),
            repo.droppedBy(guid),
            repo.soldAt(guid, b.price()),
            repo.foundOn(guid),
            repo.usedIn(guid, l),
            repo.givenInStory(guid));
    }
}
