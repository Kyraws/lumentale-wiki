package com.lumentale.wiki.furniture;

import com.lumentale.wiki.furniture.dto.FurnitureDetail;
import com.lumentale.wiki.furniture.dto.FurnitureSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Furniture endpoints (catalogue page on the redesigned schema).
 *
 *   GET /api/furniture          — furniture list (with sold/questReward flags)
 *   GET /api/furniture/{guid}   — furniture detail + provenance (shops, quests)
 */
@RestController
@RequestMapping("/api")
public class FurnitureController {

    private final FurnitureService furniture;

    public FurnitureController(FurnitureService furniture) { this.furniture = furniture; }

    @GetMapping("/furniture")
    public List<FurnitureSummary> furnitureList(@RequestParam(required = false) String lang) {
        return furniture.list(lang);
    }

    @GetMapping("/furniture/{guid}")
    public FurnitureDetail furniture(@PathVariable String guid,
                                     @RequestParam(required = false) String lang) {
        return furniture.detail(guid, lang);
    }
}
