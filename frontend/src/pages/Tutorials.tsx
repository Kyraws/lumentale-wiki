import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { TutorialSummary } from '../lib/types'
import { Tag } from '../components/Badge'
import { GridSkeleton, ErrorState, EmptyState } from '../components/States'
import { IconBook, IconSearch } from '../components/Icons'

// "AlesMokaWoodsTutorial" -> "Ales Moka Woods Tutorial" for a readable card title.
function prettyName(t: TutorialSummary): string {
  if (t.titleKey && t.titleKey.trim()) return t.titleKey.trim()
  const n = t.internalName ?? ''
  return (
    n
      .replace(/([a-z])([A-Z])/g, '$1 $2')
      .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2')
      .replace(/_/g, ' ')
      .replace(/\s+/g, ' ')
      .trim() || 'Tutorial'
  )
}

export default function Tutorials() {
  const { data, loading, error, reload } = useApi<TutorialSummary[]>('/api/tutorials')
  const [q, setQ] = useState('')

  const rows = useMemo(() => {
    if (!data) return []
    const needle = q.trim().toLowerCase()
    if (!needle) return data
    return data.filter(
      (t) =>
        prettyName(t).toLowerCase().includes(needle) ||
        (t.internalName ?? '').toLowerCase().includes(needle),
    )
  }, [data, q])

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="text-lg text-cream text-pixel-shadow">Tutorials</h1>
        <p className="mt-1 text-sm text-cream/60">
          {data ? `${rows.length} of ${data.length} tutorials` : 'Reading the help pages…'}
        </p>
      </header>

      <div className="dialog-box p-4">
        <label className="relative block">
          <span className="sr-only">Search tutorials</span>
          <IconSearch className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-lg text-ink-mute" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            type="search"
            placeholder="Search tutorials…"
            className="w-full rounded-[2px] border-ink bg-parch py-2.5 pl-10 pr-3 text-sm font-bold text-ink placeholder:text-ink-mute/70 focus:outline-none"
            style={{ borderWidth: 3 }}
          />
        </label>
      </div>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading ? (
        <GridSkeleton count={12} />
      ) : rows.length === 0 ? (
        <EmptyState title="No tutorials match." hint="Try a different search." />
      ) : (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {rows.map((t, i) => (
            <TutorialCard key={t.guid} t={t} index={i} />
          ))}
        </div>
      )}
    </div>
  )
}

function TutorialCard({ t, index }: { t: TutorialSummary; index: number }) {
  return (
    <Link
      to={`/tutorials/${t.guid}`}
      className="pixel-panel anim-pop flex items-center gap-3 p-4 transition-transform hover:-translate-y-1"
      style={{ animationDelay: `${Math.min(index, 18) * 16}ms` }}
    >
      <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-[2px] bg-ink/8 text-2xl text-lumen-deep">
        <IconBook />
      </span>
      <div className="min-w-0 flex-1">
        <h3 className="line-clamp-2 text-sm font-extrabold leading-snug text-ink">{prettyName(t)}</h3>
        <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
          {t.pageCount != null && (
            <Tag>
              {t.pageCount} {t.pageCount === 1 ? 'page' : 'pages'}
            </Tag>
          )}
        </div>
      </div>
    </Link>
  )
}
