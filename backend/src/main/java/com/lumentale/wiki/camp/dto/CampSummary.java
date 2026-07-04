package com.lumentale.wiki.camp.dto;

/**
 * One row of the camps list.
 *
 * <p>The raw {@code name} is the internal codename (e.g. {@code P01_CenterCampData});
 * {@code displayName}, {@code region} and {@code area} are derived from it by
 * {@code CampNaming} so the UI never shows a codename. {@code effectClass} is the
 * camp's effect type string (e.g. ExpBoostCampEffect) and {@code effectLabel} a
 * short human label for it; {@code effectIncrement} is the bonus magnitude (a
 * fraction, e.g. 0.1 = +10%). {@code influence}/{@code lumenAmount} are the camp's
 * progression numbers. Null fields are omitted by Jackson.
 */
public record CampSummary(String guid, String name, String displayName, String region,
                          String area, String effectClass, String effectLabel,
                          Double effectIncrement, Integer influence, Integer lumenAmount) {}
