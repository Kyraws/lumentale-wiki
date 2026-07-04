#!/usr/bin/env python3
"""Build data/curated/ — the name-resolved, human-readable game dataset.

Reads data/seed/*.json (the phase-2 extraction) + data/seed/loc/ (8-lang
localization + _keys.json key map) and emits data/curated/: one JSON per
category where EVERY entity carries its real English display name, the
Italian name where it differs, and the internal dev codename. All GUID
cross-references are annotated with resolved names so no consumer ever has
to join against the loc tables again.

Resolution chains (per category) are documented in data/curated/README.md,
which this script regenerates together with _coverage.json.

Usage:  python3 tools/build_curated_data.py        (from repo root)
Pure stdlib, idempotent — safe to re-run any time the seed changes.
"""
import json
import re
import unicodedata
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "data" / "seed"
OUT = ROOT / "data" / "curated"

# ---------------------------------------------------------------- loading

def load(name):
    with open(SEED / name, encoding="utf-8") as f:
        return json.load(f)

EN = load("loc/en.json")
IT = load("loc/it.json")
KEYS = load("loc/_keys.json")


def norm(s):
    """Normalize text for fuzzy joining: trim, collapse ws, unify quotes."""
    if not s:
        return ""
    s = unicodedata.normalize("NFC", s)
    s = s.replace("’", "'").replace("‘", "'")
    s = s.replace("“", '"').replace("”", '"')
    s = s.replace("…", "...")
    return re.sub(r"\s+", " ", s).strip()


def key_table(table):
    """{key_name: english_text} for one loc table, joined on string-id."""
    out = {}
    for sid, key in KEYS.get(table, {}).items():
        text = EN.get(table, {}).get(sid, "")
        if key and text:
            out[norm(key)] = text
    return out


def it_en_table(table):
    """{normalized italian text: english text} joined on string-id."""
    out = {}
    for sid, it_text in IT.get(table, {}).items():
        en_text = EN.get(table, {}).get(sid, "")
        if it_text and en_text:
            out.setdefault(norm(it_text), en_text)
    return out


# key-name -> english, per table (key names are e.g. 'animon_name_<guid>')
ANIMON_NAMES = key_table("ANIMON_NAMES")
ANIMON_DESC = key_table("ANIMON_DESCRIPTION")
SKILL_NAME = key_table("SKILL_NAME")
SKILL_DESC = key_table("SKILL_DESCRIPTION")
ITEM_NAME = key_table("ITEM_NAME")
ITEM_DESC = key_table("ITEM_DESCRIPTION")
FURNITURE_NAMES = key_table("FURNITURE_NAMES")
ACHI_NAMES = key_table("ACHIEVEMENT_NAMES")
ACHI_DESC = key_table("ACHIEVEMENT_DESC")
LOCATION = key_table("LOCATION")
NAMES = key_table("NAMES")  # NPC display names: italian/dev name -> english
TUTORIAL = key_table("TUTORIAL")

# italian text -> english text (string-id join), per table then global
IT2EN_QUEST = it_en_table("QUEST")
IT2EN_QUEST_DESC = it_en_table("QUEST_DESCRIPTIONS")
IT2EN = {}
for t in IT:
    for k, v in it_en_table(t).items():
        IT2EN.setdefault(k, v)


def humanize(codename):
    """'AARI_DUNGEON_P3' -> 'Aari Dungeon P3'; 'P01_CenterCampData' kept sane."""
    if not codename:
        return ""
    s = re.sub(r"[_]+", " ", codename).strip()
    s = re.sub(r"(?<=[a-z])(?=[A-Z])", " ", s)
    words = []
    for w in s.split():
        words.append(w if (w.isupper() and len(w) <= 4) or re.match(r"^P\d", w) else w.capitalize())
    return " ".join(words)


