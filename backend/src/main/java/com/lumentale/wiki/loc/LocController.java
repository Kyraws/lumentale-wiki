package com.lumentale.wiki.loc;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Localization endpoints (Module 12) — the 8-language string tables exposed as
 * flat {@code {m_key → text}} maps the frontend can load once per language.
 *
 *   GET /api/loc/{lang}         — every resolved string for a language
 *   GET /api/loc/{lang}/{table} — one loc table for a language
 *
 * An unsupported {@code lang} is a typed 404. Langs: it,en,de,es,fr,pt,ja,zh.
 */
@RestController
@RequestMapping("/api")
public class LocController {

    private final LocService loc;

    public LocController(LocService loc) { this.loc = loc; }

    @GetMapping("/loc/{lang}")
    public Map<String, String> all(@PathVariable String lang) {
        return loc.all(lang);
    }

    @GetMapping("/loc/{lang}/{table}")
    public Map<String, String> table(@PathVariable String lang, @PathVariable String table) {
        return loc.table(lang, table);
    }
}
