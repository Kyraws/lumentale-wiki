import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { BossSummary } from '../lib/types'
import Sprite from '../components/Sprite'
import { TypeBadge, EmotionBadge } from '../components/Badge'
import { GridSkeleton, ErrorState, EmptyState } from '../components/States'
import { IconSearch, IconGraph } from '../components/Icons'
import { elementColor, fmtNum } from '../lib/game'

// The list endpoint now serves the boss's origin-form menu art as its icon.
type BossListItem = BossSummary & { menuArt?: string | null }

export default function Bosses() {
  const { data, loading, error, reload } = useApi<BossListItem[]>('/api/bosses')
  const [q, setQ] = useState('')

  const filtered = useMemo(() => {
    if (!data) return []
    const needle = q.trim().toLowerCase()
    if (!needle) return data
    return data.filter(
      (b) =>
        b.name.toLowerCase().includes(needle) ||
        (b.display ?? '').toLowerCase().includes(needle) ||
        (b.originSpecies ?? '').toLowerCase().includes(needle),
    )
  }, [data, q])

  return (
    <div className="flex flex-col gap-5">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-lg text-cream text-pixel-shadow">Bosses</h1>
          <p className="mt-1 text-sm text-cream/60">
            {data ? `${filtered.length} of ${data.length} bosses` : 'Loading bosses…'}
          </p>
        </div>
      </header>

      {/* Search console */}
      <div className="dialog-box flex flex-col gap-4 p-4">
        <label className="relative block">
          <span className="sr-only">Search bosses by name</span>
          <IconSearch className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-lg text-ink-mute" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            type="search"
            inputMode="search"
            placeholder="Search by name or species…"
            className="w-full rounded-[2px] border-3 border-ink bg-parch py-2.5 pl-10 pr-3 text-sm font-bold text-ink placeholder:text-ink-mute/70 focus:outline-none"
            style={{ borderWidth: 3 }}
          />
        </label>
      </div>

      {/* Grid */}
      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading ? (
        <GridSkeleton count={12} />
      ) : filtered.length === 0 ? (
        <EmptyState title="No bosses match." hint="Try clearing the search box." />
      ) : (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((b, i) => (
            <BossCard key={b.guid} b={b} index={i} />
          ))}
        </div>
      )}
    </div>
  )
}

function BossCard({ b, index }: { b: BossListItem; index: number }) {
  return (
    <Link
      to={`/bosses/${b.guid}`}
      className="pixel-panel anim-pop group relative flex items-center gap-3 p-4 transition-transform hover:-translate-y-1"
      style={{ animationDelay: `${Math.min(index, 24) * 18}ms` }}
    >
      {/* Boss icon (origin-form menu art) on a typed disc */}
      <div
        className="relative flex h-16 w-16 shrink-0 items-center justify-center rounded-[3px] border-2 border-ink/15"
        style={{
          background: `radial-gradient(80% 80% at 50% 25%, ${elementColor(b.ele)}33, var(--color-parch))`,
        }}
      >
        <Sprite src={b.menuArt} alt={b.display || b.name} size={56} />
      </div>

      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        {b.hasGraph && (
          <span
            className="absolute right-3 top-3 inline-flex items-center gap-1 text-[0.55rem] font-extrabold uppercase tracking-wide text-lumen-deep"
            title="Has a battle graph"
          >
            <IconGraph className="text-sm" /> Graph
          </span>
        )}

        <div className="flex items-center gap-2">
          {b.level != null && (
            <span className="text-base text-berry" style={{ fontFamily: 'var(--font-pixel)' }}>
              Lv {b.level}
            </span>
          )}
          <span className="line-clamp-1 text-base font-extrabold text-ink">{b.display || b.name}</span>
        </div>

        <div className="flex flex-wrap items-center gap-1.5">
          <TypeBadge type={b.ele} small />
          <EmotionBadge emo={b.emotion} small />
          {b.extraHealthBars != null && b.extraHealthBars > 0 && (
            <span className="inline-flex items-center gap-0.5" title={`${b.extraHealthBars} extra health bar(s)`}>
              {Array.from({ length: b.extraHealthBars }).map((_, i) => (
                <span key={i} className="h-2 w-3 rounded-[1px] bg-berry/80" />
              ))}
            </span>
          )}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-ink-mute">
          {b.originSpecies && (
            <span className="line-clamp-1">
              Origin: <span className="font-bold text-ink-soft">{b.originSpecies}</span>
            </span>
          )}
          {b.expGiven != null && (
            <span className="font-bold text-gold">{fmtNum(b.expGiven)} EXP</span>
          )}
        </div>
      </div>
    </Link>
  )
}
