package com.lumentale.wiki.trainer.dto;

/**
 * One animon on a trainer's team. Joined from {@code trainer_party} → {@code form}
 * (+ {@code species}); {@code emo}/{@code ele} are resolved labels (the redesign
 * stores {@code form.emotion_code}/{@code ele_type_code} as int codes, translated
 * via {@code ReferenceIndex}), {@code item} is the held-item name (item join), and
 * {@code menuArt} is the hybrid-resolved form art.
 */
public record PartyMember(int ord, String formGuid, String species, String variant, Integer level,
                          String emo, String ele, String nickname, String item, String quirkClass, String menuArt) {}