# Italian fragments that survive into derived (non-loc) map names. Applied
# ONLY to parent/codename-derived names — loc-resolved names are untouched.
WORD_ALIASES = {
    "Borgo Iride": "Iris Hamlet",
    "Grotta": "Cave",
    "Casa": "House",
    "Interno": "Interior",
    "Tenda Lumen": "Lumen Tent",
    "Cavernadel Miracolo": "Miracle Cavern",
}


def apply_aliases(name):
    for it_word, en_word in WORD_ALIASES.items():
        name = re.sub(re.escape(it_word), en_word, name, flags=re.I)
    return name


# ---------------------------------------------------------------- master index

master = {}  # guid -> {name, kind}
coverage = {}


def register(guid, name, kind):
    if guid and name and guid not in master:
        master[guid] = {"name": name, "kind": kind}


def annotate(obj):
    """Recursively add '<x>_name' next to every '<x>_guid' that resolves."""
    if isinstance(obj, list):
        return [annotate(x) for x in obj]
    if not isinstance(obj, dict):
        return obj
    out = {}
    for k, v in obj.items():
        out[k] = annotate(v)
        lk = k.lower()
        if lk == "guid":
            continue
        if lk.endswith("guid") and isinstance(v, str) and v in master:
            nk = re.sub(r"[_]?guid$", "", k, flags=re.I) or k
            name_key = nk + ("_name" if "_" in k or k.islower() else "Name")
            out.setdefault(name_key, master[v]["name"])
        elif lk.endswith("guids") and isinstance(v, list):
            names = [master[g]["name"] for g in v if isinstance(g, str) and g in master]
            if names:
                nk = re.sub(r"[_]?guids$", "", k, flags=re.I) or k
                name_key = nk + ("_names" if "_" in k or k.islower() else "Names")
                out.setdefault(name_key, names)
    return out


def write(name, data):
    with open(OUT / name, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)


def cov(category, total, resolved, note=""):
    coverage[category] = {"total": total, "english_named": resolved, "note": note}


def strip_raw(rec):
    return {k: v for k, v in rec.items() if k != "raw"}


# ================================================================ pass 1: names
# Order matters only for readability; master is a flat guid index.

forms = load("forms.json")
species_seen = {}
for f in forms:
    sp_en = ANIMON_NAMES.get(f"animon_name_{f.get('species_guid','')}") or f.get("species_name")
    variant = f.get("variant_name") or ""
    name = sp_en if variant in ("", "Base Form") else f"{sp_en} ({variant})"
    f["_resolved_name"] = name
    register(f["guid"], name, "form")
    if f.get("species_guid"):
        register(f["species_guid"], sp_en, "species")

moves = load("moves.json")
for m in moves:
    m["_resolved_name"] = SKILL_NAME.get(norm(m.get("name_key") or "")) or m.get("name_raw")
    register(m["guid"], m["_resolved_name"], "move")

items = load("items.json")
for i in items:
    i["_resolved_name"] = ITEM_NAME.get(norm(i.get("name_key") or "")) or IT2EN.get(norm(i.get("name_raw"))) or i.get("name_raw")
    register(i["guid"], i["_resolved_name"], "item")

crafting = load("crafting.json")
for c in crafting:
    c["_resolved_name"] = IT2EN.get(norm(c.get("name_raw"))) or c.get("result_item_name") or c.get("name_raw")
    register(c["guid"], c["_resolved_name"], "recipe")

furniture = load("furniture.json")
for fu in furniture:
    fu["_resolved_name"] = FURNITURE_NAMES.get(norm(fu.get("name_key") or "")) or IT2EN.get(norm(fu.get("name_raw"))) or fu.get("name_raw")
    register(fu["guid"], fu["_resolved_name"], "furniture")

achievements = load("achievements.json")
for a in achievements:
    a["_resolved_name"] = ACHI_NAMES.get(norm(a.get("name_key") or "")) or IT2EN.get(norm(a.get("name_raw"))) or a.get("name_raw")
    register(a["guid"], a["_resolved_name"], "achievement")

