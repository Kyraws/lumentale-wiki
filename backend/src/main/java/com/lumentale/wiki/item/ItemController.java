package com.lumentale.wiki.item;

import com.lumentale.wiki.item.dto.ItemDetail;
import com.lumentale.wiki.item.dto.ItemSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Item endpoints (ported from v2 onto the redesigned schema).
 *
 *   GET /api/items          — item list (no-icon items hidden); ?lang= (default en)
 *   GET /api/items/{guid}   — item detail (+ recipe / drops / shops / pickups)
 */
@RestController
@RequestMapping("/api")
public class ItemController {

    private final ItemService items;

    public ItemController(ItemService items) { this.items = items; }

    @GetMapping("/items")
    public List<ItemSummary> items(@RequestParam(required = false) String lang) {
        return items.list(lang);
    }

    @GetMapping("/items/{guid}")
    public ItemDetail item(@PathVariable String guid, @RequestParam(required = false) String lang) {
        return items.detail(guid, lang);
    }
}
