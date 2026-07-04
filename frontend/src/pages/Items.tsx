import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { ItemSummary } from '../lib/types'
import { Tag } from '../components/Badge'
import { GridSkeleton, ErrorState, EmptyState } from '../components/States'
import { IconSearch } from '../components/Icons'
import { itemTypeLabel } from '../lib/game'

const TYPE_TINT: Record<string, string> = {
  Recipe: 'var(--color-gold)',
  Ingredient: 'var(--color-el-grass)',
  'Key Item': 'var(--color-el-aura)',
  Bilia: 'var(--color-sky)',
  Other: 'var(--color-ink-mute)',
}

export default function Items() {
  const { data, loading, error, reload } = useApi<ItemSummary[]>('/api/items')
  const [q, setQ] = useState('')
  const [type, setType] = useState<string | null>(null)
  const [storyOnly, setStoryOnly] = useState(false)

  const types = useMemo(
    () => (data ? [...new Set(data.map((i) => i.type).filter(Boolean) as string[])].sort() : []),
    [data],
  )

  const rows = useMemo(() => {
    if (!data) return []
    const needle = q.trim().toLowerCase()
    return data.filter((i) => {
      if (type && i.type !== type) return false
      if (storyOnly && !i.storyGiven) return false
      if (needle && !i.name.toLowerCase().includes(needle)) return false
      return true
    })
  }, [data, q, type, storyOnly])

  return (
    // Same full-bleed breakout as the Dex: wider than the Shell's max-w-7xl so
    // the grid gets big, dex-style cards.
    <div className="mx-[calc(50%-50vw)] px-4 sm:px-6">
    <div className="mx-auto flex max-w-[100rem] flex-col gap-5">
      <header>
        <h1 className="text-lg text-cream text-pixel-shadow">Items</h1>
        <p className="mt-1 text-sm text-cream/60">
          {data ? `${rows.length} of ${data.length} items` : 'Loading the bag…'}
        </p>
      </header>

      <div className="dialog-box flex flex-col gap-4 p-4">
        <label className="relative block">
          <span className="sr-only">Search items</span>
          <IconSearch className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-lg text-ink-mute" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            type="search"
            placeholder="Search items…"
            className="w-full rounded-[2px] border-ink bg-parch py-2.5 pl-10 pr-3 text-sm font-bold text-ink placeholder:text-ink-mute/70 focus:outline-none"
            style={{ borderWidth: 3 }}
          />
        </label>
        <div className="flex flex-wrap items-center gap-2">
          <span className="mr-1 text-[0.6rem] uppercase tracking-wide text-cream/50">Type</span>
          {types.map((t) => (
            <button
              key={t}
              onClick={() => setType(type === t ? null : t)}
              aria-pressed={type === t}
              className="type-chip transition-transform hover:-translate-y-0.5"
              style={{
                backgroundColor: TYPE_TINT[t] ?? 'var(--color-ink-mute)',
                opacity: !type || type === t ? 1 : 0.4,
                boxShadow: type === t ? '0 0 0 2px var(--color-gold), 2px 2px 0 0 rgba(0,0,0,0.3)' : undefined,
              }}
            >
              {itemTypeLabel(t)}
            </button>
          ))}
          <button
            onClick={() => setStoryOnly((v) => !v)}
            aria-pressed={storyOnly}
            title="Only items handed out by a story event"
            className={`ml-auto rounded-[2px] border-2 px-3 py-1.5 text-[0.6rem] font-extrabold uppercase tracking-wide transition-colors ${
              storyOnly ? 'border-lumen bg-lumen/20 text-lumen' : 'border-cream/15 text-cream/60 hover:bg-white/5'
            }`}
          >
            📜 Given in story
          </button>
        </div>
      </div>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading ? (
        <GridSkeleton count={18} />
      ) : rows.length === 0 ? (
        <EmptyState title="No items match." hint="Try clearing a filter." />
      ) : (
        <div className="grid grid-cols-3 gap-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 xl:grid-cols-8">
          {rows.map((it, i) => (
            <ItemCard key={it.guid} it={it} index={i} />
          ))}
        </div>
      )}
    </div>
    </div>
  )
}

function ItemCard({ it, index }: { it: ItemSummary; index: number }) {
  return (
    <Link
      to={`/items/${it.guid}`}
      className="pixel-panel anim-pop group flex flex-col items-center p-3 text-center transition-transform hover:-translate-y-1"
      style={{ animationDelay: `${Math.min(index, 24) * 16}ms` }}
    >
      <div className="pixel-screen flex aspect-square w-full items-center justify-center p-2">
        {it.icon ? (
          <img
            src={it.icon}
            alt=""
            loading="lazy"
            className="sprite h-full w-full object-contain transition-transform group-hover:scale-110"
          />
        ) : (
          <span className="text-2xl text-ink-mute/50" style={{ fontFamily: 'var(--font-display)' }}>?</span>
        )}
      </div>
      <span className="mt-2 line-clamp-1 text-sm font-extrabold text-ink">{it.name}</span>
      <span className="mt-1.5 flex flex-wrap items-center justify-center gap-1.5">
        {it.type && <Tag>{itemTypeLabel(it.type)}</Tag>}
        {it.price != null && (
          <span className="text-xs font-extrabold text-lumen-deep" style={{ fontFamily: 'var(--font-pixel)' }}>
            {it.price}₲
          </span>
        )}
      </span>
    </Link>
  )
}
