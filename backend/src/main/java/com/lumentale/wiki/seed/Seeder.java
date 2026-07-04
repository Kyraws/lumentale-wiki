package com.lumentale.wiki.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Seeds the redesigned (wiki-db) schema from {@code data/seed/*.json} after
 * Flyway has applied the DDL. v3 owns BOTH schema and seed (v2 connected to an
 * externally-seeded DB).
 *
 * <h3>Scope</h3>
 * This is the <b>seeding PATH</b> for the two reference slices (creatures +
 * bosses) — reference enums, species, forms and their join tables, moves, and
 * bosses + their kit. Each remaining domain (items, world, quests, story,
 * cards, …) is a new {@code seed*} step following the identical pattern, listed
 * in {@link #run} so the gaps are explicit rather than implied-complete.
 *
 * <h3>FK ordering</h3>
 * The V13 cross-module FKs are hard constraints, so steps run in dependency order
 * (enums → species → move → form → form_* → boss → boss_skill). Child rows that
 * point at not-yet-seeded parents — {@code form_spawn}→{@code game_map},
 * {@code form_drop}→{@code item} — are deliberately deferred to the world/item
 * steps. Rows whose required parent is missing from THIS partial seed are skipped
 * with a logged count, so a slice seed never aborts on an FK violation. (A full
 * seed instead declares V13 {@code NOT VALID} then {@code VALIDATE} — see
 * wiki-db/00-OVERVIEW §6.)
 *
 * <h3>Idempotency</h3>
 * Every step is guarded by {@link #ifEmpty}: a table is seeded only when empty, so
 * adding a later domain backfills without a full reseed, and a reboot is a no-op.
 */
@Component
@Order(0)   // core seed (reference, creatures, moves, items, bosses, mechanics, logic) runs first;
            // modular domain seeders (card/furniture/trainer/quest/…) run after via @Order(10+).
public class Seeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(Seeder.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Value("${lumentale.seed.on-empty:true}")  private boolean seedOnEmpty;
    @Value("${lumentale.seed.dir:data/seed}")   private String seedDir;
    @Value("${lumentale.seed.phase4-dir:data/phase4-complete}") private String phase4Dir;

    /** Guids seeded this run, so child rows can skip dangling references cleanly. */
    private final Set<UUID> seededForms = new HashSet<>();
    private final Set<UUID> seededMoves = new HashSet<>();
    private final Set<UUID> seededItems = new HashSet<>();

    public Seeder(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!seedOnEmpty) { log.info("Seeding disabled (lumentale.seed.on-empty=false)."); return; }
        log.info("Seeder: source={}, phase4={}", seedDir, phase4Dir);

        JsonNode forms = readArray("forms.json");

        // --- reference enums (derived from the data, plus NONE=0) ----------------
        ifEmpty("ele_type",       () -> seedEleTypes(forms));
        ifEmpty("emotion_type",   () -> seedEmotionTypes(forms));
        ifEmpty("emotion_chart",  this::seedEmotionChart);   // after emotion_type (FK)
        ifEmpty("skill_category", this::seedEnumLookups);    // per-domain enums (FK targets for move/item)
        ifEmpty("quirk",          () -> seedQuirks(forms));
        ifEmpty("xp_curve",       this::seedXpCurves);       // before form (FK: form.exp_curve_type)

        // --- creatures + battle reference ---------------------------------------
        ifEmpty("species",  () -> seedSpecies(forms));
        ifEmpty("move",     this::seedMoves);                 // before form_skill (FK); needs enum lookups
        ifEmpty("item",     this::seedItems);                 // before crafting/form_drop (FK); needs item_material
        ifEmpty("crafting_recipe", this::seedCrafting);       // after item (FK: result/ingredient → item)
        ifEmpty("form",     () -> seedForms(forms));          // after species + enums
        ifEmpty("form_hidden_type", () -> seedFormChildren(forms));
        ifEmpty("form_drop", () -> seedFormDrops(forms));     // after form + item (FK)

        // --- bosses + logic graphs ----------------------------------------------
        ifEmpty("boss", this::seedBosses);                    // after species + form + move

        // --- NEW layers needing phase4 sources (no-op + note if not staged) -----
        ifEmpty("boss_battle_graph", this::seedBattleGraphs);

        // --- logic graphs (M10): behavior trees / timelines / minigames ---------
        ifEmpty("behavior_tree",     this::seedBehaviorTrees);
        ifEmpty("timeline_director", this::seedTimelines);
        ifEmpty("minigame_instance", this::seedMinigames);

        // --- mechanics: curated from native/FORMULAS.md + constants.json ---------
        ifEmpty("formula",           this::seedFormulas);
        ifEmpty("game_constant",     this::seedGameConstants);     // after formula (FK)
        ifEmpty("difficulty_scalar", this::seedDifficultyScalars);

        // --- TODO (same pattern, land with their pages) -------------------------
        //   seedCards/CardPools, seedMaps/MiniMaps, seedMapPlacements/Spawns/
        //   Shops/Battles, seedQuests, seedStory, seedTrainers, seedCamps,
        //   seedSquadrons, seedAchievements, seedFurniture, seedTutorials,
        //   seedFormSpawns (→game_map), seedAssets/AssetGuids/EntityAssets
        //   (phase4 manifest), seedLocalization. (reference enums, creatures,
        //   moves, items, crafting, drops, bosses, mechanics, logic graphs done.)

        log.info("Seeder finished.");
    }

    // ====================================================================== enums

    /** ele_type: union of (ele,ele_raw) on forms and (type,type_raw) on moves, + NONE=0. */
    private void seedEleTypes(JsonNode forms) {
        Map<Integer,String> codes = new TreeMap<>();
        codes.put(0, "NONE");
        for (JsonNode f : forms) putCode(codes, f, "ele", "ele_raw");
        JsonNode moves = readArray("moves.json");
        for (JsonNode m : moves) putCode(codes, m, "type", "type_raw");
        codes.forEach((code, name) ->
            jdbc.update("INSERT INTO ele_type(code,name) VALUES (?,?) ON CONFLICT DO NOTHING", code, name));
        log.info("  ele_type: {} rows", codes.size());
    }

    /**
     * emotion_type in the FORMS code system — the canonical one for the DB, since
     * {@code form.emotion_code} is seeded from {@code forms.json emo_raw}.
     * Codes 1–4 are confirmed directly by forms data ({@code emo}/{@code emo_raw}
     * pairs); code 5 = HORRENS by elimination (34 forms carry emo_raw=5 with no
     * name string, HORRENS is the only unassigned name, and it's attested in
     * dump.cs + asset names). Hardcoded so code 5 exists for the emotion_chart FK.
     *
     * NB: this differs from the engine's {@code EmoTypes} enum
     * (SEREUM=1,FELICIS=2,HORRENS=3,FUROR=4,MESTUS=5); the native chart is
     * translated into THIS system by {@link #seedEmotionChart}.
     */
    private void seedEmotionTypes(JsonNode forms) {
        Map<Integer,String> names = new TreeMap<>(Map.of(
            1, "FELICIS", 2, "MESTUS", 3, "FUROR", 4, "SEREUM", 5, "HORRENS"));
        names.forEach((code, name) ->
            jdbc.update("INSERT INTO emotion_type(code,name) VALUES (?,?) ON CONFLICT DO NOTHING", code, name));
        log.info("  emotion_type: {} rows", names.size());
    }

    /**
     * The global 5×5 emotion-effectiveness chart, recovered from
     * native/decompiled/BattleMath__GetEmotionalTypeEffectivenessMultiplier.c.
     * The three returned {@code _DAT_} symbols resolve via native/constants.json:
     * {@code _DAT_18229ba4c}=0.8 (resisted), {@code _DAT_18229b7a0}=1.0 (neutral),
     * {@code _DAT_18229bb04}=1.2 (super-effective).
     *
     * The decompiled function indexes the engine {@code EmoTypes} enum
     * (SEREUM=1,FELICIS=2,HORRENS=3,FUROR=4,MESTUS=5). The matrix below is already
     * TRANSLATED into the DB's forms code system (FELICIS=1,MESTUS=2,FUROR=3,
     * SEREUM=4,HORRENS=5) by name, so it joins {@code form.emotion_code} directly.
     * Rows = attacker, cols = defender; the diagonal is neutral (self vs self).
     */
    private void seedEmotionChart() {
        double[][] m = com.lumentale.wiki.creature.EmotionChartData.FORMS_CODE;
        int n = 0;
        for (int a = 0; a < 5; a++)
            for (int d = 0; d < 5; d++, n++)
                jdbc.update("INSERT INTO emotion_chart(attacker_code,defender_code,multiplier) VALUES (?,?,?)",
                    a + 1, d + 1, m[a][d]);
        log.info("  emotion_chart: {} rows (5×5, native chart translated to forms codes)", n);
    }

    /**
     * The 6 XP curves (curve_type 0..5) recovered from
     * native/decompiled/AniCurve__GetExpForLevel.c. The 12 {@code _UNK_}/{@code _DAT_}
     * constants are resolved via constants.json and substituted into the
     * expressions below. {@code AC(L)} denotes the per-curve AnimationCurve base
     * sample (native fn @0x293010), which the decompilation does NOT resolve — so
     * these are <b>structural</b>, and the precomputed {@code xp_level_exp} table is
     * deliberately left empty (fabricating numeric exp from an unverified base
     * would be inventing data; see wiki-db/00-OVERVIEW §6). forms link via
     * {@code form.exp_curve_type = level_curve}.
     */
    private void seedXpCurves() {
        String src = "native/decompiled/AniCurve__GetExpForLevel.c";
        String[][] curves = {
            { "0", "Curve 0 (cubic-blend)", "AC(L)·1.2 − AC(L)·15 + 100·L − 140" },
            { "1", "Curve 1 (sample passthrough)", "AC(L)" },
            { "2", "Curve 2 (piecewise)", "L≤49→(100−L)·AC(L)/50; 50≤L<68→(150−L)·AC(L)/100; "
                + "68≤L<98→((1911−10L)/3)·AC(L)/500; L≥98→(160−L)·AC(L)/100" },
            { "3", "Curve 3 (piecewise)", "L<15→((L+1)/3+24)·AC(L)/50; 15≤L<36→(L+14)·AC(L)/50; "
                + "L≥36→(L·0.5+32)·AC(L)/50" },
            { "4", "Curve 4 (scaled)", "AC(L)·4 / 5" },
            { "5", "Curve 5 (scaled)", "AC(L)·1.25" },
        };
        for (String[] c : curves)
            jdbc.update("INSERT INTO xp_curve(curve_type,name,kind,expression,keyframes,source_file) " +
                "VALUES (?,?, 'polynomial', ?, '[]'::jsonb, ?)",
                Integer.parseInt(c[0]), c[1], c[2], src);
        log.info("  xp_curve: {} rows (structural; xp_level_exp left empty — base AC(L) unresolved)", curves.length);
    }

    private void seedQuirks(JsonNode forms) {
        Set<String> classes = new TreeSet<>();
        for (JsonNode f : forms)
            for (JsonNode q : f.path("quirks")) classes.add(q.path("class").asText());
        for (String c : classes)
            jdbc.update("INSERT INTO quirk(quirk_class) VALUES (?) ON CONFLICT DO NOTHING", c);
        log.info("  quirk: {} rows", classes.size());
    }

    // ============================================================ species / moves

    private void seedSpecies(JsonNode forms) {
        // collapse forms → one species row (min dex, any lost_locked)
        Map<String,int[]> dex = new HashMap<>();      // guid → [minDex]
        Map<String,String> name = new HashMap<>();
        Map<String,Boolean> lost = new HashMap<>();
        for (JsonNode f : forms) {
            String g = f.path("species_guid").asText();
            name.put(g, f.path("species_name").asText());
            int d = f.path("dex").asInt(0);
            dex.merge(g, new int[]{d}, (a, b) -> new int[]{ Math.min(a[0], b[0]) });
            lost.merge(g, f.path("lost_locked").asBoolean(false), (a, b) -> a || b);
        }
        for (String g : name.keySet())
            jdbc.update("INSERT INTO species(guid,name,dex,lost_locked,raw) VALUES (?,?,?,?,'{}'::jsonb)",
                UUID.fromString(g), name.get(g), dex.get(g)[0], lost.get(g));
        log.info("  species: {} rows", name.size());
    }

    private void seedMoves() {
        JsonNode moves = readArray("moves.json");
        for (JsonNode m : moves) {
            UUID guid = UUID.fromString(m.path("guid").asText());
            jdbc.update(
                "INSERT INTO move(guid,name_raw,name_key,desc_key,description,ele_type_code,category_code," +
                "target_code,aoe_code,power,accuracy,sp_cost,cooldown,player_turn_cd,is_contact,is_dot,is_eot," +
                "num_effects,effects,raw) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb)",
                guid, txt(m,"name_raw"), txt(m,"name_key"), txt(m,"desc_key"), txt(m,"description_raw"),
                intOrNull(m,"type_raw"), intOrNull(m,"category_raw"), intOrNull(m,"target_raw"), intOrNull(m,"aoe_raw"),
                intOrNull(m,"power"), intOrNull(m,"accuracy"), intOrNull(m,"cost"), intOrNull(m,"cd"),
                intOrNull(m,"player_turn_cd"), boolOrNull(m,"is_contact"), boolOrNull(m,"is_dot"), boolOrNull(m,"is_eot"),
                m.path("effects").size(), json(m.get("effects")), json(m.get("raw")));
            seededMoves.add(guid);
        }
        log.info("  move: {} rows", seededMoves.size());
    }

    /**
     * Per-domain enum lookups, from the authoritative V1 schema values (which the
     * extracted data confirms exactly). These are the FK targets for move/item
     * code columns — without them, seeding move.category_code / item.material_code
     * would violate their foreign keys.
     */
    private void seedEnumLookups() {
        seedLookup("skill_category",     new String[]{ "PHYSICAL","SPECIAL","STATUS" });
        seedLookup("skill_target_type",  new String[]{ "Foe","Ally","AllyOnly","Any","Self" });
        seedLookup("skill_aoe_type",     new String[]{ "SingleTarget","TargetAOE","EveryoneAOE","AdjacentAOE" });
        seedLookup("item_material",      new String[]{ "Plastic","Glass","Organic","Metal" });
        seedLookup("item_target_type",   new String[]{ "Single","Multiple","None" });
        seedLookup("item_battle_target", new String[]{ "Current","Party" });
        seedLookup("quest_type",         new String[]{ "Main","Side","Task" });
        log.info("  enum lookups: 7 domains seeded");
    }

    /** Insert code=index/name rows into a (code int PK, name text) lookup table. */
    private void seedLookup(String table, String[] names) {
        for (int code = 0; code < names.length; code++)
            jdbc.update("INSERT INTO " + table + "(code,name) VALUES (?,?) ON CONFLICT DO NOTHING", code, names[code]);
    }

    // ============================================================ items / crafting

    /** ItemMaterial / target / battle-target are int codes; the source carries labels. */
    private static final Map<String,Integer> ITEM_TARGET = Map.of("Single",0,"Multiple",1,"None",2);
    private static final Map<String,Integer> ITEM_BATTLE = Map.of("Current",0,"Party",1);

    private void seedItems() {
        JsonNode items = readArray("items.json");
        for (JsonNode it : items) {
            UUID guid = UUID.fromString(it.path("guid").asText());
            jdbc.update(
                "INSERT INTO item(guid,name_raw,name_key,desc_key,description_it,type_code,type_label," +
                "material_code,target_type,battle_target,price,max_stack,sellable,givable,is_collectible," +
                "unbreakable,untossable,icon_guid,effects,raw) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb)",
                guid, txt(it,"name_raw"), txt(it,"name_key"), txt(it,"desc_key"), txt(it,"description_raw"),
                intOrNull(it,"type"), txt(it,"type_label"), intOrNull(it,"material"),
                ITEM_TARGET.get(txt(it,"target_type")), ITEM_BATTLE.get(txt(it,"battle_target")),
                intOrNull(it,"price"), intOrNull(it,"max_stack"),
                it.has("unsellable") ? !it.path("unsellable").asBoolean() : null,   // sellable = !unsellable
                boolOrNull(it,"givable"), boolOrNull(it,"is_collectible"),
                boolOrNull(it,"unbreakable"), boolOrNull(it,"untossable"),
                txt(it,"icon_guid"), arr(it.get("effects")), json(it.get("raw")));
            seededItems.add(guid);
        }
        log.info("  item: {} rows", seededItems.size());
    }

    private void seedCrafting() {
        JsonNode recipes = readArray("crafting.json");
        int rec = 0, ing = 0, ingMiss = 0;
        for (JsonNode r : recipes) {
            UUID guid = UUID.fromString(r.path("guid").asText());
            UUID result = uuidOrNull(r, "result_item_guid");
            if (result != null && !seededItems.contains(result)) result = null;   // FK guard
            jdbc.update(
                "INSERT INTO crafting_recipe(guid,name_raw,result_item_guid,project_type,success_rate," +
                "preferred_actor,raw) VALUES (?,?,?,?,?,?,?::jsonb)",
                guid, txt(r,"name_raw"), result, intOrNull(r,"project_type_raw"),
                intOrNull(r,"success_rate"), txt(r,"preferred_actor"), json(r.get("raw")));
            rec++;
            for (JsonNode req : r.path("requirements")) {
                UUID item = uuidOrNull(req, "item_guid");
                if (item == null || !seededItems.contains(item)) { ingMiss++; continue; }
                jdbc.update("INSERT INTO crafting_ingredient(recipe_guid,item_guid,amount) VALUES (?,?,?)",
                    guid, item, intOrNull(req,"amount"));
                ing++;
            }
        }
        log.info("  crafting_recipe: {} rows, crafting_ingredient: {} rows (skipped→item={})", rec, ing, ingMiss);
    }

    /** form_drop, deferred until item is seeded (FK form_drop.item_guid → item). */
    private void seedFormDrops(JsonNode forms) {
        int n = 0, miss = 0;
        for (JsonNode f : forms) {
            UUID form = UUID.fromString(f.path("guid").asText());
            for (JsonNode d : f.path("item_drops")) {
                UUID item = uuidOrNull(d, "item_guid");
                if (item == null || !seededItems.contains(item)) { miss++; continue; }
                jdbc.update("INSERT INTO form_drop(form_guid,item_guid,amount_min,amount_max) VALUES (?,?,?,?)",
                    form, item, intOrNull(d,"amount_min"), intOrNull(d,"amount_max"));
                n++;
            }
        }
        log.info("  form_drop: {} rows (skipped→item={})", n, miss);
    }

    // =================================================================== forms

    private void seedForms(JsonNode forms) {
        for (JsonNode f : forms) {
            UUID guid = UUID.fromString(f.path("guid").asText());
            int[] s = stats(f.path("stat_min_values"));
            int bst = Arrays.stream(s).sum();
            double[] h = range(f.path("range_height"));
            double[] w = range(f.path("range_weight"));
            jdbc.update(
                "INSERT INTO form(guid,species_guid,variant_name,dex,ele_type_code,emotion_code," +
                "stat_hp,stat_atk,stat_def,stat_spa,stat_spd,stat_spe,bst,catch_rate,base_affection,sp_amount," +
                "exp_given_mult,battle_weight,range_height_m,range_weight_kg,kind,description,can_follow," +
                "lost_locked,exp_curve_type,raw) " +
                "VALUES (?,?,?,?,?,?, ?,?,?,?,?,?, ?,?,?,?, ?,?, numrange(?::numeric,?::numeric,'[]'), " +
                "numrange(?::numeric,?::numeric,'[]'), ?,?,?,?,?,?::jsonb)",
                guid, UUID.fromString(f.path("species_guid").asText()), txt(f,"variant_name"), intOrNull(f,"dex"),
                nz(f,"ele_raw"), emoOrNull(f),
                s[0], s[1], s[2], s[3], s[4], s[5], bst,
                intOrNull(f,"catch_rate"), intOrNull(f,"base_affection"), intOrNull(f,"sp_amount"),
                dblOrNull(f,"exp_given_mult"), dblOrNull(f,"battle_weight"),
                h[0], h[1], w[0], w[1],
                txt(f,"kind"), txt(f,"description_raw"), f.path("can_follow").asBoolean(false),
                f.path("lost_locked").asBoolean(false), intOrNull(f,"level_curve"), json(f.get("raw")));
            seededForms.add(guid);
        }
        log.info("  form: {} rows", seededForms.size());
    }

    /** form_hidden_type, form_weakness, form_quirk, form_skill, form_evolution. */
    private void seedFormChildren(JsonNode forms) {
        Map<String,Integer> eleCode = loadCodes("ele_type");
        int weak = 0, hidden = 0, quirks = 0, skills = 0, evos = 0, skMiss = 0, evoMiss = 0;
        for (JsonNode f : forms) {
            UUID form = UUID.fromString(f.path("guid").asText());

            for (JsonNode ht : f.path("hidden_types")) {
                Integer c = eleCode.get(ht.asText());
                if (c != null) { jdbc.update("INSERT INTO form_hidden_type(form_guid,ele_type_code) VALUES (?,?) ON CONFLICT DO NOTHING", form, c); hidden++; }
            }
            var wk = f.path("weaknesses").fields();
            while (wk.hasNext()) {
                var e = wk.next();
                Integer c = eleCode.get(e.getKey());
                if (c != null) { jdbc.update("INSERT INTO form_weakness(form_guid,attacker_code,effectiveness) VALUES (?,?,?) ON CONFLICT DO NOTHING", form, c, e.getValue().asText()); weak++; }
            }
            for (JsonNode q : f.path("quirks")) {
                jdbc.update("INSERT INTO form_quirk(form_guid,quirk_class,is_hidden) VALUES (?,?,?)",
                    form, q.path("class").asText(), q.path("is_hidden").asBoolean(false));
                quirks++;
            }
            for (JsonNode sk : f.path("skills")) {
                UUID move = UUID.fromString(sk.path("move_guid").asText());
                if (!seededMoves.contains(move)) { skMiss++; continue; }   // skip dangling FK
                for (JsonNode lm : sk.path("learn_methods")) {
                    jdbc.update("INSERT INTO form_skill(form_guid,move_guid,method,level) VALUES (?,?,?,?)",
                        form, move, lm.path("Method").asText(), intOrNull(lm,"Level"));
                    skills++;
                }
            }
            for (JsonNode ev : f.path("evolutions")) {
                String tgt = ev.path("target_form_guid").asText(null);
                UUID target = (tgt == null || tgt.isBlank()) ? null : UUID.fromString(tgt);
                if (target != null && !seededForms.contains(target)) { evoMiss++; target = null; }
                jdbc.update("INSERT INTO form_evolution(form_guid,target_form_guid,method_class,level,params) " +
                    "VALUES (?,?,?,?,?::jsonb)",
                    form, target, txt(ev,"method_class"), intOrNull(ev.path("params"),"Level"), json(ev.get("params")));
                evos++;
            }
        }
        log.info("  form children: hidden={} weakness={} quirk={} skill={} evolution={} (skipped skill→move={}, evo→form={})",
            hidden, weak, quirks, skills, evos, skMiss, evoMiss);
    }

    // =================================================================== bosses

    private void seedBosses() {
        JsonNode bosses = readArray("bosses.json");
        int kit = 0, kitMiss = 0;
        for (JsonNode b : bosses) {
            UUID guid = UUID.fromString(b.path("guid").asText());
            UUID origin = uuidOrNull(b, "origin_species_guid");
            UUID form = uuidOrNull(b, "form_guid");
            jdbc.update(
                "INSERT INTO boss(guid,internal_name,display,origin_species_guid,form_guid,form_label," +
                "ele_type_code,emotion_code,hidden_type_code,level,exp_given,target_bst,sp_override," +
                "extra_health_bars,stats_override,ai,raw) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb)",
                guid, txt(b,"internal_name"), txt(b,"origin_species"), origin, form, txt(b,"form_variant"),
                nz(b,"ele_raw"), emoBossOrNull(b), intOrNull(b,"hidden_type_raw"), intOrNull(b,"level"),
                intOrNull(b,"exp_given"), intOrNull(b,"target_bst"), intOrNull(b,"sp_override"),
                intOrNull(b,"extra_health_bars"), json(b.get("stats_override")), json(b.get("ai")), json(b.get("raw")));

            int ord = 0;
            for (JsonNode bs : b.path("boss_skills")) {
                UUID move = uuidOrNull(bs, "move_guid");
                if (move == null || !seededMoves.contains(move)) { kitMiss++; ord++; continue; }
                jdbc.update("INSERT INTO boss_skill(boss_guid,move_guid,skill_level,ord) VALUES (?,?,?,?)",
                    guid, move, intOrNull(bs,"skill_level"), ord++);
                kit++;
            }
        }
        log.info("  boss: {} rows, boss_skill: {} rows (skipped→move={})", bosses.size(), kit, kitMiss);
    }

    /**
     * boss_battle_graph (whole-graph jsonb) + boss_graph_skill rollup, from
     * phase4 logic-graphs/battle_graphs.json. Edges are derived from each node's
     * link fields ({@code next}/{@code true}/{@code false} → target path_id), since
     * the source stores adjacency inline rather than as an edge list. The rollup
     * maps the graph's {@code boss_skills} (names) to moves by name_raw, skipping
     * unmatched. A row is written for every boss present in the {@code boss} table
     * (those without a scripted graph carry only the {@code note}).
     */
    private void seedBattleGraphs() {
        Path src = Path.of(phase4Dir, "logic-graphs", "battle_graphs.json");
        if (!Files.isRegularFile(src)) {
            log.warn("  boss_battle_graph: SKIPPED — {} not staged.", src);
            return;
        }
        JsonNode bosses;
        try { bosses = mapper.readTree(Files.readAllBytes(src)).path("bosses"); }
        catch (Exception e) { throw new IllegalStateException("Cannot read " + src, e); }

        Set<String> seededBosses = new HashSet<>();
        jdbc.query("SELECT guid FROM boss", rs -> { seededBosses.add(rs.getString(1)); });
        Map<String,UUID> moveByName = new HashMap<>();
        jdbc.query("SELECT guid, name_raw FROM move", rs -> {
            String n = rs.getString("name_raw");
            if (n != null) moveByName.putIfAbsent(n, (UUID) rs.getObject("guid"));
        });

        int graphs = 0, notes = 0, skipped = 0, rollup = 0, rollupMiss = 0;
        var it = bosses.fields();
        while (it.hasNext()) {
            var entry = it.next();
            String guid = entry.getKey();
            if (!seededBosses.contains(guid)) { skipped++; continue; }   // FK: boss must exist
            JsonNode b = entry.getValue();
            JsonNode bgList = b.path("battle_graph");
            JsonNode g = (bgList.isArray() && bgList.size() > 0) ? bgList.get(0) : null;

            if (g != null) {
                JsonNode nodes = g.path("nodes");
                jdbc.update(
                    "INSERT INTO boss_battle_graph(boss_guid,graph_name,graph_path_id,asset_guid,graph_bundle," +
                    "node_count,nodes,edges,note) VALUES (?,?,?,?,?,?,?::jsonb,?::jsonb,?)",
                    UUID.fromString(guid), txt(g,"graph_name"), longOrNull(g,"graph_path_id"),
                    txt(b,"asset_guid"), txt(b,"graph_bundle"), intOrNull(g,"node_count"),
                    nodes.toString(), deriveEdges(nodes), null);
                graphs++;
            } else {
                jdbc.update(
                    "INSERT INTO boss_battle_graph(boss_guid,asset_guid,graph_bundle,nodes,edges,note) " +
                    "VALUES (?,?,?,'[]'::jsonb,'[]'::jsonb,?)",
                    UUID.fromString(guid), txt(b,"asset_guid"), txt(b,"graph_bundle"), txt(b,"note"));
                notes++;
            }

            for (JsonNode sk : b.path("boss_skills")) {
                UUID move = moveByName.get(sk.asText());
                if (move == null) { rollupMiss++; continue; }
                jdbc.update("INSERT INTO boss_graph_skill(boss_guid,move_guid,target_form,target_formula) " +
                    "VALUES (?,?,?,?)", UUID.fromString(guid), move, null, null);
                rollup++;
            }
        }
        log.info("  boss_battle_graph: {} graphs + {} note-only (skipped no-boss={}); boss_graph_skill: {} (unmatched names={})",
            graphs, notes, skipped, rollup, rollupMiss);
    }

    /** Derive an edge list from inline node links (next/true/false → target path_id). */
    private String deriveEdges(JsonNode nodes) {
        var edges = mapper.createArrayNode();
        for (JsonNode n : nodes) {
            long from = n.path("path_id").asLong();
            for (String kind : new String[]{ "next", "true", "false" }) {
                JsonNode to = n.get(kind);
                if (to != null && to.isNumber()) {
                    var e = mapper.createObjectNode();
                    e.put("from", from); e.put("to", to.asLong()); e.put("kind", kind);
                    edges.add(e);
                }
            }
        }
        return edges.toString();
    }

    // ============================================================ logic graphs

    /** AI behavior trees (whole tree as nodes/edges jsonb). Source keys are camelCase. */
    private void seedBehaviorTrees() {
        JsonNode trees = readPhase4("logic-graphs/behavior_trees.json", "trees");
        if (trees == null) return;
        int n = 0, skipped = 0;
        for (JsonNode t : trees) {
            Long pathId = longOrNull(t, "pathID");
            if (pathId == null) { skipped++; continue; }   // path_id is the PK
            jdbc.update(
                "INSERT INTO behavior_tree(path_id,bundle,cab,object_name,behavior_name,behavior_desc," +
                "bd_version,kind,task_count,flags,external_behavior,nodes,edges) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?::jsonb) ON CONFLICT (path_id) DO NOTHING",
                pathId, txt(t,"bundle"), txt(t,"cab"), txt(t,"objectName"), txt(t,"behaviorName"),
                txt(t,"behaviorDescription"), txt(t,"bdVersion"), txt(t,"kind"), intOrNull(t,"taskCount"),
                obj(t.get("flags")), obj(t.get("externalBehavior")), arr(t.get("nodes")), arr(t.get("edges")));
            n++;
        }
        log.info("  behavior_tree: {} rows (skipped null-pathID={})", n, skipped);
    }

    /** Cutscene timeline directors (recursive track tree as jsonb). Nested under bundles. */
    private void seedTimelines() {
        JsonNode bundles = readPhase4("logic-graphs/timelines.json", "bundles");
        if (bundles == null) return;
        int n = 0;
        for (JsonNode b : bundles) {
            String bundle = txt(b, "bundle");
            for (JsonNode dir : b.path("directors")) {
                Long pathId = longOrNull(dir, "director_path_id");
                if (pathId == null) continue;
                JsonNode tracks = dir.path("tracks");
                jdbc.update(
                    "INSERT INTO timeline_director(director_path_id,bundle,gameobject,playable_asset_id," +
                    "timeline_name,wrap_mode,initial_state,update_mode,n_scene_bindings,n_tracks,n_clips," +
                    "crossbundle,tracks) VALUES (?,?,?,?,?,?,?,?,?,?,?, false, ?::jsonb) " +
                    "ON CONFLICT (director_path_id) DO NOTHING",
                    pathId, bundle, txt(dir,"gameobject"), longOrNull(dir.path("playable_asset"),"path_id"),
                    txt(dir,"timeline_name"), intOrNull(dir,"wrap_mode"), intOrNull(dir,"initial_state"),
                    intOrNull(dir,"update_mode"), intOrNull(dir,"n_scene_bindings"),
                    tracks.size(), countClips(tracks), arr(tracks));
                n++;
            }
        }
        log.info("  timeline_director: {} rows", n);
    }

    /** Recursively count clips across a track tree (tracks → clips + children). */
    private int countClips(JsonNode tracks) {
        int c = 0;
        for (JsonNode t : tracks) {
            c += t.path("clips").size();
            c += countClips(t.path("children"));
        }
        return c;
    }

    /** Minigame instances ({@code fields} blob). Source: instances{className:[...]}. */
    private void seedMinigames() {
        JsonNode root = readPhase4Root("logic-graphs/minigames.json");
        if (root == null) return;
        JsonNode instances = root.path("instances");
        int n = 0;
        var it = instances.fields();
        while (it.hasNext()) {
            var entry = it.next();
            String className = entry.getKey();
            for (JsonNode inst : entry.getValue()) {
                Long pathId = longOrNull(inst, "path_id");
                if (pathId == null) continue;
                jdbc.update(
                    "INSERT INTO minigame_instance(path_id,class_name,bundle,gameobject_name,fields) " +
                    "VALUES (?,?,?,?,?::jsonb) ON CONFLICT (path_id) DO NOTHING",
                    pathId, className, txt(inst,"bundle"), txt(inst,"gameobject_name"), obj(inst.get("fields")));
                n++;
            }
        }
        // minigame_prize is intentionally NOT seeded: the prize tables live inside
        // each instance's `fields` blob as opaque config (AcePrize/TopPrizes/… with
        // no resolved item GUIDs), so there's nothing to map to item_guid yet. The
        // full prize config is returned in the detail `fields`.
        log.info("  minigame_instance: {} rows (minigame_prize skipped — prizes are opaque in fields)", n);
    }

    /**
     * The recovered battle/progression formulas as citable rows, curated from
     * native/FORMULAS.md (one row per documented function). Each is a transcription
     * of a documented fact — signature + expression + confidence — not a parse of
     * the prose. {@code confidence} mirrors FORMULAS.md's per-formula honesty
     * (verified-by-construction / structurally-complete / partial).
     */
    private void seedFormulas() {
        String src = "native/FORMULAS.md";
        // {key, name, signature, expression, confidence, description}
        String[][] f = {
            { "damage", "Damage", "BattleMath$$DamageFormula(power, level, atk, def, x)",
              "dmg = ((atk+1)/(def+1))·(level·5+10)·0.3/100·power·((level+25)/(x+25))·0.45 + 7",
              "structural", "Core per-hit damage, before crit / effectiveness / random spread." },
            { "crit_chance", "Crit chance", "BattleMath$$GetCritChance(stage)",
              "v=2; for i in 1..stage: v+=i;  critChance% = v/23·100   (stage0 = 8.70%)",
              "verified", "Crit % by crit stat-stage; self-contained → verified by construction." },
            { "stat_stage_mult", "Stat-stage multiplier", "BattleMath$$GetStageMultiplier(stage)",
              "stage>=0: (stage+2)/2 ;  stage<0: 2/(2-stage)",
              "verified", "Classic stage table (+1->1.5, -1->0.667, ...)." },
            { "emotion_effectiveness", "Emotion-type effectiveness",
              "BattleMath$$GetEmotionalTypeEffectivenessMultiplier(atkEmotion, defEmotion)",
              "x1.2 super-effective . x0.8 resisted . x1.0 neutral  (full 5x5 -> emotion_chart)",
              "verified", "Emotion axis multiplier; the resolved 5x5 lives in emotion_chart." },
            { "stat", "Stat formula", "BattleMath$$StatFormula(kind, base, nature, level, iv)",
              "t=sqrt(iv)·base/100;  HP(kind0): base·0.5+5+level+t+base+15 ;  else: base·0.5·nature+5+level+t",
              "structural", "Final stat from base/level; the sqrt-term (IV/EV) semantics are inferred." },
            { "xp_curve", "XP curve", "AniCurve$$GetExpForLevel(curveType, level)",
              "switch(curveType): polynomial (types 0,2) or AnimationCurve.Evaluate(level) (data). See xp_curve.",
              "partial", "Non-polynomial curve types are designer keyframe data, not closed-form." },
            { "difficulty", "Difficulty damage scaling",
              "GameState$$ModifyDamageByDifficulty(gameState, battleInfo, dmg)",
              "NORMAL x1.0 ;  EASY player_out x1.24, enemy_out x0.75 ;  HARD enemy_out x1.20",
              "verified", "In-place damage scale by difficulty + side. See difficulty_scalar." },
            { "catch_rate", "Catch rate", "GameEventMaster$$OnCatchrateCalculation (event dispatch)",
              "No closed form: additive / scalar / attribute modifiers (bilia items + camp buffs) compose the rate at runtime.",
              "partial", "Event-driven modifier chain, not a single formula." },
            { "damage_pipeline", "Damage pipeline (end-to-end)", "BattleMath$$CalculateDamage(info, hits)",
              "base=DamageFormula(...); dmg=base x crit x elementEff x emotionEff x spread; ModifyDamageByDifficulty; x hits",
              "structural", "Full path from move+stats to final number (orchestration)." },
        };
        for (String[] r : f)
            jdbc.update("INSERT INTO formula(key,name,signature,expression,confidence,source_file) " +
                "VALUES (?,?,?,?,?,?)", r[0], r[1], r[2], r[3], r[4], src);
        log.info("  formula: {} rows", f.length);
    }

    /**
     * Named tuning constants ({@code game_constant}), the meaningful subset behind
     * the formulas. Values from FORMULAS.md; {@code va} provenance is filled where
     * the address is known (the emotion multipliers resolve in constants.json),
     * null otherwise rather than guessed. {@code formula_key} links to seedFormulas.
     */
    private void seedGameConstants() {
        // {name, value, kind, va, formula_key, description}
        Object[][] c = {
            { "crit_base_pct",        8.70, "percent",    null,           "crit_chance",           "Crit chance at stat-stage 0" },
            { "crit_divisor",         23.0, "divisor",    null,           "crit_chance",           "Denominator in critChance% = v/23·100" },
            { "emotion_super_mult",   1.2,  "multiplier", "0x18229bb04",  "emotion_effectiveness", "Super-effective emotion multiplier" },
            { "emotion_resist_mult",  0.8,  "multiplier", "0x18229ba4c",  "emotion_effectiveness", "Resisted emotion multiplier" },
            { "emotion_neutral_mult", 1.0,  "multiplier", "0x18229b7a0",  "emotion_effectiveness", "Neutral emotion multiplier" },
            { "easy_player_out_mult", 1.24, "multiplier", null,           "difficulty",            "EASY: player-dealt damage scale" },
            { "easy_enemy_out_mult",  0.75, "multiplier", null,           "difficulty",            "EASY: enemy-dealt damage scale" },
            { "hard_enemy_out_mult",  1.20, "multiplier", null,           "difficulty",            "HARD: enemy-dealt damage scale" },
            { "damage_flat_add",      7.0,  "additive",   null,           "damage",                "Flat damage added at the end of DamageFormula" },
            { "damage_scale",         0.3,  "multiplier", null,           "damage",                "Scale on the (level·5+10) term" },
            { "damage_x_scale",       0.45, "multiplier", null,           "damage",                "Scale on the ((level+25)/(x+25)) term" },
            { "stat_hp_bonus",        15.0, "additive",   null,           "stat",                  "Extra constant in the HP stat branch" },
        };
        for (Object[] r : c)
            jdbc.update("INSERT INTO game_constant(name,value,kind,va,formula_key,description) VALUES (?,?,?,?,?,?)",
                r[0], r[1], r[2], r[3], r[4], r[5]);
        log.info("  game_constant: {} rows", c.length);
    }

    /** Difficulty damage multipliers ({@code difficulty_scalar}); NORMAL = no row (×1.0). */
    private void seedDifficultyScalars() {
        Object[][] d = {
            { "EASY", "player_out", 1.24 },
            { "EASY", "enemy_out",  0.75 },
            { "HARD", "enemy_out",  1.20 },
        };
        for (Object[] r : d)
            jdbc.update("INSERT INTO difficulty_scalar(difficulty,direction,multiplier) VALUES (?,?,?)",
                r[0], r[1], r[2]);
        log.info("  difficulty_scalar: {} rows", d.length);
    }

    // ================================================================== helpers

    @FunctionalInterface private interface SeedStep { void run() throws Exception; }

    private void ifEmpty(String table, SeedStep step) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        if (n != null && n > 0) { log.info("  {} already has {} rows — skipping.", table, n); return; }
        try { step.run(); }
        catch (Exception e) { throw new IllegalStateException("Seed step failed for " + table, e); }
    }

    private JsonNode readArray(String file) {
        try {
            JsonNode root = mapper.readTree(Files.readAllBytes(Path.of(seedDir, file)));
            if (root.isArray()) return root;
            for (JsonNode v : root) if (v.isArray()) return v;   // {key: [...]} wrapper
            return mapper.createArrayNode();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read seed file " + file + " under " + seedDir, e);
        }
    }

    private Map<String,Integer> loadCodes(String table) {
        Map<String,Integer> m = new HashMap<>();
        jdbc.query("SELECT code,name FROM " + table, rs -> { m.put(rs.getString("name"), rs.getInt("code")); });
        return m;
    }

    private static void putCode(Map<Integer,String> into, JsonNode n, String nameField, String codeField) {
        if (n.hasNonNull(codeField) && n.hasNonNull(nameField))
            into.putIfAbsent(n.path(codeField).asInt(), n.path(nameField).asText());
    }

    private static String txt(JsonNode n, String f) {
        JsonNode v = n.get(f); return (v == null || v.isNull()) ? null : v.asText();
    }
    private static Integer intOrNull(JsonNode n, String f) {
        JsonNode v = n.get(f); return (v == null || v.isNull() || !v.isNumber()) ? null : v.asInt();
    }
    private static Double dblOrNull(JsonNode n, String f) {
        JsonNode v = n.get(f); return (v == null || v.isNull() || !v.isNumber()) ? null : v.asDouble();
    }
    private static Long longOrNull(JsonNode n, String f) {
        JsonNode v = n.get(f); return (v == null || v.isNull() || !v.isNumber()) ? null : v.asLong();
    }
    private static Boolean boolOrNull(JsonNode n, String f) {
        JsonNode v = n.get(f); return (v == null || v.isNull()) ? null : v.asBoolean();
    }
    private static Integer nz(JsonNode n, String f) {            // ele code: 0 (NONE) → null FK
        Integer v = intOrNull(n, f); return (v == null || v == 0) ? null : v;
    }
    private static Integer emoOrNull(JsonNode f) {
        Integer v = intOrNull(f, "emo_raw"); return (v == null || v == 0) ? null : v;
    }
    private static Integer emoBossOrNull(JsonNode b) {
        Integer v = intOrNull(b, "emo_raw"); return (v == null || v == 0) ? null : v;
    }
    private static UUID uuidOrNull(JsonNode n, String f) {
        String s = txt(n, f); return (s == null || s.isBlank()) ? null : UUID.fromString(s);
    }
    private String json(JsonNode n) {
        return (n == null || n.isNull()) ? "{}" : n.toString();
    }
    private static String obj(JsonNode n) { return (n == null || n.isNull()) ? "{}" : n.toString(); }
    private static String arr(JsonNode n) { return (n == null || n.isNull()) ? "[]" : n.toString(); }

    /** Read a named array from a phase4 JSON document, or null (+warn) if the file is absent. */
    private JsonNode readPhase4(String file, String arrayKey) {
        JsonNode root = readPhase4Root(file);
        if (root == null) return null;
        JsonNode arr = root.path(arrayKey);
        return arr.isArray() ? arr : mapper.createArrayNode();
    }

    /** Read a phase4 JSON document's root, or null (+warn) if not staged. */
    private JsonNode readPhase4Root(String file) {
        Path src = Path.of(phase4Dir, file);
        if (!Files.isRegularFile(src)) {
            log.warn("  SKIPPED — {} not staged.", src);
            return null;
        }
        try { return mapper.readTree(Files.readAllBytes(src)); }
        catch (Exception e) { throw new IllegalStateException("Cannot read " + src, e); }
    }
    private static int[] stats(JsonNode arr) {
        int[] s = new int[6];
        for (int i = 0; i < 6 && i < arr.size(); i++) s[i] = arr.get(i).asInt();
        return s;
    }
    private static double[] range(JsonNode arr) {
        if (arr == null || arr.size() < 2) return new double[]{ 0, 0 };
        return new double[]{ arr.get(0).asDouble(), arr.get(1).asDouble() };
    }
}