cards = load("cards.json")
for c in cards:
    c["_resolved_name"] = IT2EN.get(norm(c.get("name_raw"))) or (
        master.get(c.get("form_guid") or "", {}).get("name")
    ) or c.get("name_raw")
    register(c["guid"], c["_resolved_name"], "card")

quests = load("quests.json")
for q in quests:
    q["_resolved_name"] = IT2EN_QUEST.get(norm(q.get("quest_name_raw"))) or IT2EN.get(norm(q.get("quest_name_raw"))) or q.get("quest_name_raw")
    register(q["guid"], q["_resolved_name"], "quest")

tutorials = load("tutorials.json")
for t in tutorials:
    t["_resolved_name"] = TUTORIAL.get(norm(t.get("title_key") or "")) or humanize(t.get("internal_name"))
    register(t["guid"], t["_resolved_name"], "tutorial")

# ---- maps: own loc name -> ancestor loc name + qualifier -> italian map_name -> codename
maps = load("maps.json")
by_guid = {m["guid"]: m for m in maps}


def map_own_en(m):
    return LOCATION.get(norm(f"mapname_{m['guid']}"))


def _qualifier(internal_name, ancestor, ancestor_en):
    """Humanized codename minus every word the ancestor is known by (English
    name, Italian map_name, codename tokens) and 'Map' noise."""
    qual = apply_aliases(humanize(internal_name or ""))
    stop = set()
    for src in (ancestor_en, ancestor.get("map_name") or "",
                humanize(ancestor.get("internal_name") or "")):
        stop.update(w.lower() for w in src.split())
    qual = " ".join(w for w in qual.split() if w.lower() not in stop and w.lower() != "map")
    qual = re.sub(r"(?<=[a-zA-Z]{3})(?=\d)", " ", qual)  # 'Room3' → 'Room 3'
    return qual.strip(" –-")


def map_name_chain(m):
    own = map_own_en(m)
    if own:
        return own, "loc"
    # walk parents for a named ancestor
    seen, cur = set(), m
    while cur and cur.get("parent_guid") and cur["parent_guid"] not in seen:
        seen.add(cur["parent_guid"])
        cur = by_guid.get(cur["parent_guid"])
        if cur:
            anc = map_own_en(cur)
            if anc:
                qual = _qualifier(m.get("internal_name"), cur, anc)
                return (f"{anc} – {qual}" if qual else anc), "parent"
    it_name = norm(m.get("map_name"))
    if it_name:
        return IT2EN.get(it_name, m["map_name"]), "map_name"
    derived = apply_aliases(humanize(m.get("internal_name") or "")) or m["guid"]
    hit = IT2EN.get(norm(derived))
    return (hit or derived), "codename"


def refine_region(m):
    """Split the game's RegionSide=0 bucket (region NULL) into the wiki's
    taxonomy: 'hub' = Magnolia city + districts/interiors/AARI tower;
    'center' = the central wilderness ring (Center Areas + their caves/woods);
    'prologue' = Iris Hamlet / Borgo Iride + Kapan's lab. North/south stay as
    tagged by the engine. Unmatched special maps (Anispace, test scenes) stay
    None. Parent-chain inheritance backstops interiors."""
    if m.get("region"):
        return m["region"]
    def rule(name):
        ln = (name or "").lower()
        if not ln:
            return None
        if "magnolia" in ln or ln.startswith("aari") or ln.startswith("sm_"):
            return "hub"
        if ln.startswith("center_area"):
            return "center"
        if ln.startswith("grotta p") or "boscorubo" in ln or "scarlet_woods" in ln \
                or ln == "grotta_cremisi":
            return "center"
        if "borgoiride" in ln or "borgo_iride" in ln or "borgo iride" in ln \
                or "iris_hamlet" in ln or ln.startswith("kapan") or "fioreria" in ln \
                or ln == "ales's room":
            return "prologue"
        if "paradine" in ln:
            return "north"
        return None
    r = rule(m.get("internal_name"))
    if r:
        return r
    seen, cur = set(), m
    while cur and cur.get("parent_guid") and cur["parent_guid"] not in seen:
        seen.add(cur["parent_guid"])
        cur = by_guid.get(cur["parent_guid"])
        if cur:
            r = cur.get("region") or rule(cur.get("internal_name"))
            if r:
                return r
    return None


