// ============================================================================
// Logic-graph label decoding — turns raw engine identifiers into plain English.
//
// The game's behavioural logic was extracted straight from the Unity bundles,
// so node/track/clip names are the original BehaviorDesigner / Unity-Timeline
// class names (e.g. "ParallelSelector", "WwiseSkillEventClip"). On their own
// they read as noise to a wiki reader. These tables map each known identifier to
// a human verb + a one-line gloss, so the page can render an outline that reads
// like a description of what the creature/cutscene actually does.
//
// Anything not in a table falls back to a spaced-out version of the raw name —
// never hidden, never fabricated.
// ============================================================================

// ---- BEHAVIOR-TREE TASK TYPES ----------------------------------------------
// Categories (from the extractor): entry | composite | decorator | conditional
// | action. Composites/decorators are *control flow*; conditionals are *checks*;
// actions are *what the creature does*.

export interface TaskInfo {
  /** Short human label shown as the node title. */
  verb: string
  /** One-line plain-language explanation. */
  gloss: string
}

// Strip the namespace so "BehaviorDesigner.Runtime.Tasks.Tutorials.SeekPlayer"
// matches the bare "SeekPlayer" key.
export function bareTaskName(typeOrLabel?: string | null): string {
  if (!typeOrLabel) return ''
  const parts = String(typeOrLabel).split('.')
  return parts[parts.length - 1]
}

const TASK_INFO: Record<string, TaskInfo> = {
  // --- entry / control flow (composites & decorators) ---
  EntryTask: { verb: 'Start', gloss: 'Where the behaviour begins each tick.' },
  Sequence: { verb: 'Do in order', gloss: 'Run each child in turn; stop if one fails.' },
  Selector: { verb: 'Try until one works', gloss: 'Run children in order until one succeeds.' },
  ParallelSelector: {
    verb: 'Do at the same time',
    gloss: 'Run all children together until one succeeds.',
  },
  Repeater: { verb: 'Repeat', gloss: 'Keep repeating its child (usually forever).' },
  PickRandomBehavior: { verb: 'Pick at random', gloss: 'Choose one of its children randomly.' },

  // --- conditionals (checks / senses) ---
  CanSeePlayer: { verb: 'Can it see you?', gloss: 'Succeeds when the player is within line of sight.' },
  CanHearPlayer: { verb: 'Can it hear you?', gloss: 'Succeeds when the player is within hearing range.' },
  IsCloseToObject: { verb: 'Is something near?', gloss: 'Succeeds when a target is within range.' },
  CheckWeather: { verb: 'Check the weather', gloss: 'Branches on the current weather (rain, snow, …).' },
  CheckDayTime: { verb: 'Check time of day', gloss: 'Branches on whether it is day or night.' },
  CheckPartyHealth: { verb: 'Check your party HP', gloss: 'Branches on how healthy your team is.' },
  CheckFoodEffect: { verb: 'Check food lure', gloss: 'Reacts to bait / food effects in the world.' },
  CompareSharedBool: { verb: 'Check a flag', gloss: 'Compares an internal on/off flag.' },

  // --- actions (movement / combat / fx) ---
  SeekPlayer: { verb: 'Chase you', gloss: 'Move toward the player.' },
  SeekObject: { verb: 'Move toward target', gloss: 'Move toward a specific object.' },
  TurnTowardsPlayer: { verb: 'Turn to face you', gloss: 'Rotate to look at the player.' },
  AttackPlayer: { verb: 'Attack you', gloss: 'Initiate an attack on the player.' },
  ChangeAnimonSpeed: { verb: 'Change its speed', gloss: 'Speed up or slow down (often weather-driven).' },
  SetSharedBool: { verb: 'Set a flag', gloss: 'Flip an internal on/off flag.' },
  Wait: { verb: 'Wait', gloss: 'Pause for a moment.' },
  Instantiate: { verb: 'Spawn something', gloss: 'Create an object (effect, projectile, …).' },
  Destroy: { verb: 'Remove something', gloss: 'Destroy a spawned object.' },
  RepelBase: { verb: 'Repel (basic)', gloss: 'Flee from the player — basic Repel item logic.' },
  RepelPlus: { verb: 'Repel (Plus)', gloss: 'Flee from the player — Repel+ item logic.' },
  BrickleHide: { verb: 'Hide (Brickle)', gloss: "Brickle's signature hide-in-shell move." },
  MuribangExplosion: { verb: 'Self-destruct (Muribang)', gloss: "Muribang's signature explosion." },
}

export function taskInfo(typeOrLabel?: string | null): TaskInfo {
  const bare = bareTaskName(typeOrLabel)
  if (TASK_INFO[bare]) return TASK_INFO[bare]
  return { verb: spaceCamel(bare) || 'Task', gloss: '' }
}

