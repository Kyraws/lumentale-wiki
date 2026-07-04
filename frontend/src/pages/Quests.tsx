import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { QuestSummary } from '../lib/types'
import { GridSkeleton, ErrorState, EmptyState } from '../components/States'
import { Tag } from '../components/Badge'
import { IconQuest } from '../components/Icons'
import { fmtNum } from '../lib/game'

// v3 quest `type` is a small enum. NOTE: v3 has no city/track on quests.
const QUEST_TYPE: Record<number, { label: string; tint: string }> = {
  0: { label: 'Main story', tint: 'var(--color-gold)' },
  1: { label: 'Side quest', tint: 'var(--color-sky)' },
  2: { label: 'Task', tint: 'var(--color-lumen)' },
}

function typeInfo(t?: number | null) {
  return t != null ? QUEST_TYPE[t] : undefined
}

export default function Quests() {
  const { data, loading, error, reload } = useApi<QuestSummary[]>('/api/quests')
  const [type, setType] = useState<number | null>(null)

  const rows = useMemo(() => {
    if (!data) return []
    return data.filter((q) => (type == null ? true : q.type === type))
  }, [data, type])

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="text-lg text-cream text-pixel-shadow">Quests</h1>
        <p className="mt-1 text-sm text-cream/60">
          {data ? `${rows.length} of ${data.length} quests` : 'Loading the quest log…'}
        </p>
      </header>

      <div className="dialog-box flex flex-wrap items-center gap-2 p-4">
        <span className="mr-1 text-[0.6rem] uppercase tracking-wide text-cream/50">Type</span>
        {Object.keys(QUEST_TYPE).map((k) => {
          const n = Number(k)
          const info = QUEST_TYPE[n]
          const active = type === n
          return (
            <button
              key={k}
              onClick={() => setType(active ? null : n)}
              aria-pressed={active}
              className="rounded-[2px] px-2.5 py-1 text-[0.55rem] font-extrabold uppercase tracking-wide text-night transition-transform hover:-translate-y-0.5"
              style={{
                backgroundColor: info.tint,
                opacity: type == null || active ? 1 : 0.4,
                boxShadow: active ? '0 0 0 2px var(--color-gold), 2px 2px 0 0 rgba(0,0,0,0.3)' : undefined,
              }}
            >
              {info.label}
            </button>
          )
        })}
      </div>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading ? (
        <GridSkeleton count={9} />
      ) : rows.length === 0 ? (
        <EmptyState title="No quests match." hint="Try clearing the filter." />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {rows.map((q) => (
            <QuestCard key={q.guid} q={q} />
          ))}
        </div>
      )}
    </div>
  )
}

function QuestCard({ q }: { q: QuestSummary }) {
  const info = typeInfo(q.type)
  const tint = info?.tint ?? 'var(--color-ink-mute)'
  return (
    <Link
      to={`/quests/${q.guid}`}
      className="pixel-panel anim-pop flex flex-col gap-3 p-4 transition-transform hover:-translate-y-0.5"
      style={{ borderLeft: `6px solid ${tint}` }}
    >
      <div className="flex items-start gap-3">
        <span
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[2px] text-lg text-night"
          style={{ backgroundColor: tint }}
        >
          <IconQuest />
        </span>
        <h2 className="min-w-0 flex-1 text-sm leading-snug text-ink" style={{ fontFamily: 'var(--font-display)' }}>
          {q.title || q.name}
        </h2>
      </div>
      <div className="mt-auto flex flex-wrap items-center gap-2">
        {info && (
          <span
            className="rounded-[2px] px-1.5 py-0.5 text-[0.5rem] font-extrabold uppercase tracking-wide text-night"
            style={{ backgroundColor: tint }}
          >
            {info.label}
          </span>
        )}
        {q.giver ? <Tag>from {q.giver}</Tag> : null}
        <span className="ml-auto text-[0.65rem] font-bold text-ink-mute">{fmtNum(q.nodes)} steps</span>
      </div>
    </Link>
  )
}
