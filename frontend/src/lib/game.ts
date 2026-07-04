// Game vocabulary helpers: type colors, labels, stat grading, formatting.

/** Element types map to CSS custom properties defined in index.css `@theme`. */
const ELEMENT_VARS: Record<string, string> = {
  FIRE: '--color-el-fire',
  WATER: '--color-el-water',
  GRASS: '--color-el-grass',
  ELECTRIC: '--color-el-electric',
  ICE: '--color-el-ice',
  GEO: '--color-el-geo',
  AURA: '--color-el-aura',
  CHAKRA: '--color-el-chakra',
  VIRUS: '--color-el-virus',
  DATA: '--color-el-data',
  DEMON: '--color-el-demon',
  ANCIENT: '--color-el-ancient',
  ANOMALOUS: '--color-el-anomalous',
}

const EMOTION_VARS: Record<string, string> = {
  FELICIS: '--color-emo-felicis',
  FUROR: '--color-emo-furor',
  MESTUS: '--color-emo-mestus',
  SEREUM: '--color-emo-sereum',
  HORRENS: '--color-emo-horrens',
}

/** Short human gloss for the five emotion types. */
export const EMOTION_GLOSS: Record<string, string> = {
  FELICIS: 'Joy',
  FUROR: 'Rage',
  MESTUS: 'Sorrow',
  SEREUM: 'Calm',
  HORRENS: 'Dread',
}

export function elementColor(type?: string | null): string {
  if (!type) return 'var(--color-el-anomalous)'
  return `var(${ELEMENT_VARS[type.toUpperCase()] ?? '--color-el-anomalous'})`
}

export function emotionColor(emo?: string | null): string {
  if (!emo) return 'var(--color-ink-mute)'
  return `var(${EMOTION_VARS[emo.toUpperCase()] ?? '--color-ink-mute'})`
}

export const ALL_ELEMENTS = Object.keys(ELEMENT_VARS)
export const ALL_EMOTIONS = Object.keys(EMOTION_VARS)

/** TITLE -> Title (keeps acronyms readable). */
export function titleCase(s?: unknown): string {
  // Defensive: raw extracted records can hand us numbers/objects, not just strings.
  if (s == null) return ''
  const str = typeof s === 'string' ? s : String(s)
  if (!str) return ''
  return str
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase())
}

/** Stat-grade tint A(best) -> F(worst). */
export function gradeColor(grade: string): string {
  switch (grade.toUpperCase()) {
    case 'A':
    case 'S':
      return 'var(--color-lumen)'
    case 'B':
      return 'var(--color-el-grass)'
    case 'C':
      return 'var(--color-gold)'
    case 'D':
      return 'var(--color-el-fire)'
    default:
      return 'var(--color-berry)'
  }
}

export const REGION_LABEL: Record<string, string> = {
  north: 'Northern Route',
  south: 'Southern Route',
}

/** Full stat names for the six-stat radar/bars. */
export const STAT_FULL: Record<string, string> = {
  HP: 'Hit Points',
  ATK: 'Attack',
  DEF: 'Defense',
  SpA: 'Sp. Attack',
  SpD: 'Sp. Defense',
  Spe: 'Speed',
}

/** Pad a dex number like 1 -> "001". */
export function dexNo(n: number): string {
  return `#${String(n).padStart(3, '0')}`
}

/** "MAIN_5.1_BI_TreyJustLeftLab_Cutscene" -> "Trey Just Left Lab Cutscene";
 *  "MG_Main_2_Ander" -> "Ander". Strips the main-quest number prefix (global
 *  MAIN_<n> or regional <R>_Main_<n>), then a leading short region/phase tag. */
export function cleanSceneName(name: string): string {
  return (
    name
      .replace(/^MAIN_[\d._]+_?/i, '') // MAIN_5.1_
      .replace(/^[A-Za-z]+_Main_[\d._]+_?/i, '') // MG_Main_2_
      .replace(/^[A-Z]{1,4}_/, '') // BI_ / AL_ / BIDP_ tag
      .replace(/_/g, ' ')
      .replace(/([a-z])([A-Z])/g, '$1 $2') // camelCase -> spaced
      .replace(/\s+/g, ' ')
      .trim() || name
  )
}

/** "AL_GuardGuardingPrisonEntrance" -> "Guard Guarding Prison Entrance". */
export function cleanTrainerName(name: string, display?: string | null): string {
  if (display && display.trim()) return display.trim()
  return (
    name
      .replace(/^[A-Z0-9]{1,4}_/, '') // leading code like AL_ / BR_
      .replace(/_/g, ' ')
      .replace(/([a-z])([A-Z])/g, '$1 $2')
      .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2')
      .replace(/\s+/g, ' ')
      .trim() || name
  )
}

/** Item type_label -> player-facing wording ("Recipe" items are the craftables). */
export function itemTypeLabel(t?: string | null): string {
  return t === 'Recipe' ? 'Craftable' : (t ?? '')
}

/** Skill AoE codes -> player-facing spread labels. */
export const SPREAD: Record<string, string> = {
  SingleTarget: 'Single',
  TargetAOE: 'All Foes',
  AdjacentAOE: '3 Targets',
  EveryoneAOE: 'Everyone',
}

/** Move damage class -> color + short label. */
export const MOVE_CATEGORY: Record<string, { tint: string; label: string }> = {
  PHYSICAL: { tint: 'var(--color-berry)', label: 'Physical' },
  SPECIAL: { tint: 'var(--color-emo-mestus)', label: 'Special' },
  STATUS: { tint: 'var(--color-ink-mute)', label: 'Status' },
}

/** Defensive read of a possibly-missing string field on a raw record. */
export function rawStr(rec: Record<string, unknown>, key: string): string | undefined {
  const v = rec[key]
  return typeof v === 'string' ? v : undefined
}

export function rawNum(rec: Record<string, unknown>, key: string): number | undefined {
  const v = rec[key]
  return typeof v === 'number' ? v : undefined
}

// ----- v3 helpers -----

/** Elemental effectiveness (defending) -> tint + arrow glyph. Never color alone. */
export const EFFECTIVENESS: Record<string, { tint: string; glyph: string; label: string }> = {
  WEAKNESS: { tint: 'var(--color-berry)', glyph: '▲', label: 'Weak' },
  RESISTANCE: { tint: 'var(--color-lumen)', glyph: '▼', label: 'Resist' },
  IMMUNITY: { tint: 'var(--color-ink)', glyph: '✕', label: 'Immune' },
  NORMAL: { tint: 'var(--color-ink-mute)', glyph: '—', label: 'Normal' },
}

/** Emotion multiplier (1.2/0.8/1.0) -> tint + label. */
export function multiplierInfo(m: number): { tint: string; label: string } {
  if (m > 1) return { tint: 'var(--color-berry)', label: `×${m}` }
  if (m < 1) return { tint: 'var(--color-lumen)', label: `×${m}` }
  return { tint: 'var(--color-ink-mute)', label: `×${m}` }
}

/** Recovered-formula confidence -> tint. */
export function confidenceColor(c?: string | null): string {
  switch ((c ?? '').toLowerCase()) {
    case 'verified':
      return 'var(--color-lumen)'
    case 'structural':
      return 'var(--color-gold)'
    default:
      return 'var(--color-ink-mute)' // partial / unknown
  }
}

/** Locale-aware integer formatting for stat/price/exp columns. */
export function fmtNum(n?: number | null): string {
  return n == null ? '—' : n.toLocaleString()
}
