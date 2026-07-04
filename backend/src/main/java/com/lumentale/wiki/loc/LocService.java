package com.lumentale.wiki.loc;

import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.error.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Localization page service (Module 12). A thin delegate over the shared
 * {@link LocalizationResolver} — the slice owns no localization SQL of its own,
 * it just exposes the resolver's {@code all}/{@code table} reads as HTTP
 * payloads and turns an unsupported language into a typed 404.
 *
 * The resolver returns flat {@code {m_key → text}} maps keyed off the
 * {@code loc_key → localization} join; the maps are static post-seed and
 * already {@code @Cacheable} on the resolver, so this layer stays pure routing.
 */
@Service
public class LocService {

    private final LocalizationResolver loc;

    public LocService(LocalizationResolver loc) { this.loc = loc; }

    /** Flat {m_key → text} for an entire language; 404 if the language is unsupported. */
    public Map<String, String> all(String lang) {
        if (!loc.isSupported(lang)) throw new NotFoundException("language", lang);
        return loc.all(lang);
    }

    /** {m_key → text} for one loc table in one language; 404 if the language is unsupported. */
    public Map<String, String> table(String lang, String table) {
        if (!loc.isSupported(lang)) throw new NotFoundException("language", lang);
        return loc.table(table, lang);
    }
}