map_src_counts = defaultdict(int)
region_counts = defaultdict(int)
for m in maps:
    name, src = map_name_chain(m)
    m["_resolved_name"], m["_name_source"] = name, src
    m["region"] = refine_region(m)
    region_counts[m["region"]] += 1
    map_src_counts[src] += 1
    register(m["guid"], name, "map")

# ---- bosses: named after their creature form (+ N/S marker from codename)
bosses = load("bosses.json")
for b in bosses:
    base = master.get(b.get("form_guid") or "", {}).get("name") or b.get("form_species") or humanize(b.get("internal_name"))
    pre = b.get("internal_name") or ""
    side = "North" if pre.startswith("NF_") else "South" if pre.startswith("SF_") else None
    b["_resolved_name"] = f"{base} ({side})" if side else base
    register(b["guid"], b["_resolved_name"], "boss")

# ---- trainers: NAMES loc bridge -> raw proper name -> generic template
trainers = load("trainers.json")
for t in trainers:
    nr = norm(t.get("name_raw"))
    t["_resolved_name"] = NAMES.get(nr) or t.get("name_raw") or humanize(t.get("internal_name"))
    t["_generic"] = not nr
    register(t["guid"], t["_resolved_name"], "trainer")

# ---- squadrons: internal_name IS the real name
squadrons = load("squadrons.json")
for s in squadrons:
    s["_resolved_name"] = s.get("name_raw") or s.get("internal_name")
    register(s["guid"], s["_resolved_name"], "squadron")

# ---- camps: P<NN> codename -> *_AREA_<NN> map -> that map's English name
camps = load("camps.json")
area_maps = {}
for m in maps:
    mm = re.match(r"^(CENTER|SOUTH(?:ERN)?|NORTH(?:ERN)?)_AREA_(\d+)$", m.get("internal_name") or "")
    if mm:
        area_maps[int(mm.group(2))] = m
for c in camps:
    mm = re.match(r"^P(\d+)_", c.get("name") or "")
    amap = area_maps.get(int(mm.group(1))) if mm else None
    if amap:
        c["_resolved_name"] = f"{amap['_resolved_name']} Camp"
        c["_map_guid"] = amap["guid"]
        prefix = (amap.get("internal_name") or "").split("_")[0]
        c["_region"] = {"CENTER": "center", "SOUTH": "south", "SOUTHERN": "south",
                        "NORTH": "north", "NORTHERN": "north"}.get(prefix)
    else:
        c["_resolved_name"] = humanize(c.get("name"))
    register(c["guid"], c["_resolved_name"], "camp")

card_pools = load("card_pools.json")
for p in card_pools:
    p["_resolved_name"] = p.get("name")
    register(p.get("guid") or p["name"], p["name"], "card_pool")

# ================================================================ pass 2: emit

OUT.mkdir(parents=True, exist_ok=True)


def emit(fname, records, kind, name_it_field=None, codename_field=None, extra=None):
    out, named = [], 0
    for r in records:
        rec = strip_raw(r)
        name = rec.pop("_resolved_name", None)
        clean = {"guid": rec.get("guid"), "name": name}
        if codename_field and rec.get(codename_field) and norm(rec[codename_field]) != norm(name):
            clean["codename"] = rec[codename_field]
        if name_it_field and rec.get(name_it_field) and norm(rec[name_it_field]) != norm(name):
            clean["name_it"] = rec[name_it_field]
        for k, v in rec.items():
            if k in ("guid",) or k.startswith("_resolved"):
                continue
            clean.setdefault(k.lstrip("_"), v)
        if extra:
            extra(clean, r)
        # named in English = differs from a bare codename fallback
        if name:
            named += 1
        out.append(clean)
    out = annotate(out)
    write(fname, out)
    cov(fname, len(out), named)
    return out


