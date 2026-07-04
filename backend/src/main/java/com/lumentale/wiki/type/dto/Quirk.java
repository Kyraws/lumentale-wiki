package com.lumentale.wiki.type.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A quirk (ability) with its localized name/description and the forms that have
 * it. {@code name}/{@code description} are resolved from {@code quirk.name_key}/
 * {@code desc_key} via the shared localization tables and omitted when absent.
 */
public record Quirk(@JsonProperty("class") String quirkClass, String name, String description,
                    List<Owner> owners) {

    /**
     * A form that has the quirk; {@code hidden} = the quirk is its hidden slot.
     * {@code dex}/{@code menuArt} let the frontend render the form's menu sprite
     * as a thumbnail link (resolved the same way the dex grid resolves art).
     */
    public record Owner(String guid, String species, String variant, boolean hidden,
                        Integer dex, String menuArt) {}
}
