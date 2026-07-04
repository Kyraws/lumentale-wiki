// Response shapes for backend-v2. Optional fields are OMITTED (not null) by the
// serializer — always check key presence, not value. See ARCHITECTURE.md §3.2.

export type Region = 'north' | 'south'

export interface CreatureSummary {
  guid: string
  species: string
  variant: string
  dex: number
  emo?: string // emotion type
  ele?: string // elemental type
  variants: number
  hasLost: boolean
  menuArt?: string
  front?: string
  regions: Region[]
}

export interface StatGrade {
  stat: string
  grade: string
  pct: number
  rank?: number
}

export interface SpawnRef {
  guid: string
  name: string
  region?: string | null
  interior: boolean
  tile?: string | null
  levelMin?: number
  levelMax?: number
}

export interface UsedBy {
  kind: 'trainer' | 'boss'
  guid: string
  name: string
  level?: number
}

export interface EvoNode {
  formGuid: string
  species: string
  variant: string
  menuArt?: string | null
  current: boolean
  methodClass?: string
  level?: string
}

/** v3 two-axis type chart (elemental per-form + emotion 5×5). */
export interface TypeChart {
  emotion?: string
  elemental: { attacker: string; effectiveness: string }[] // WEAKNESS|RESISTANCE|NORMAL|IMMUNITY
  emotionOffense: { other: string; multiplier: number }[]
  emotionDefense: { other: string; multiplier: number }[]
}

export interface LearnsetEntry {
  moveGuid: string
  name: string // English-resolved
  type?: string // elemental type label
  level?: number
  method?: string // "Level Up" | "Other"
}

export interface CreatureDetail {
  form: Record<string, unknown>
  // English-resolved sibling fields (v3.1 — no more parsing raw Italian)
  species?: string
  variant?: string
  dex?: number
  ele?: string
  description?: string
  learnset?: LearnsetEntry[]
  regions: Region[]
  statGrades: StatGrade[]
  typeChart: TypeChart
  spawns: SpawnRef[]
  usedBy: UsedBy[]
  evolvesFrom: EvoNode[]
  evoChain: EvoNode[][]
}

export interface MapSummary {
  guid: string
  name: string
  displayName?: string | null
  mapName?: string | null
  region?: string | null
  interior: boolean
  spawns: number
  tile?: string | null
}

export interface Pos {
  x: number | null
  y: number | null
  z: number | null
}

export interface SpawnForm {
  guid: string
  species: string
  variant: string
  dex: number
  emo?: string
  ele?: string
  menuArt?: string | null
  levelMin?: number
  levelMax?: number
}

export interface ShopEntry {
  kind: 'item' | 'furniture' | 'move' | 'recipe' | 'crafting'
  name?: string | null
  icon?: string | null
  price?: number
  limit?: number
}

export interface Exit {
  name?: string | null
  targetGuid?: string | null
  targetName?: string | null
  targetRegion?: string | null
  direction?: number
  resolvedBy?: string | null
  pos?: Pos | null
  targetPos?: Pos | null
}

export interface Pickup {
  name?: string | null
  amount?: number
  itemGuid?: string | null
  itemName?: string | null
  icon?: string | null
  pos?: Pos | null
}

export interface MapDetail {
  guid: string
  internalName: string
  displayName?: string | null
  mapName?: string | null
  region?: string | null
  interior: boolean
  tile?: string | null
  spawns: SpawnForm[]
  spawnPoints: { name?: string | null; pos?: Pos | null; forms: SpawnForm[] }[]
  shops: { npc?: string | null; graph?: string | null; pos?: Pos | null; entries: ShopEntry[] }[]
  battles: {
    npc: string
    pos?: Pos | null
    fights: { kind?: string | null; guid?: string | null; name?: string | null; forms: SpawnForm[] }[]
  }[]
  exits: Exit[]
  pickups: Pickup[]
  /** Connected maps from the game connectivity graph — includes seamless border crossings that aren't doors/exits. */
  connections: Connection[]
  /** Story-state variants of this location (≥2 means show a state switcher); empty otherwise. */
  stateGroup: StateVariant[]
  /** The tile's world-space bounds; markers project onto the tile via these. Absent on maps with no menu-map geometry.
   *  tileCenter/tileSpan are the empirically calibrated frame for the few bakes rendered off the default framing. */
  bounds?: {
    offsetX: number; offsetZ: number; sizeX: number; sizeZ: number; tileWorldSize: number
    tileCenterX?: number | null; tileCenterZ?: number | null
    tileSpanX?: number | null; tileSpanZ?: number | null
  }
}

