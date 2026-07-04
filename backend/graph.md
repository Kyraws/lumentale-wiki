# backend-v3 — visual architecture

A layered tour: first the **big picture** (five boxes), then we open each box to
show the **real data** and **how it's transformed** at every hop. Diagrams are
Mermaid (render on GitHub / most Markdown viewers).

🎨 **Colour key** — 🟡 sources · 🔵 transform · 🟢 database · 🟣 API/DTO ·
⚪ client · 🔴 honest gap (blocked on data extraction)

---

## 0. The big picture

Five stages. Data flows left→right at runtime; the seed pipeline (stages 1–3)
runs once at boot.

```mermaid
flowchart LR
    S["🟡 <b>SOURCES</b><br/>game extract JSON<br/>+ decompiled native"]
    I["🔵 <b>INGESTION</b><br/>Flyway DDL<br/>+ idempotent Seeder"]
    D["🟢 <b>DATABASE</b><br/>anidex3 · 81 tables<br/>static, read-only"]
    A["🟣 <b>API</b><br/>backend-v3<br/>Controller→Service→Repository"]
    C["⚪ <b>CLIENT</b><br/>frontend<br/>camelCase JSON"]

    S --> I --> D --> A --> C

    classDef src  fill:#FFF3CD,stroke:#E0A800,stroke-width:2px,color:#5a4500;
    classDef xf   fill:#D1ECF1,stroke:#17A2B8,stroke-width:2px,color:#0b4f5c;
    classDef dbc  fill:#D4EDDA,stroke:#28A745,stroke-width:2px,color:#155724;
    classDef api  fill:#E2D9F3,stroke:#6F42C1,stroke-width:2px,color:#3d2566;
    classDef cli  fill:#E9ECEF,stroke:#6C757D,stroke-width:2px,color:#343a40;
    class S src; class I xf; class D dbc; class A api; class C cli;
```

---

## 1. Open the boxes — what's inside each stage

```mermaid
flowchart TB
    subgraph SRC["🟡 SOURCES"]
        direction TB
        seed["data/seed/*.json<br/><i>forms · moves · bosses · …</i>"]
        native["phase4/native/<br/><i>FORMULAS.md · constants.json · decompiled/*.c</i>"]
        logic["phase4/logic-graphs/<br/><i>battle_graphs · behavior_trees · timelines · minigames</i>"]
        manifest["phase4/assets/<br/><i>manifest shards</i> 🔴"]
    end

    subgraph ING["🔵 INGESTION"]
        direction TB
        fly["Flyway<br/>applies wiki-db/migrations V1..V13"]
        sdr["Seeder<br/>FK-ordered · ifEmpty per table"]
    end

    subgraph DB["🟢 DATABASE — anidex3"]
        direction TB
        refT["reference: ele_type · emotion_type · emotion_chart · xp_curve"]
        coreT["core: species · form · form_* · move"]
        npcT["npc: boss · boss_battle_graph"]
        mechT["mechanics: formula · game_constant · difficulty_scalar"]
        graphT["logic: behavior_tree · timeline_director · minigame_instance"]
    end

    subgraph APIp["🟣 API — slices"]
        direction TB
        slc["creature · boss · mechanics · logicgraph · move"]
        cmn["common/: ReferenceIndex · AssetResolver · LocalizationResolver · RawRecordService · Guids"]
    end

    SRC --> ING --> DB --> APIp

    classDef src  fill:#FFF3CD,stroke:#E0A800,color:#5a4500;
    classDef xf   fill:#D1ECF1,stroke:#17A2B8,color:#0b4f5c;
    classDef dbc  fill:#D4EDDA,stroke:#28A745,color:#155724;
    classDef api  fill:#E2D9F3,stroke:#6F42C1,color:#3d2566;
    class seed,native,logic,manifest src; class fly,sdr xf;
    class refT,coreT,npcT,mechT,graphT dbc; class slc,cmn api;
    style SRC fill:#FFFBF0,stroke:#E0A800,stroke-width:2px;
    style ING fill:#F0FAFC,stroke:#17A2B8,stroke-width:2px;
    style DB  fill:#F2FBF4,stroke:#28A745,stroke-width:2px;
    style APIp fill:#F6F2FC,stroke:#6F42C1,stroke-width:2px;
```

---

## 2. DRILL — how a creature row becomes JSON (the read path)

Follow one real creature (**Mewaii**) from raw columns to the response. The
redesign stores compact **int codes** + **no art columns**; the repository
*resolves* them, the service *assembles*, the serializer *omits nulls*.

