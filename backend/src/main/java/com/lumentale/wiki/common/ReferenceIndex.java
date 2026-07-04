package com.lumentale.wiki.common;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Startup-loaded {@code code → name} maps for the Module-1 reference enums
 * ({@code ele_type}, {@code emotion_type}, and the per-domain lookups). The
 * redesign stores these axes as integer codes on entity rows (e.g.
 * {@code form.ele_type_code}, {@code move.category_code}); the wiki wants the
 * human label.
 *
 * Loading them ONCE here (a handful of tiny tables) means every page query can
 * select the raw int and translate in-memory, instead of joining four lookup
 * tables on every creature/move/boss row. The tables are static post-seed, so a
 * single boot-time read is correct for the process lifetime.
 */
@Component
public class ReferenceIndex {

    private final JdbcTemplate jdbc;

    private Map<Integer,String> eleType;
    private Map<Integer,String> emotionType;
    private Map<Integer,String> skillCategory;
    private Map<Integer,String> skillTarget;
    private Map<Integer,String> skillAoe;
    private Map<Integer,String> itemMaterial;
    private Map<Integer,String> questType;
    private Map<Integer,String> achievementRarity;

    public ReferenceIndex(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostConstruct
    public void build() {
        eleType           = load("ele_type");
        emotionType       = load("emotion_type");
        skillCategory     = load("skill_category");
        skillTarget       = load("skill_target_type");
        skillAoe          = load("skill_aoe_type");
        itemMaterial      = load("item_material");
        questType         = load("quest_type");
        achievementRarity = load("achievement_rarity");
    }

    private Map<Integer,String> load(String table) {
        Map<Integer,String> m = new HashMap<>();
        jdbc.query("SELECT code, name FROM " + table,
            rs -> { m.put(rs.getInt("code"), rs.getString("name")); });
        return Map.copyOf(m);
    }

    /** Elemental type label for a code, or {@code null} (NONE / unknown). */
    public String ele(Integer code)          { return code == null ? null : eleType.get(code); }
    public String emotion(Integer code)      { return code == null ? null : emotionType.get(code); }
    public String skillCategory(Integer code){ return code == null ? null : skillCategory.get(code); }
    public String skillTarget(Integer code)  { return code == null ? null : skillTarget.get(code); }
    public String skillAoe(Integer code)     { return code == null ? null : skillAoe.get(code); }
    public String itemMaterial(Integer code) { return code == null ? null : itemMaterial.get(code); }
    public String questType(Integer code)    { return code == null ? null : questType.get(code); }
    public String achievementRarity(Integer code) { return code == null ? null : achievementRarity.get(code); }

    /** All elemental type names, ordered by code — for the type-chart axes. */
    public Map<Integer,String> eleTypes() { return eleType; }
    public Map<Integer,String> emotionTypes() { return emotionType; }
}