export interface Connection {
  guid: string
  internalName: string
  displayName?: string | null
  mapName?: string | null
  region?: string | null
  interior: boolean
  /** "both" | "out" | "in" */
  direction: string
  /** A door/teleport to this map also exists (also listed under Exits). */
  viaExit: boolean
  /** Flag gates on this connection; empty = always open. */
  conditions: string[]
  /** Crossing points on this map's tile: every door entrance (exact), or one bearing pin for a seamless border. Empty when ungeocodable. */
  crossings: Pos[]
  /** >1 when this entry stands for a location that has several story-states (collapsed into one). */
  stateCount: number
}

export interface StateVariant {
  guid: string
  internalName: string
  displayName?: string | null
  /** Human story-state label (e.g. "Before", "Boss event", "After"). */
  label: string
  /** True for the state currently being viewed. */
  current: boolean
}

export interface StoryCity {
  region: string
  track: 'prologue' | 'south' | 'north' | 'hub' | 'other'
  scenes: SceneLite[]
  quests: QuestSummary[]
}

export interface SceneLite {
  sceneId: string
  name: string
  region?: string | null
  track?: string | null
  chapter?: number
  mainNum?: number
  dialogue: number
}

export interface QuestSummary {
  guid: string
  name: string
  title?: string | null
  giver?: string | null
  type?: number // 0=Main, 1=Side, 2=Center task
  nodes: number
  city?: string
  track?: string
}

export interface MetaCounts {
  [table: string]: number
}

export interface MoveSummary {
  guid: string
  name: string
  /** English-resolved flavour text (falls back to Italian when no loc exists). */
  description?: string
  power?: number
  accuracy?: number
  cost?: number
  category?: string | null
  type?: string | null
  target?: string | null
  aoe?: string | null
  learners: number
  /** Dex-ordered preview (capped) of forms that learn it; art = /data/forms/<guid>/menu.png */
  learnerGuids?: string[]
  /** Internal implementation skill (DoT/EoT tick, charge stage, dev test) — hidden from the list. */
  system?: boolean
}

export interface MoveLearner {
  guid: string
  species: string
  variant: string
  dex: number
  level?: number
  menuArt?: string | null
}

export interface ItemSummary {
  guid: string
  name: string
  nameKey?: string | null
  type?: string | null
  material?: string | null
  price?: number
  maxStack?: number
  icon?: string | null
  /** Handed out by a story "Give Item" event. */
  storyGiven?: boolean
}

export interface ItemDetail extends ItemSummary {
  descKey?: string | null
  /** English-resolved flavour text. */
  description?: string | null
  effects: { class?: string; data?: Record<string, unknown> }[]
  recipe?: {
    successRate?: number
    preferredActor?: string | null
    ingredients: { name?: string | null; guid?: string | null; amount?: number }[]
  }
  droppedBy: { guid: string; species: string; variant: string; min?: number; max?: number; menuArt?: string | null }[]
  soldAt: { mapGuid: string; mapName: string; shop?: string | null; npc?: string | null; price?: number }[]
  foundOn: { mapGuid: string; mapName: string; spots: number; total: number }[]
  /** Recipes this item is an ingredient of (crafted result + amount needed). */
  usedIn: { resultGuid?: string | null; resultName?: string | null; amount?: number }[]
  /** Story scenes that hand the player this item. */
  givenIn: { sceneId: string; sceneName: string }[]
}

export interface PartyMember {
  ord: number
  formGuid: string
  species: string
  variant: string
  level?: number
  emo?: string | null
  ele?: string | null
  nickname?: string | null
  item?: string | null
  quirkClass?: string | null
  menuArt?: string | null
}