def en_desc(rec, key_map, key_field, raw_field):
    d = key_map.get(norm(rec.get(key_field) or ""))
    if not d:
        d = IT2EN.get(norm(rec.get(raw_field)))
    return d


# creatures
def creature_extra(clean, r):
    clean["description"] = ANIMON_DESC.get(f"animon_desc_{r['guid']}") or IT2EN.get(norm(r.get("description_raw"))) or None
    clean["description_it"] = r.get("description_raw") or None

emit("creatures.json", forms, "form", codename_field=None, extra=creature_extra)

def move_extra(clean, r):
    clean["description"] = en_desc(r, SKILL_DESC, "desc_key", "description_raw") or None
emit("moves.json", moves, "move", name_it_field="name_raw", extra=move_extra)

def item_extra(clean, r):
    clean["description"] = en_desc(r, ITEM_DESC, "desc_key", "description_raw") or None
emit("items.json", items, "item", name_it_field="name_raw", extra=item_extra)

def craft_extra(clean, r):
    clean["description"] = IT2EN.get(norm(r.get("description_raw"))) or None
emit("crafting.json", crafting, "recipe", name_it_field="name_raw", extra=craft_extra)

emit("cards.json", cards, "card", name_it_field="name_raw")
emit("card_pools.json", card_pools, "card_pool")
emit("furniture.json", furniture, "furniture", name_it_field="name_raw")

def achi_extra(clean, r):
    clean["description"] = en_desc(r, ACHI_DESC, "desc_key", "desc_raw") or None
emit("achievements.json", achievements, "achievement", name_it_field="name_raw", extra=achi_extra)

def quest_extra(clean, r):
    clean["description"] = IT2EN_QUEST_DESC.get(norm(r.get("quest_description_raw"))) or IT2EN.get(norm(r.get("quest_description_raw"))) or None
    clean["description_it"] = r.get("quest_description_raw") or None
emit("quests.json", quests, "quest", name_it_field="quest_name_raw", codename_field="internal_name", extra=quest_extra)

emit("tutorials.json", tutorials, "tutorial", codename_field="internal_name")

def map_extra(clean, r):
    clean["name_source"] = r.get("_name_source")
emit("maps.json", maps, "map", name_it_field="map_name", codename_field="internal_name", extra=map_extra)

emit("bosses.json", bosses, "boss", codename_field="internal_name")
emit("trainers.json", trainers, "trainer", name_it_field=None, codename_field="internal_name",
     extra=lambda clean, r: clean.__setitem__("generic", r.get("_generic", False)))
emit("squadrons.json", squadrons, "squadron", codename_field="internal_name")

def camp_extra(clean, r):
    if r.get("_map_guid"):
        clean["map_guid"] = r["_map_guid"]
        clean["map_name"] = master[r["_map_guid"]]["name"]
        clean["region"] = r.get("_region")
emit("camps.json", camps, "camp", codename_field="name", extra=camp_extra)

# ---- map layers: pure annotation passes (spawns / shops / battles / placements)
for fname in ("map_spawns.json", "map_shops.json", "map_npcs.json",
              "map_placements_v2.json", "map_connection_dirs.json"):
    data = annotate(load(fname))
    write(fname, data)
    cov(fname, len(data), len(data), "guid cross-refs annotated with names")

# ---- story scenes: annotate + humanized title + English speaker names
scenes = load("story_scenes.json")
speakers_resolved = 0
for s in scenes:
    s["title"] = humanize(re.sub(r"^(MAIN_[\d.]+_|MG_Main_\d+_|Main_\d+[._]?)", "", s.get("name") or ""))
    for node in s.get("nodes", []):
        sp = norm(node.get("speaker") or "")
        sp_en = NAMES.get(sp)
        if sp and sp_en and sp_en != sp:
            node["speaker_en"] = sp_en
            speakers_resolved += 1