export type TaskCategory = 'entry' | 'composite' | 'decorator' | 'conditional' | 'action'

export const CATEGORY_LABEL: Record<TaskCategory, string> = {
  entry: 'start',
  composite: 'flow',
  decorator: 'flow',
  conditional: 'check',
  action: 'action',
}

// Simplify the 5 raw categories to the 3 a reader cares about.
export function categoryRole(cat?: string | null): 'flow' | 'check' | 'action' | 'start' {
  if (cat === 'conditional') return 'check'
  if (cat === 'action') return 'action'
  if (cat === 'entry') return 'start'
  return 'flow'
}

// ---- TIMELINE TRACK TYPES --------------------------------------------------
// Tracks are grouped into a few human buckets so a 60-track VFX timeline reads
// as "camera / animation / effects / audio" rather than 60 cryptic rows.

export type TrackBucket = 'camera' | 'animation' | 'effects' | 'audio' | 'ui' | 'control' | 'other'

export const TRACK_BUCKET_LABEL: Record<TrackBucket, string> = {
  camera: 'Camera',
  animation: 'Animation',
  effects: 'Effects',
  audio: 'Audio',
  ui: 'Screen / UI',
  control: 'Timing & control',
  other: 'Other',
}

export const TRACK_BUCKET_GLOSS: Record<TrackBucket, string> = {
  camera: 'How the camera moves and frames the action.',
  animation: 'Character / creature poses and movement.',
  effects: 'Visual effects — impacts, particles, slow-motion.',
  audio: 'Sound effects and music cues.',
  ui: 'On-screen overlays, fades and image alpha.',
  control: 'Activation and timing of other elements.',
  other: 'Uncategorised tracks.',
}

// Drop the trailing " (1)", " (2)", "2" disambiguators Unity appends.
function baseTrackName(name?: string | null): string {
  return String(name || '')
    .replace(/\s*\(\d+\)\s*$/, '')
    .replace(/\s*\d+$/, '')
    .trim()
}

export function trackBucket(trackType?: string | null): TrackBucket {
  const n = baseTrackName(trackType).toLowerCase()
  if (!n) return 'other'
  if (n.includes('cinemachine') || n.includes('fov') || n.includes('camera')) return 'camera'
  if (n.includes('wwise') || n.includes('audio') || n.includes('sound') || n.includes('music')) return 'audio'
  if (
    n.includes('vfx') ||
    n.includes('impact') ||
    n.includes('timescale') ||
    n.includes('post process') ||
    n.includes('postprocess') ||
    n.includes('buff')
  )
    return 'effects'
  if (n.includes('image') || n.includes('alpha') || n.includes('ui')) return 'ui'
  if (n.includes('activation')) return 'control'
  if (
    n.includes('animation') ||
    n.includes('attack') ||
    n.includes('movement') ||
    n.includes('weight') ||
    n.includes('monster') ||
    n.includes('animon')
  )
    return 'animation'
  return 'other'
}

// A friendlier per-track label.
export function trackLabel(trackType?: string | null): string {
  const base = baseTrackName(trackType)
  const map: Record<string, string> = {
    'Cinemachine Track': 'Camera move',
    'FOV Change Track': 'Camera zoom (FOV)',
    UserAnimationTrack: 'Your creature — animation',
    UserAttackTrack: 'Your creature — attack',
    TargetAnimationTrack: 'Target — animation',
    TargetAttackTrack: 'Target — attack',
    'Animation Track': 'Animation',
    'Activation Track': 'Show / hide object',
    'VFX Impact Track': 'Impact effect',
    'VFX TimeScale Track': 'Slow-motion effect',
    VFX_Timescale: 'Slow-motion effect',
    'Post Process Weight Track': 'Screen colour grade',
    'Wwise Skill Event Track': 'Sound / music cue',
    BuffActivationTrack: 'Buff visual',
    'Image Alpha Track': 'Screen fade',
    'SecondaryImpact Track': 'Secondary impact',
  }
  return map[base] || spaceCamel(base) || 'Track'
}

// ---- TIMELINE CLIP TYPES ---------------------------------------------------
// Unity appends "(Clone)" (sometimes several). Strip them, then map to English.
export function cleanClipType(t?: string | null): string {
  return String(t || '').replace(/(\(Clone\))+$/, '').trim()
}

const CLIP_LABEL: Record<string, string> = {
  ActivationPlayableAsset: 'Show / hide',
  MonsterAnimationClip: 'Creature animation',
  MonsterCommonEffectClip: 'Creature effect',
  SideWeightClip: 'Lean / weight shift',
  WwiseSkillEventClip: 'Sound cue',
  VFXImpactImpulseClip: 'Impact burst',
  VFXTimeScaleImpulseClip: 'Slow-motion burst',
  MovementControlClip: 'Movement',
  PostProcessWeightClip: 'Colour grade',
  FOVChangeClip: 'Camera zoom',
  ImageAlphaClip: 'Screen fade',
}

