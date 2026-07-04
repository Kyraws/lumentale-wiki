package com.lumentale.wiki.card.dto;

/**
 * One row of the cards list. {@code ele} is a resolved label (int code in the
 * schema); {@code formGuid} is the depicted form (null for the few formless
 * cards, omitted by Jackson); {@code art} is the hybrid-resolved card-art URL;
 * {@code pools} is the number of card pools this card appears in.
 */
public record CardSummary(String guid, String name, String rarity, String ele,
                          String formGuid, String art, Integer pools,
                          String holo, String mask,
                          Double holoTilingX, Double holoTilingY) {}
