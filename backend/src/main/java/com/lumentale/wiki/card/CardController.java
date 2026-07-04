package com.lumentale.wiki.card;

import com.lumentale.wiki.card.dto.CardDetail;
import com.lumentale.wiki.card.dto.CardPoolSummary;
import com.lumentale.wiki.card.dto.CardSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Card endpoints on the redesigned schema (Module 4).
 *
 *   GET /api/cards         — card list (rarity, ele label, depicted form, art, pool count)
 *   GET /api/cards/{guid}  — card detail (raw record + art/mask/holo + form + pools)
 *   GET /api/card-pools    — card-pool catalogue with entry counts
 */
@RestController
@RequestMapping("/api")
public class CardController {

    private final CardService cards;

    public CardController(CardService cards) { this.cards = cards; }

    @GetMapping("/cards")
    public List<CardSummary> cards() {
        return cards.list();
    }

    @GetMapping("/cards/{guid}")
    public CardDetail card(@PathVariable String guid) {
        return cards.detail(guid);
    }

    @GetMapping("/card-pools")
    public List<CardPoolSummary> pools() {
        return cards.pools();
    }
}
