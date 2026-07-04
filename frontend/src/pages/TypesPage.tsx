import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { TypeOffense, TypeCoverage, Defender } from '../lib/types'
import { TypeBadge, EmotionBadge } from '../components/Badge'
import { Skeleton, ErrorState } from '../components/States'
import { elementColor } from '../lib/game'

type Tab = 'offense' | 'coverage' | 'defenders'

export default function TypesPage() {
  const [tab, setTab] = useState<Tab>('offense')

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="text-lg text-cream text-pixel-shadow">Type Lab</h1>
        <p className="mt-1 max-w-prose text-sm text-cream/60">
          How the 13 elements stack up — attacking reach, per-type coverage, and the world's
          sturdiest defenders.
        </p>
      </header>

      <div className="flex gap-1.5">
        {(['offense', 'coverage', 'defenders'] as Tab[]).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            aria-pressed={tab === t}
            className={`rounded-[2px] border-2 px-4 py-2 text-[0.62rem] font-extrabold uppercase tracking-wide transition-colors ${
              tab === t ? 'border-gold bg-gold/25 text-gold' : 'border-cream/15 text-cream/60 hover:bg-white/5'
            }`}
            style={{ fontFamily: 'var(--font-display)' }}
          >
            {t === 'offense' ? 'Offense' : t === 'coverage' ? 'Coverage' : 'Defenders'}
          </button>
        ))}
      </div>

      {tab === 'offense' && <Offense />}
      {tab === 'coverage' && <Coverage />}
      {tab === 'defenders' && <Defenders />}
    </div>
  )
}

/* ---------------- Offense ---------------- */

const SEG: { key: keyof TypeOffense; label: string; tint: string }[] = [
  { key: 'superEffective', label: 'Super effective', tint: 'var(--color-lumen)' },
  { key: 'neutral', label: 'Neutral', tint: 'var(--color-ink-mute)' },
  { key: 'resisted', label: 'Resisted', tint: 'var(--color-el-fire)' },
  { key: 'immune', label: 'Immune', tint: 'var(--color-berry)' },
]

function Offense() {
  const { data, loading, error, reload } = useApi<TypeOffense[]>('/api/types/offense')
  if (error) return <ErrorState message={error} onRetry={reload} />
  if (loading || !data) return <Skeleton className="h-96 w-full" />

  return (
    <div className="pixel-panel p-4 md:p-5">
      <Legend items={SEG} />
      <div className="mt-4 flex flex-col gap-3">
        {data.map((t) => {
          const total = SEG.reduce((n, s) => n + (t[s.key] as number), 0) || 1
          return (
            <div key={t.type} className="grid grid-cols-[7rem_1fr] items-center gap-3">
              <TypeBadge type={t.type} small />
              <div className="flex items-center gap-3">
                <div className="flex h-5 flex-1 overflow-hidden rounded-[2px] border-2 border-ink">
                  {SEG.map((s) => {
                    const v = t[s.key] as number
                    if (!v) return null
                    return (
                      <span
                        key={s.key}
                        title={`${s.label}: ${v}`}
                        style={{ width: `${(v / total) * 100}%`, backgroundColor: s.tint }}
                      />
                    )
                  })}
                </div>
                <span className="w-8 text-right text-sm font-extrabold text-lumen-deep" style={{ fontFamily: 'var(--font-pixel)' }}>
                  {t.superEffective}
                </span>
              </div>
            </div>
          )
        })}
      </div>
      <p className="mt-3 text-xs text-ink-mute">
        Bars show, for each attacking type, how the full roster reacts. The number is how many
        creatures it hits super-effectively.
      </p>
    </div>
  )
}

/* ---------------- Coverage ---------------- */