export function clipLabel(t?: string | null): string {
  const c = cleanClipType(t)
  return CLIP_LABEL[c] || spaceCamel(c) || 'Clip'
}

// ---- MINIGAMES -------------------------------------------------------------
// Map the engine class → a fan-facing name, where it is played, and what it is.

export interface MinigameInfo {
  name: string
  where: string
  blurb: string
  internal?: boolean
}

export const MINIGAME_INFO: Record<string, MinigameInfo> = {
  LanpitMinigame: {
    name: 'Lanpit Shooter',
    where: 'Voltar arcade',
    blurb: 'Shoot the Lanpit and manage the rising water level for a high score.',
  },
  LobstrikeMinigame: {
    name: 'Lobstrike Rhythm',
    where: 'Voltar arcade',
    blurb: 'Hit the left and right Lobstrike in time with the tempo.',
  },
  TwinklerMinigame: {
    name: 'Twinkler Tap',
    where: 'Voltar arcade',
    blurb: 'Tap the Twinklers as they pop up to the active position.',
  },
  CostaLindaMinigameCocopa: {
    name: 'Cocopa Drop',
    where: 'Costa Linda',
    blurb: 'A plinko-style grid: drop Cocopas down lines and rows for prizes.',
  },
  AltipetraMinigame: {
    name: 'Altipetra Sand Run',
    where: 'Altipetra',
    blurb: 'Switch lanes and follow the uphill sand path, filling modules as you go.',
  },
  AltipetraMinigameLoader: {
    name: 'Altipetra Sand Run (loader)',
    where: 'Altipetra (overworld)',
    blurb: 'The overworld trigger that loads the Altipetra Sand Run.',
    internal: true,
  },
  BorgoIride_Minigame: {
    name: 'Borgo Iride Prizes',
    where: 'Borgo Iride',
    blurb: 'A prize game with Easy, Medium and Ace prize tiers.',
  },
  MinigameUIHandler: {
    name: 'Minigame HUD',
    where: 'Shared',
    blurb: 'The shared on-screen score / time / start-text overlay used by minigames.',
    internal: true,
  },
}

export function minigameInfo(className?: string | null): MinigameInfo {
  const k = String(className || '')
  return (
    MINIGAME_INFO[k] || {
      name: spaceCamel(k.replace(/Minigame$/, '')) || k,
      where: 'Unknown',
      blurb: '',
    }
  )
}

// A single prize row pulled out of a minigame's prize-tier field.
export interface PrizeEntry {
  guid: string
  amount: number
}

// Prize-tier fields hold an array like
//   [{ "Amount": 5, "ItemGUID": { "GUID": "0173886b-…", … } }, …]
// Pull those into clean {guid, amount} rows. Returns null if the value isn't a
// recognisable prize list.
export function parsePrizeField(value: unknown): PrizeEntry[] | null {
  if (!Array.isArray(value)) return null
  const out: PrizeEntry[] = []
  for (const raw of value) {
    if (!raw || typeof raw !== 'object') continue
    const o = raw as Record<string, unknown>
    const itemGuid = o.ItemGUID as Record<string, unknown> | undefined
    const guid = itemGuid && typeof itemGuid.GUID === 'string' ? itemGuid.GUID : null
    if (!guid) continue
    const amount = typeof o.Amount === 'number' ? o.Amount : 1
    out.push({ guid, amount })
  }
  return out.length ? out : null
}

export function isPrizeFieldKey(key: string): boolean {
  return /prize/i.test(key.replace(/^m_/, ''))
}

// Minigame field-key → friendlier label (best-effort; unknowns get spaceCamel).
export function minigameFieldLabel(key: string): string {
  const k = key.replace(/^m_/, '')
  const map: Record<string, string> = {
    OST: 'Music',
    StartSFX: 'Start sound',
    ScoreIncrease: 'Points per hit',
    XSpacing: 'Column spacing',
    ZSpacing: 'Row spacing',
    Rows: 'Rows',
    Lines: 'Columns',
    TopPrizes: 'Top prizes',
    MidPrizes: 'Mid prizes',
    LowPrizes: 'Low prizes',
    EasyPrize: 'Easy prize',
    MediumPrize: 'Medium prize',
    AcePrize: 'Ace prize',
    WaterAmount: 'Water level',
    Name: 'Name',
  }
  return map[k] || spaceCamel(k)
}

// ---- shared ----------------------------------------------------------------
function spaceCamel(s: string): string {
  if (!s) return ''
  return s
    .replace(/_/g, ' ')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2')
    .trim()
}