export interface TrainerSummary {
  guid: string
  name: string
  display?: string | null
  levelCap?: number
  money?: number
  idle?: string | null
  party: PartyMember[]
}

export interface TrainerDetail extends TrainerSummary {
  rank?: number
  lumenClass?: string | null
  foundOnMaps: { guid: string; name: string; region?: string | null; tile?: string | null }[]
  foundInScenes: { sceneId: string; name: string; region?: string | null; track?: string | null }[]
  squadrons: { guid: string; name: string; role?: string | null }[]
}

export interface TypeOffense {
  type: string
  superEffective: number
  neutral: number
  resisted: number
  immune: number
}

export interface TypeCoverage {
  type: string
  weakness: string[]
  normal: string[]
  resistance: string[]
  immunity: string[]
}

export interface Defender {
  guid: string
  species: string
  variant: string
  emo?: string
  ele?: string
  score: number
  weak: number
  resist: number
  immune: number
  def: number
  spd: number
}

export interface Quirk {
  class: string
  name?: string
  description?: string
  owners: {
    guid: string
    species: string
    variant: string
    hidden: boolean
    /** Dex number of the bearer form (for ordering / tooltip). */
    dex?: number | null
    /** Menu sprite URL, resolved like the dex grid (`/data/forms/<guid>/menu.png`). */
    menuArt?: string | null
  }[]
}

// ----- v3 new-page shared list types (detail types live in their page files,
// inspected from the live API). -----

export interface BossSummary {
  guid: string
  name: string
  display?: string | null
  level?: number
  ele?: string
  emotion?: string
  expGiven?: number
  extraHealthBars?: number
  originSpecies?: string | null
  hasGraph: boolean
}

export interface CardSummary {
  guid: string
  name?: string | null
  rarity?: string | null
  ele?: string | null
  formGuid?: string | null
  art?: string | null
  pools?: number
  /** The game's per-card holo pipeline (GameCardData): foil texture, artwork
   *  mask, foil tiling. Present on the 34 holo-finish cards. */
  holo?: string | null
  mask?: string | null
  holoTilingX?: number | null
  holoTilingY?: number | null
}

export interface FurnitureSummary {
  guid: string
  name?: string | null
  nameKey?: string | null
  rarity?: string | null
  price?: number
  size?: string
  carpet?: boolean
  icon?: string | null
}

export interface CampSummary {
  guid: string
  name?: string | null
  effectClass?: string | null
  influence?: number
  lumenAmount?: number
}

export interface SquadronSummary {
  guid: string
  name?: string | null
  rank?: number
  memberCount?: number
  logo?: string | null
}

export interface AchievementSummary {
  guid: string
  name?: string | null
  rarity?: string | null
  rarityCode?: number
  steps?: number
  icon?: string | null
}

export interface TutorialSummary {
  guid: string
  internalName?: string | null
  titleKey?: string | null
  pageCount?: number
}

// Mechanics
export interface FormulaSummary {
  key: string
  name: string
  signature?: string | null
  confidence?: string | null
}
export interface XpCurveSummary {
  curveType: number
  name?: string | null
  kind?: string | null
  expAt50?: number
  expAt100?: number
}
export interface DifficultyScalar {
  difficulty: string
  direction: string
  multiplier: number
}
export interface MechanicsOverview {
  formulas: FormulaSummary[]
  xpCurves: XpCurveSummary[]
  difficulty: DifficultyScalar[]
  constantCount: number
}

// Logic graphs
export interface BehaviorTreeSummary {
  pathId: number
  behaviorName?: string | null
  objectName?: string | null
  bundle?: string | null
  kind?: string | null
  taskCount?: number
}
export interface TimelineSummary {
  directorPathId: number
  timelineName?: string | null
  gameobject?: string | null
  bundle?: string | null
  nTracks?: number
  nClips?: number
  crossbundle?: boolean
}
export interface MinigameSummary {
  pathId: number
  className: string
  bundle?: string | null
  gameobjectName?: string | null
}