function Coverage() {
  const { data, loading, error, reload } = useApi<TypeCoverage[]>('/api/types/coverage')
  const [pick, setPick] = useState<string | null>(null)
  if (error) return <ErrorState message={error} onRetry={reload} />
  if (loading || !data) return <Skeleton className="h-96 w-full" />

  const active = data.find((t) => t.type === (pick ?? data[0].type)) ?? data[0]
  const groups: { key: keyof TypeCoverage; label: string; tint: string }[] = [
    { key: 'weakness', label: 'Super effective against', tint: 'var(--color-lumen)' },
    { key: 'normal', label: 'Neutral against', tint: 'var(--color-ink-mute)' },
    { key: 'resistance', label: 'Resisted by', tint: 'var(--color-el-fire)' },
    { key: 'immunity', label: 'No effect on', tint: 'var(--color-berry)' },
  ]

  return (
    <div className="flex flex-col gap-4">
      <div className="dialog-box flex flex-wrap gap-1.5 p-4">
        {data.map((t) => (
          <button
            key={t.type}
            onClick={() => setPick(t.type)}
            aria-pressed={active.type === t.type}
            className="type-chip transition-transform hover:-translate-y-0.5"
            style={{
              backgroundColor: elementColor(t.type),
              boxShadow: active.type === t.type ? '0 0 0 2px var(--color-gold), 2px 2px 0 0 rgba(0,0,0,0.3)' : undefined,
            }}
          >
            {t.type}
          </button>
        ))}
      </div>

      <div className="pixel-panel p-4 md:p-5">
        <div className="mb-4 flex items-center gap-2">
          <span className="text-sm font-extrabold text-ink">Attacking with</span>
          <TypeBadge type={active.type} />
        </div>
        <div className="flex flex-col gap-4">
          {groups.map((g) => {
            const names = active[g.key] as string[]
            return (
              <div key={g.key}>
                <div className="mb-1.5 flex items-center gap-2 text-[0.7rem] font-extrabold uppercase tracking-wide" style={{ color: g.tint }}>
                  {g.label}
                  <span className="rounded-[2px] bg-ink/10 px-1.5 py-0.5 text-ink-soft">{names.length}</span>
                </div>
                {names.length === 0 ? (
                  <p className="text-sm text-ink-mute">None.</p>
                ) : (
                  <div className="flex flex-wrap gap-1.5">
                    {names.map((n) => (
                      <span key={n} className="rounded-[2px] bg-ink/5 px-2 py-1 text-xs font-bold text-ink-soft">
                        {n}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

/* ---------------- Defenders ---------------- */

function Defenders() {
  const { data, loading, error, reload } = useApi<Defender[]>('/api/types/defenders?limit=30')
  if (error) return <ErrorState message={error} onRetry={reload} />
  if (loading || !data) return <Skeleton className="h-96 w-full" />

  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
      {data.map((d, i) => (
        <Link
          key={d.guid}
          to={`/dex/${d.guid}`}
          className="pixel-panel flex items-center gap-3 p-3 transition-transform hover:-translate-y-1"
        >
          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[2px] bg-ink text-cream" style={{ fontFamily: 'var(--font-pixel)' }}>
            {i + 1}
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex items-baseline justify-between gap-2">
              <span className="line-clamp-1 text-sm font-extrabold text-ink">{d.species}</span>
              <span className="shrink-0 text-sm font-extrabold text-lumen-deep" style={{ fontFamily: 'var(--font-pixel)' }} title="Defensive score">
                {d.score}
              </span>
            </div>
            <div className="mt-1 flex flex-wrap items-center gap-1">
              <TypeBadge type={d.ele} small />
              <EmotionBadge emo={d.emo} small />
            </div>
            <div className="mt-1.5 flex flex-wrap gap-x-3 gap-y-0.5 text-[0.65rem] font-bold text-ink-mute">
              <span>{d.weak} weak</span>
              <span>{d.resist} resist</span>
              <span>{d.immune} immune</span>
              <span className="text-ink-soft">DEF {d.def}</span>
              <span className="text-ink-soft">SpD {d.spd}</span>
            </div>
          </div>
        </Link>
      ))}
    </div>
  )
}

function Legend({ items }: { items: { label: string; tint: string }[] }) {
  return (
    <div className="flex flex-wrap gap-x-4 gap-y-1.5 text-[0.65rem] font-bold text-ink-soft">
      {items.map((s) => (
        <span key={s.label} className="inline-flex items-center gap-1.5">
          <span className="h-3 w-3 rounded-[1px] border border-ink/40" style={{ backgroundColor: s.tint }} />
          {s.label}
        </span>
      ))}
    </div>
  )
}
