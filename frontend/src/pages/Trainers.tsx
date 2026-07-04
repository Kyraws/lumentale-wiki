import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { TrainerSummary } from '../lib/types'
import Sprite from '../components/Sprite'
import { GridSkeleton, ErrorState, EmptyState } from '../components/States'
import { IconSearch } from '../components/Icons'
import { cleanTrainerName } from '../lib/game'

export default function Trainers() {
  const { data, loading, error, reload } = useApi<TrainerSummary[]>('/api/trainers')
  const [q, setQ] = useState('')
  const [withParty, setWithParty] = useState(true)

  const rows = useMemo(() => {
    if (!data) return []
    const needle = q.trim().toLowerCase()
    return data
      .filter((t) => {
        if (withParty && t.party.length === 0) return false
        if (needle && !cleanTrainerName(t.name, t.display).toLowerCase().includes(needle)) return false
        return true
      })
      .sort((a, b) => b.party.length - a.party.length || cleanTrainerName(a.name, a.display).localeCompare(cleanTrainerName(b.name, b.display)))
  }, [data, q, withParty])

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="text-lg text-cream text-pixel-shadow">Trainers</h1>
        <p className="mt-1 text-sm text-cream/60">
          {data ? `${rows.length} of ${data.length} trainers` : 'Loading trainers…'}
        </p>
      </header>

      <div className="dialog-box flex flex-col gap-4 p-4 sm:flex-row sm:items-center">
        <label className="relative block flex-1">
          <span className="sr-only">Search trainers</span>
          <IconSearch className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-lg text-ink-mute" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            type="search"
            placeholder="Search trainers…"
            className="w-full rounded-[2px] border-ink bg-parch py-2.5 pl-10 pr-3 text-sm font-bold text-ink placeholder:text-ink-mute/70 focus:outline-none"
            style={{ borderWidth: 3 }}
          />
        </label>
        <button
          onClick={() => setWithParty((v) => !v)}
          aria-pressed={withParty}
          className={`shrink-0 rounded-[2px] border-2 px-3 py-2 text-[0.6rem] font-extrabold uppercase tracking-wide transition-colors ${
            withParty ? 'border-lumen bg-lumen/20 text-lumen' : 'border-cream/15 text-cream/60 hover:bg-white/5'
          }`}
        >
          Has a team
        </button>
      </div>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading ? (
        <GridSkeleton count={12} />
      ) : rows.length === 0 ? (
        <EmptyState title="No trainers match." hint="Try clearing the search." />
      ) : (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {rows.map((t, i) => (
            <TrainerCard key={t.guid} t={t} index={i} />
          ))}
        </div>
      )}
    </div>
  )
}

function TrainerCard({ t, index }: { t: TrainerSummary; index: number }) {
  return (
    <Link
      to={`/trainers/${t.guid}`}
      className="pixel-panel anim-pop flex gap-3 p-3 transition-transform hover:-translate-y-1"
      style={{ animationDelay: `${Math.min(index, 18) * 16}ms` }}
    >
      <div className="pixel-screen flex h-20 w-20 shrink-0 items-center justify-center p-1">
        <Sprite src={t.idle} alt={cleanTrainerName(t.name, t.display)} size={72} />
      </div>
      <div className="min-w-0 flex-1">
        <h3 className="line-clamp-1 text-sm font-extrabold text-ink">{cleanTrainerName(t.name, t.display)}</h3>
        <div className="mt-1 flex flex-wrap items-center gap-2 text-xs font-bold text-ink-mute">
          {t.money != null && <span className="text-gold-deep">{t.money}₲ reward</span>}
        </div>
        <div className="mt-2 flex flex-wrap gap-1">
          {t.party.slice(0, 6).map((p) => (
            <Sprite key={p.ord} src={p.menuArt} alt={p.species} size={28} />
          ))}
        </div>
      </div>
    </Link>
  )
}