```mermaid
flowchart LR
    subgraph DBR["🟢 DB ROW (form)"]
        direction TB
        r["guid 8f6bea97…<br/>ele_type_code = <b>6</b><br/>emotion_code = <b>2</b><br/>(no art column)<br/>stat_hp/atk/… ints"]
    end

    subgraph REPO["🔵 REPOSITORY — resolve"]
        direction TB
        x1["ReferenceIndex.ele(6) → <b>VIRUS</b>"]
        x2["ReferenceIndex.emotion(2) → <b>MESTUS</b>"]
        x3["AssetResolver.art(form,guid,menu_art)<br/>→ /data/forms/…/menu.png"]
        x4["form_weakness + emotion_chart<br/>→ TypeChartService (pure)"]
    end

    subgraph DTO["🟣 DTO (CreatureDetail)"]
        direction TB
        d["ele: VIRUS · emo: MESTUS<br/>menuArt: /data/…<br/>typeChart{ elemental, emotion± }<br/>statGrades · regions · evoChain"]
    end

    subgraph JSON["⚪ JSON RESPONSE"]
        direction TB
        j["camelCase keys<br/>null fields <b>omitted</b><br/>Cache-Control: 1h"]
    end

    DBR -->|"RowMapper"| REPO -->|"Service assembles"| DTO -->|"Jackson non_null"| JSON

    classDef dbc fill:#D4EDDA,stroke:#28A745,color:#155724;
    classDef xf  fill:#D1ECF1,stroke:#17A2B8,color:#0b4f5c;
    classDef api fill:#E2D9F3,stroke:#6F42C1,color:#3d2566;
    classDef cli fill:#E9ECEF,stroke:#6C757D,color:#343a40;
    class r dbc; class x1,x2,x3,x4 xf; class d api; class j cli;
    style DBR fill:#F2FBF4,stroke:#28A745; style REPO fill:#F0FAFC,stroke:#17A2B8;
    style DTO fill:#F6F2FC,stroke:#6F42C1; style JSON fill:#F1F3F5,stroke:#6C757D;
```

---

## 3. DRILL — how raw JSON becomes typed rows (the seed path)

One `forms.json` record **fans out** into the species + form + child tables. The
Seeder transforms each field to its typed home; codes 0/blank become NULL FKs;
dangling refs are skipped (logged), never crash the seed.

```mermaid
flowchart TB
    src["🟡 forms.json record<br/>species_guid · species_name · dex<br/>ele_raw=6 · emo_raw=2 · level_curve<br/>weaknesses{ELECTRIC:WEAKNESS,…}<br/>skills[{move_guid,learn_methods}]<br/>quirks[] · evolutions[] · raw{…}"]

    subgraph T["🔵 Seeder transforms"]
        direction TB
        t1["species_name → <b>species</b> (dedup, min dex)"]
        t2["typed cols + ele_raw→code + level_curve→exp_curve_type<br/>→ <b>form</b> (raw kept as jsonb)"]
        t3["weaknesses dict → 13× <b>form_weakness</b> (name→code)"]
        t4["skills → <b>form_skill</b> (skip if move unseeded)"]
        t5["evolutions → <b>form_evolution</b> · quirks → <b>form_quirk</b>"]
    end

    src --> T
    t1 --> sp[("🟢 species")]
    t2 --> fm[("🟢 form")]
    t3 --> fw[("🟢 form_weakness")]
    t4 --> fs[("🟢 form_skill")]
    t5 --> fe[("🟢 form_evolution / _quirk")]

    classDef src fill:#FFF3CD,stroke:#E0A800,color:#5a4500;
    classDef xf  fill:#D1ECF1,stroke:#17A2B8,color:#0b4f5c;
    classDef dbc fill:#D4EDDA,stroke:#28A745,color:#155724;
    class src src; class t1,t2,t3,t4,t5 xf; class sp,fm,fw,fs,fe dbc;
    style T fill:#F0FAFC,stroke:#17A2B8,stroke-width:2px;
```

---

## 4. DRILL — recovering the emotion chart from the binary

The trickiest transform: a decompiled C function → resolved constants → an
enum-system translation → seeded rows the creature page joins. This is why the
emotion axis is *correct*, not guessed.

