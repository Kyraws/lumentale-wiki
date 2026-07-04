import { elementColor, emotionColor, EMOTION_GLOSS, titleCase } from '../lib/game'

/** Elemental type chip (e.g. VIRUS, FIRE). Color + text — never color alone. */
export function TypeBadge({ type, small = false }: { type?: string | null; small?: boolean }) {
  if (!type) return null
  return (
    <span
      className="type-chip"
      style={{ backgroundColor: elementColor(type), fontSize: small ? '0.42rem' : undefined }}
    >
      {type.toUpperCase()}
    </span>
  )
}

/** Emotion type chip with a short gloss (FELICIS · Joy). */
export function EmotionBadge({ emo, small = false }: { emo?: string | null; small?: boolean }) {
  if (!emo) return null
  const gloss = EMOTION_GLOSS[emo.toUpperCase()]
  return (
    <span
      className="type-chip"
      style={{ backgroundColor: emotionColor(emo), fontSize: small ? '0.42rem' : undefined }}
      title={gloss}
    >
      {emo.toUpperCase()}
      {gloss && !small ? ` · ${gloss}` : ''}
    </span>
  )
}

/** North/South route pill. */
export function RegionBadge({ region }: { region: string }) {
  const north = region === 'north'
  return (
    <span
      className="inline-flex items-center gap-1 rounded-[2px] border-2 border-ink/60 px-1.5 py-0.5 text-[0.6rem] font-extrabold uppercase tracking-wide"
      style={{
        backgroundColor: north ? 'rgba(74,163,232,0.18)' : 'rgba(87,184,148,0.2)',
        color: north ? 'var(--color-sky)' : 'var(--color-emo-sereum)',
      }}
    >
      {north ? '▲ North' : '▼ South'}
    </span>
  )
}

/** Generic small tag used for quest types, materials, rarity, etc. */
export function Tag({ children }: { children: React.ReactNode }) {
  return (
    <span className="inline-flex items-center rounded-[2px] bg-ink/8 px-2 py-0.5 text-xs font-bold text-ink-soft">
      {children}
    </span>
  )
}

export { titleCase }
