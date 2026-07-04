import { Link, useParams } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { MoveLearner, MoveSummary } from '../lib/types'
import Sprite from '../components/Sprite'
import { TypeBadge } from '../components/Badge'
import { ErrorState, Skeleton, EmptyState } from '../components/States'
import { IconBack } from '../components/Icons'
import { MOVE_CATEGORY, SPREAD, rawStr, rawNum, titleCase } from '../lib/game'

// Raw `/api/moves/{guid}` keeps the game's original PascalCase keys (Description,
// CD, IsContact, …) and stores Type/Category/Target as numeric codes — so we read
// the friendly, camelCased fields from the typed list endpoint and pull only the
// description + behaviour flags out of the raw record.
function rawFlag(rec: Record<string, unknown>, key: string): boolean {
  const v = rec[key]
  return v === true || v === 1
}

export default function MoveDetail() {
  const { guid = '' } = useParams()
  const { data, loading, error, reload } = useApi<Record<string, unknown>>(`/api/moves/${guid}`)
  const { data: summaries } = useApi<MoveSummary[]>('/api/moves')
  const { data: learners } = useApi<MoveLearner[]>(`/api/moves/${guid}/learners`)

  if (error) return <ErrorState message={error} onRetry={reload} />
  if (loading || !data) return <Skeleton className="h-72 w-full" />

  const summary = summaries?.find((m) => m.guid === guid)
  const name = summary?.name ?? 'Move'
  const type = summary?.type
  const category = summary?.category ?? undefined
  // English-resolved description from the list endpoint; the raw record's
  // Description (Italian) is only the last-resort fallback.
  const desc = summary?.description ?? rawStr(data, 'Description')
  const cat = category ? MOVE_CATEGORY[category] : undefined
  const flags = [
    rawFlag(data, 'IsContact') ? 'Contact' : null,
    rawFlag(data, 'IsDoT') ? 'Damage over time' : null,
    rawFlag(data, 'IsEoT') ? 'End of turn' : null,
  ].filter(Boolean) as string[]

  const facts: [string, string | number | undefined][] = [
    ['Power', summary?.power || '—'],
    ['Accuracy', summary?.accuracy != null ? `${summary.accuracy}%` : '—'],
    ['SP Cost', summary?.cost ?? '—'],
    ['Cooldown', rawNum(data, 'CD') ?? '—'],
    ['Target', titleCase(summary?.target)],
    ['Spread', summary?.aoe ? SPREAD[summary.aoe] ?? titleCase(summary.aoe) : undefined],
  ]

  return (
    <div className="flex flex-col gap-5">
      <Link to="/moves" className="inline-flex w-fit items-center gap-1 text-sm font-bold text-cream/70 hover:text-cream">
        <IconBack /> Back to Moves
      </Link>

      <section className="dialog-box p-5 md:p-7">
        <h1 className="text-xl text-cream text-pixel-shadow md:text-2xl">{name}</h1>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <TypeBadge type={type} />
          {cat && (
            <span
              className="type-chip"
              style={{ backgroundColor: cat.tint }}
            >
              {cat.label}
            </span>
          )}
          {flags.map((f) => (
            <span key={f} className="rounded-[2px] border-2 border-cream/20 px-2 py-1 text-[0.6rem] font-extrabold uppercase tracking-wide text-cream/80">
              {f}
            </span>
          ))}
        </div>
        {desc && (
          <p className="mt-4 max-w-prose border-l-4 border-lumen/40 pl-3 text-sm italic leading-relaxed text-cream/80">
            {desc}
          </p>
        )}
      </section>

      <section className="pixel-panel p-4 md:p-5">
        <h2 className="mb-4 inline-block rounded-[2px] bg-ink px-2 py-1 text-[0.7rem] text-cream" style={{ fontFamily: 'var(--font-display)' }}>
          Battle Data
        </h2>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-6">
          {facts.map(([label, value]) => (
            <div key={label} className="rounded-[2px] bg-ink/5 px-2 py-1.5">
              <div className="text-[0.6rem] font-bold uppercase tracking-wide text-ink-mute">{label}</div>
              <div className="text-base font-extrabold text-ink" style={{ fontFamily: 'var(--font-pixel)' }}>
                {value || '—'}
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="pixel-panel p-4 md:p-5">
        <h2 className="mb-4 inline-block rounded-[2px] bg-ink px-2 py-1 text-[0.7rem] text-cream" style={{ fontFamily: 'var(--font-display)' }}>
          Learned By {learners ? `(${learners.length})` : ''}
        </h2>
        {!learners ? (
          <Skeleton className="h-24 w-full" />
        ) : learners.length === 0 ? (
          <EmptyState title="No creature learns this move." />
        ) : (
          <div className="grid grid-cols-3 gap-3 sm:grid-cols-4 md:grid-cols-6 lg:grid-cols-8">
            {learners.map((l) => (
              <Link key={l.guid} to={`/dex/${l.guid}`} className="pixel-screen flex flex-col items-center p-2 text-center transition-transform hover:-translate-y-0.5">
                <Sprite src={l.menuArt} alt={l.species} size={48} />
                <span className="mt-1 line-clamp-1 text-xs font-extrabold text-ink">{l.species}</span>
                {l.level != null && l.level > 0 && (
                  <span className="text-[0.6rem] font-bold text-ink-mute">Lv {l.level}</span>
                )}
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
