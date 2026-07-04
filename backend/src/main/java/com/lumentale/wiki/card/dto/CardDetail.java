package com.lumentale.wiki.card.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Full card page: the card's pruned {@code raw} record (the canonical fields are
 * computed siblings) plus the "everything connects" cross-links — the depicted
 * form (guid/species/variant, null when the card has no form), the resolved
 * art/mask/holo URLs (two-hop Addressables), and the pools the card belongs to.
 */
public record CardDetail(
    JsonNode card,                 // raw pruned record
    String art,                    // hybrid-resolved card art URL
    String mask,                   // resolved mask texture URL (or omitted)
    String holo,                   // resolved holo texture URL (or omitted)
    Form form,                     // depicted form (null when form_guid unset)
    List<InPool> pools             // pools this card appears in
) {
    public record Form(String guid, String species, String variant, String menuArt) {}
    public record InPool(String name, Boolean kickstarter, Float weight, Integer level, Integer ord) {}
}