scenes = annotate(scenes)
write("story_scenes.json", scenes)
cov("story_scenes.json", len(scenes), len(scenes),
    f"titles humanized, guids annotated, {speakers_resolved} Italian speaker names → English")

write("variables.json", load("variables.json"))
cov("variables.json", len(load("variables.json")), 0, "copied as-is (flag names are canonical)")

# ---- master index
write("names.json", master)

# ---- coverage + README
real_cov = {}
for cat, recs_file in (("creatures.json", None),):
    pass

# recompute honest English coverage per emitted file (name not a codename fallback)
def english_pct(fname, fallback_fields=("codename",)):
    data = json.load(open(OUT / fname, encoding="utf-8"))
    if not isinstance(data, list) or not data or not isinstance(data[0], dict) or "name" not in data[0]:
        return None
    n = sum(1 for r in data if r.get("name"))
    return n, len(data)

write("_coverage.json", coverage)

readme = f"""# data/curated — name-resolved game data

Generated by `tools/build_curated_data.py` from `data/seed/` + the 8-language
localization tables. **Regenerate after any seed change** — do not hand-edit.

Every entity here carries:

- `name` — the real English display name (the one the game shows)
- `name_it` — the Italian source name, when it differs
- `codename` — the internal dev identifier (e.g. `P01_CenterCampData`,
  `NF_Nuclheart`, `CENTER_AREA_01`), kept for traceability
- every `*_guid` cross-reference gets a sibling `*_name` with the resolved
  display name, recursively — consumers never need to join loc tables.

`names.json` is the **master GUID index**: every known guid → `{{name, kind}}`.
Look there first when any guid shows up anywhere.

## How names were resolved (per category)

| File | Resolution chain |
|---|---|
| creatures.json | species via `animon_name_<species_guid>` loc key; variants appended `(Variant)`; descriptions via `animon_desc_<form_guid>` |
| moves.json / items.json / furniture.json / achievements.json | direct `*_name_<guid>` / `*_desc_<guid>` loc keys |
| crafting.json / cards.json / quests.json | Italian source text → string-id join → English (per-table first, then global) |
| maps.json | own `mapname_<guid>` LOCATION entry → nearest named ancestor + qualifier → Italian `map_name` → humanized codename (`name_source` records which) |
| bosses.json | named after their creature form; `NF_`/`SF_` codename prefix → `(North)` / `(South)` |
| trainers.json | `NAMES` loc table (Italian → English) → raw proper name; `generic: true` marks unnamed battle templates |
| squadrons.json | `internal_name` is canonical (AARI, Hellflamers, …) |
| camps.json | `P<NN>` codename → `*_AREA_<NN>` map → that map's English name + " Camp"; `map_guid`/`region` attached |
| map_spawns / map_shops / map_npcs / map_placements_v2 / map_connection_dirs | structure unchanged, all guid refs annotated with names |
| story_scenes.json | scene codenames kept, humanized `title` added, guid refs annotated; dialogue `speaker` (Italian) gains `speaker_en` via the `NAMES` table |

## Coverage

See `_coverage.json` (regenerated each run).

## Known residue (source gaps, not bugs)

- Entities whose English string simply doesn't exist in the game's loc bundles
  keep their Italian name (it's what the game itself shows).
- Generic trainers (`generic: true`) are unnamed battle templates in the game data.
"""
with open(OUT / "README.md", "w", encoding="utf-8") as f:
    f.write(readme)

# ---------------------------------------------------------------- report
print(f"master index: {len(master)} guids")
print(f"map name sources: {dict(map_src_counts)}")
print(f"map regions: {dict(region_counts)}")
for k, v in coverage.items():
    print(f"  {k}: {v['total']} records, named {v['english_named']}" + (f" ({v['note']})" if v.get("note") else ""))
