import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useApi } from '../lib/api'
import Sprite from '../components/Sprite'
import { GridSkeleton, ErrorState, EmptyState } from '../components/States'
import { IconSearch, IconSquadron, IconTrainer } from '../components/Icons'
import { fmtNum } from '../lib/game'
import './camps-squadrons.css'

interface SquadronSummary {
  guid: string
  name?: string | null
  rank?: number
  rankLabel?: string | null
  memberCount?: number
  logo?: string | null
}

const TIER_LABEL: Record<number, string> = {
  2: "Player's Squadron",
  1: 'Special Squadrons',
  0: 'Regional Squadrons',
}

export default function Squadrons() {
  const { data, loading, error, reload } = useApi<SquadronSummary[]>('/api/squadrons')
  const [q, setQ] = useState('')

  const groups = useMemo(() => {
    if (!data) return []
    const needle = q.trim().toLowerCase()
    const filtered = data.filter((s) => !needle || (s.name ?? '').toLowerCase().includes(needle))
    const byTier = new Map<number, SquadronSummary[]>()
    for (const s of filtered) {
      const t = s.rank ?? 0
      if (!byTier.has(t)) byTier.set(t, [])
      byTier.get(t)!.push(s)
    }
    return [...byTier.entries()]
      .sort((a, b) => b[0] - a[0])
      .map(([tier, squads]) => ({
        tier,
        label: TIER_LABEL[tier] ?? `Tier ${tier}`,
        squads: squads.sort((a, b) => (a.name ?? '').localeCompare(b.name ?? '')),
      }))
  }, [data, q])

  const total = data?.length ?? 0
  const shown = groups.reduce((n, g) => n + g.squads.length, 0)

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="text-lg text-cream text-pixel-shadow">Squadrons</h1>
        <p className="mt-1 text-sm text-cream/70">
          Bands of Lumen that hold the camps across Talea. Each region has its own squadron led by a{' '}
          <span className="font-bold text-cream">camp boss</span>; defeat them to claim their camp.
        </p>
        <p className="mt-1 text-xs text-cream/55">
          {data ? `${shown} of ${total} squadrons` : 'Loading squadrons…'}
        </p>
      </header>

      <div className="dialog-box flex flex-col gap-4 p-4">
        <label className="relative block">
          <span className="sr-only">Search squadrons</span>
          <IconSearch className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-lg text-ink-mute" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            type="search"
            placeholder="Search squadrons…"
            className="w-full rounded-[2px] border-ink bg-parch py-2.5 pl-10 pr-3 text-sm font-bold text-ink placeholder:text-ink-mute/70 focus:outline-none"
            style={{ borderWidth: 3 }}
          />
        </label>
      </div>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading ? (
        <GridSkeleton count={9} />
      ) : shown === 0 ? (
        <EmptyState title="No squadrons match." hint="Try clearing the search." />
      ) : (
        <div className="flex flex-col gap-6">
          {groups.map((g) => (
            <section key={g.tier} className="flex flex-col gap-3">
              <h2 className="roster-rule">
                <span>{g.label}</span>
              </h2>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {g.squads.map((s, i) => (
                  <SquadronCard key={s.guid} s={s} index={i} />
                ))}
              </div>
            </section>
          ))}
        </div>
      )}
    </div>
  )
}

function SquadronCard({ s, index }: { s: SquadronSummary; index: number }) {
  return (
    <Link
      to={`/squadrons/${s.guid}`}
      className="pixel-panel anim-pop flex gap-3 p-3 transition-transform hover:-translate-y-1"
      style={{ animationDelay: `${Math.min(index, 18) * 16}ms` }}
    >
      <div className="pixel-screen flex h-16 w-16 shrink-0 items-center justify-center p-1 text-2xl text-ink-soft">
        {s.logo ? <Sprite src={s.logo} alt={s.name || 'Squadron'} size={56} /> : <IconSquadron />}
      </div>
      <div className="min-w-0 flex-1">
        <h3 className="line-clamp-1 text-sm font-extrabold text-ink">{s.name || 'Squadron'}</h3>
        {s.rankLabel && (
          <span className="mt-1 inline-block text-[0.6rem] font-bold uppercase tracking-wide text-ink-mute">
            {s.rankLabel}
          </span>
        )}
        <div className="mt-1.5 flex flex-wrap items-center gap-1.5 text-xs font-bold text-ink-mute">
          <IconTrainer className="text-[0.85em] text-ink-soft" />
          <span>{fmtNum(s.memberCount)} members</span>
        </div>
      </div>
    </Link>
  )
}