```mermaid
flowchart LR
    a["🟡 decompiled .c<br/>returns _DAT_ symbols<br/>per (atk,def) branch"]
    b["🔵 resolve via constants.json<br/>_DAT_…ba4c=0.8<br/>_DAT_…b7a0=1.0<br/>_DAT_…bb04=1.2"]
    c["🔵 translate by NAME<br/>EmoTypes codes → forms codes<br/>(so it joins form.emotion_code)"]
    d["🔵 EmotionChartData (tested)<br/>5×5 matrix · diagonal=1.0"]
    e[("🟢 emotion_chart<br/>25 rows")]
    f["🟣 CreatureDetail.typeChart<br/>emotionOffense / emotionDefense"]

    a --> b --> c --> d --> e --> f

    classDef src fill:#FFF3CD,stroke:#E0A800,color:#5a4500;
    classDef xf  fill:#D1ECF1,stroke:#17A2B8,color:#0b4f5c;
    classDef dbc fill:#D4EDDA,stroke:#28A745,color:#155724;
    classDef api fill:#E2D9F3,stroke:#6F42C1,color:#3d2566;
    class a src; class b,c,d xf; class e dbc; class f api;
```

---

## 5. DRILL — hybrid asset resolution (one contract, two legs)

Every art field returns a `/data/...` URL — filesystem first, DB manifest as
fallback.

```mermaid
flowchart TB
    q(["🟣 art(entityType, guid, role)"])
    disk{"🔵 file on disk?<br/>data/assets/.../menu.png"}
    ea["🔵 entity_asset → Addressables GUID"]
    hop{"🔵 asset_guid → bundle,path_id → asset.file"}
    url(["⚪ /data/... URL"])
    none(["⚪ null → field omitted"])

    q --> disk
    disk -->|yes| url
    disk -->|no| ea --> hop
    hop -->|found| url
    hop -->|"🔴 no asset_guid cache yet"| none

    classDef xf  fill:#D1ECF1,stroke:#17A2B8,color:#0b4f5c;
    classDef api fill:#E2D9F3,stroke:#6F42C1,color:#3d2566;
    classDef cli fill:#E9ECEF,stroke:#6C757D,color:#343a40;
    classDef gap fill:#F8D7DA,stroke:#DC3545,color:#721c24,stroke-dasharray:4 3;
    class disk,ea,hop xf; class q api; class url cli; class none gap;
```

---

## 6. Slice catalogue — input tables → output, with status

Each slice is the same Controller→Service→Repository seam over different tables.

```mermaid
flowchart LR
    subgraph IMPL["✅ Implemented (whole backend)"]
        direction TB
        m1["<b>creature·move·item·card·furniture</b><br/>dex/2-axis · learners · recipe/drops · pools · catalogue"]
        m2["<b>boss·trainer·camp·squadron</b><br/>battle-graph · party/where-found · targets/tasks · members"]
        m3["<b>map·quest·story</b><br/>spawns/shops/battles + map-graph · state-machine · scenes/cities"]
        m4["<b>mechanics·logicgraph·type·loc·meta·achievement·tutorial</b><br/>formulas/curves · BT/timeline/minigame · analytics/quirks · 8-lang · counts"]
    end

    subgraph PLAN["⏸ Deferred (blocked on data)"]
        direction TB
        p1["<b>asset</b> 🔴 resolver endpoint<br/>Addressables GUID→bundle cache not extracted"]
    end

    IMPL ~~~ PLAN

    classDef done fill:#D4EDDA,stroke:#28A745,color:#155724;
    classDef todo fill:#F8D7DA,stroke:#DC3545,color:#721c24,stroke-dasharray:4 3;
    class m1,m2,m3,m4 done; class p1 todo;
    style IMPL fill:#F2FBF4,stroke:#28A745,stroke-width:2px;
    style PLAN fill:#FDF2F3,stroke:#DC3545,stroke-width:2px;
```

---

## 7. Honest gaps (🔴 blocked on data extraction, not skipped)

```mermaid
flowchart LR
    g1["🔴 asset_guid<br/>Addressables GUID→bundle cache<br/>not extracted → two-hop incomplete<br/>(filesystem leg still serves art)"]
    g2["🔴 xp_level_exp<br/>AniCurve base sampler unresolved<br/>→ per-level exp not verifiable<br/>(curve metadata IS served)"]
    g3["🔴 minigame_prize<br/>prize tables opaque in fields<br/>→ no item_guid to map yet"]

    classDef gap fill:#F8D7DA,stroke:#DC3545,color:#721c24;
    class g1,g2,g3 gap;
```

> These are genuine extraction limits, documented and isolated — the rest of each
> slice works, and they light up the moment the missing source lands.
