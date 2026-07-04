import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useApi } from '../lib/api'
import { GridSkeleton, ErrorState, EmptyState } from '../components/States'
import { IconSearch, IconCamp, IconStar } from '../components/Icons'
import { fmtNum } from '../lib/game'
import './camps-squadrons.css'

/** Local response shape — richer than the shared types.ts CampSummary. */
interface CampSummary {
  guid: string
  name?: string | null
  displayName?: string | null
  region?: string | null
  area?: string | null
  effectClass?: string | null
  effectLabel?: string | null
  effectIncrement?: number
  influence?: number
  lumenAmount?: number
}

const REGION_ORDER: Record<string, number> = { Center: 0, North: 1, South: 2, Talea: 3 }

function pct(inc?: number): string | null {
  if (inc == null) return null
  const v = inc * 100
  return Number.isInteger(v) ? `${v}` : `${v.toFixed(1)}`
}

export default function Camps() {
  const { data, loading, error, reload } = useApi<CampSummary[]>('/api/camps')
  const [q, setQ] = useState('')

  const groups = useMemo(() => {
    if (!data) return []
    const needle = q.trim().toLowerCase()
    const filtered = data.filter((c) => {
      if (!needle) return true
      return (
        (c.displayName ?? '').toLowerCase().includes(needle) ||
        (c.region ?? '').toLowerCase().includes(needle) ||
        (c.area ?? '').toLowerCase().includes(needle) ||
        (c.effectLabel ?? '').toLowerCase().includes(needle)
      )
    })
    const byRegion = new Map<string, CampSummary[]>()
    for (const c of filtered) {
      const r = c.region || 'Talea'
      if (!byRegion.has(r)) byRegion.set(r, [])
      byRegion.get(r)!.push(c)
    }
    return [...byRegion.entries()]
      .sort((a, b) => (REGION_ORDER[a[0]] ?? 9) - (REGION_ORDER[b[0]] ?? 9))
      .map(([region, camps]) => ({
        region,
        camps: camps.sort((a, b) => (a.area ?? '').localeCompare(b.area ?? '')),
      }))
  }, [data, q])

  const total = data?.length ?? 0
  const shown = groups.reduce((n, g) => n + g.camps.length, 0)

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="text-lg text-cream text-pixel-shadow">Lumen Camps</h1>
        <p className="mt-1 text-sm text-cream/70">
          Outposts dotted across Talea. Defeat the squadron holding a camp to claim its{' '}
          <span className="font-bold text-cream">temporary bonus</span>, unlock a nearby shop, and pick up
          bounty &amp; info tasks from townsfolk.
        </p>
        <p className="mt-1 text-xs text-cream/55">
          {data ? `${shown} of ${total} camps` : 'Loading camps…'}
        </p>
      </header>

      <div className="dialog-box flex flex-col gap-4 p-4">
        <label className="relative block">
          <span className="sr-only">Search camps</span>
          <IconSearch className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-lg text-ink-mute" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            type="search"
            placeholder="Search by area, region, or bonus…"
            className="w-full rounded-[2px] border-ink bg-parch py-2.5 pl-10 pr-3 text-sm font-bold text-ink placeholder:text-ink-mute/70 focus:outline-none"
            style={{ borderWidth: 3 }}
          />
        </label>
      </div>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading ? (
        <GridSkeleton count={8} />
      ) : shown === 0 ? (
        <EmptyState title="No camps match." hint="Try clearing the search." />
      ) : (
        <div className="flex flex-col gap-6">
          {groups.map((g) => (
            <section key={g.region} className="flex flex-col gap-3">
              <h2 className="roster-rule">
                <span className="region-pill" data-region={g.region}>
                  {g.region === 'Center' ? '✦ ' : ''}
                  {g.region} {g.region !== 'Talea' ? 'Route' : ''}
                </span>
              </h2>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {g.camps.map((c, i) => (
                  <CampCard key={c.guid} c={c} index={i} />
                ))}
              </div>
            </section>
          ))}
        </div>
      )}
    </div>
  )
}

function CampCard({ c, index }: { c: CampSummary; index: number }) {
  const p = pct(c.effectIncrement)
  return (
    <Link
      to={`/camps/${c.guid}`}
      data-effect={c.effectClass ?? ''}
      className="camp-accent pixel-panel anim-pop flex flex-col gap-3 p-3 transition-transform hover:-translate-y-1"
      style={{ animationDelay: `${Math.min(index, 18) * 16}ms` }}
    >
      <div className="flex items-start gap-3">
        <div
          className="pixel-screen flex h-14 w-14 shrink-0 items-center justify-center p-1 text-2xl"
          style={{ color: 'var(--camp-tint)' }}
        >
          <IconCamp />
        </div>
        <div className="min-w-0 flex-1">
          <h3 className="line-clamp-2 text-sm font-extrabold leading-tight text-ink">
            {c.displayName || c.name || 'Camp'}
          </h3>
          <div className="mt-1 flex flex-wrap items-center gap-1.5">
            {c.region && (
              <span className="region-pill" data-region={c.region}>
                {c.region}
              </span>
            )}
            {c.area && <span className="text-[0.6rem] font-bold text-ink-mute">{c.area}</span>}
          </div>
        </div>
      </div>

      {c.effectLabel && (
        <span className="camp-effect-chip w-fit">
          {c.effectLabel}
          {p && <span className="text-ink/80">+{p}%</span>}
        </span>
      )}

      <div className="mt-auto flex items-center gap-3 text-xs font-bold text-ink-mute">
        {c.lumenAmount != null && (
          <span className="inline-flex items-center gap-1 text-gold-deep">
            <IconStar className="text-[0.85em]" />
            {fmtNum(c.lumenAmount)} Lumen to take
          </span>
        )}
      </div>
    </Link>
  )
}
