package com.lumentale.wiki.type;

import com.lumentale.wiki.type.dto.Defender;
import com.lumentale.wiki.type.dto.Offense;
import com.lumentale.wiki.type.dto.Quirk;
import com.lumentale.wiki.type.dto.TypeCoverage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Cross-cutting type analytics, quirks, and meta counts. Pure routing — all work
 * is delegated to {@link TypeService}. Read-only over already-seeded tables.
 *
 *   GET /api/types/coverage             — per attacking ele type, how forms react
 *   GET /api/types/defenders?limit=25   — best defenders, scored
 *   GET /api/types/offense              — offense leaderboard
 *   GET /api/quirks?lang=en             — quirks + owners (localized)
 *   GET /api/meta                       — catalogue counts
 */
@RestController
@RequestMapping("/api")
public class TypeController {

    private final TypeService types;

    public TypeController(TypeService types) { this.types = types; }

    @GetMapping("/types/coverage")
    public List<TypeCoverage> coverage() {
        return types.coverage();
    }

    @GetMapping("/types/defenders")
    public List<Defender> defenders(@RequestParam(defaultValue = "25") int limit) {
        return types.defenders(limit);
    }

    @GetMapping("/types/offense")
    public List<Offense> offense() {
        return types.offense();
    }

    @GetMapping("/quirks")
    public List<Quirk> quirks(@RequestParam(defaultValue = "en") String lang) {
        return types.quirks(lang);
    }

    @GetMapping("/meta")
    public Map<String, Integer> meta() {
        return types.meta();
    }
}
