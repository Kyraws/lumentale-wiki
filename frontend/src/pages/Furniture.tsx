import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { FurnitureSummary } from '../lib/types'
import Sprite from '../components/Sprite'
import { Tag } from '../components/Badge'
import { GridSkeleton, ErrorState, EmptyState } from '../components/States'
import { IconSearch, IconFurniture } from '../components/Icons'
import { rarityMeta } from './furnitureMeta'

// The /api/furniture list now carries provenance flags (sold / questReward) so
// the page can filter by "where do I get this?" without a per-row request. The
// shared FurnitureSummary type doesn't declare them yet, so we extend locally.
type FurnitureRow = FurnitureSummary & { sold?: boolean; questReward?: boolean }

type SourceFilter = 'all' | 'shop' | 'quest' | 'none'
type SortKey = 'name' | 'price-asc' | 'price-desc' | 'rarity'

const SOURCE_TABS: { key: SourceFilter; label: string }[] = [
  { key: 'all', label: 'All' },
  { key: 'shop', label: 'Sold in shops' },
  { key: 'quest', label: 'Quest reward' },
  { key: 'none', label: 'No known source' },
]

export default function Furniture() {
  const { data, loading, error, reload } = useApi<FurnitureRow[]>('/api/furniture')
  const [q, setQ] = useState('')
  const [source, setSource] = useState<SourceFilter>('all')
  const [rarity, setRarity] = useState<string>('all')
  const [sort, setSort] = useState<SortKey>('name')

  // Counts per source tab, computed once over the full set (for the tab badges).
  const counts = useMemo(() => {
    const c = { all: 0, shop: 0, quest: 0, none: 0 }
    for (const f of data ?? []) {
      c.all++
      if (f.sold) c.shop++
      if (f.questReward) c.quest++
      if (!f.sold && !f.questReward) c.none++
    }
    return c
  }, [data])

  const rows = useMemo(() => {
    if (!data) return []
    const needle = q.trim().toLowerCase()
    let out = data.filter((f) => {
      if (needle && !(f.name ?? '').toLowerCase().includes(needle)) return false
      if (source === 'shop' && !f.sold) return false
      if (source === 'quest' && !f.questReward) return false
      if (source === 'none' && (f.sold || f.questReward)) return false
      if (rarity !== 'all' && String(f.rarity ?? '') !== rarity) return false
      return true
    })
    out = [...out].sort((a, b) => {
      switch (sort) {
        case 'price-asc':
          return (a.price ?? Infinity) - (b.price ?? Infinity)
        case 'price-desc':
          return (b.price ?? -Infinity) - (a.price ?? -Infinity)
        case 'rarity':
          return (Number(b.rarity) || 0) - (Number(a.rarity) || 0) ||
            (a.name ?? '').localeCompare(b.name ?? '')
        default:
          return (a.name ?? '').localeCompare(b.name ?? '')
      }
    })
    return out
  }, [data, q, source, rarity, sort])

  const rarities = useMemo(() => {
    const set = new Set<string>()
    for (const f of data ?? []) if (f.rarity != null) set.add(String(f.rarity))
    return [...set].sort()
  }, [data])

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="flex items-center gap-2 text-lg text-cream text-pixel-shadow">
          <IconFurniture className="text-xl" /> Furniture
        </h1>
        <p className="mt-1 text-sm text-cream/60">
          {data ? `${rows.length} of ${data.length} pieces` : 'Unpacking the catalog…'}
          {data && counts.shop + counts.quest > 0 && (
            <span className="text-cream/40">
              {' '}· {counts.shop + counts.quest} with a known source
            </span>
          )}
        </p>
      </header>

      <div className="dialog-box flex flex-col gap-4 p-4">
        <label className="relative block">
          <span className="sr-only">Search furniture</span>
          <IconSearch className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-lg text-ink-mute" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            type="search"
            placeholder="Search furniture…"
            className="w-full rounded-[2px] border-ink bg-parch py-2.5 pl-10 pr-3 text-sm font-bold text-ink placeholder:text-ink-mute/70 focus:outline-none"
            style={{ borderWidth: 3 }}
          />
        </label>

        {/* Source filter tabs */}
        <div className="flex flex-wrap gap-1.5">
          {SOURCE_TABS.map((t) => (
            <button
              key={t.key}
              onClick={() => setSource(t.key)}
              className={`rounded-[2px] border-2 px-2.5 py-1 text-xs font-extrabold transition-colors ${
                source === t.key
                  ? 'border-ink bg-ink text-cream'
                  : 'border-ink/20 bg-parch text-ink hover:bg-ink/10'
              }`}
            >
              {t.label}
              <span className="ml-1 opacity-60">{counts[t.key]}</span>
            </button>
          ))}
        </div>

        {/* Rarity + sort */}
        <div className="flex flex-wrap items-center gap-3">
          {rarities.length > 1 && (
            <div className="flex flex-wrap items-center gap-1.5">
              <span className="text-xs font-extrabold uppercase tracking-wide text-ink-mute">Rarity</span>
              <FilterChip active={rarity === 'all'} onClick={() => setRarity('all')}>
                All
              </FilterChip>
              {rarities.map((r) => {
                const m = rarityMeta(r)
                return (
                  <FilterChip key={r} active={rarity === r} onClick={() => setRarity(r)}>
                    {m?.label ?? `R${r}`}
                  </FilterChip>
                )
              })}
            </div>
          )}
          <label className="ml-auto flex items-center gap-2 text-xs font-extrabold uppercase tracking-wide text-ink-mute">
            Sort
            <select
              value={sort}
              onChange={(e) => setSort(e.target.value as SortKey)}
              className="rounded-[2px] border-2 border-ink/20 bg-parch px-2 py-1 text-xs font-extrabold text-ink focus:outline-none"
            >
              <option value="name">Name</option>
              <option value="price-asc">Price ↑</option>
              <option value="price-desc">Price ↓</option>
              <option value="rarity">Rarity</option>
            </select>
          </label>
        </div>
      </div>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading ? (
        <GridSkeleton count={18} />
      ) : rows.length === 0 ? (
        <EmptyState title="No furniture matches." hint="Try a different search or filter." />
      ) : (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
          {rows.map((f, i) => (
            <FurnitureCard key={f.guid} f={f} index={i} />
          ))}
        </div>
      )}
    </div>
  )
}

function FilterChip({
  active,
  onClick,
  children,
}: {
  active: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      onClick={onClick}
      className={`rounded-[2px] border-2 px-2 py-0.5 text-xs font-extrabold transition-colors ${
        active ? 'border-ink bg-ink text-cream' : 'border-ink/20 bg-parch text-ink hover:bg-ink/10'
      }`}
    >
      {children}
    </button>
  )
}

function FurnitureCard({ f, index }: { f: FurnitureRow; index: number }) {
  const rar = rarityMeta(f.rarity)
  return (
    <Link
      to={`/furniture/${f.guid}`}
      className="pixel-panel anim-pop flex items-center gap-3 p-3 transition-transform hover:-translate-y-1"
      style={{ animationDelay: `${Math.min(index, 20) * 16}ms` }}
    >
      <div className="pixel-screen flex h-16 w-16 shrink-0 items-center justify-center p-1">
        <Sprite src={f.icon} alt={f.name ?? 'Furniture'} size={56} />
      </div>
      <div className="min-w-0 flex-1">
        <span className="line-clamp-2 text-sm font-extrabold leading-snug text-ink">
          {f.name ?? 'Unknown furniture'}
        </span>
        <span className="mt-1 flex flex-wrap items-center gap-1.5">
          {rar ? (
            <span
              className="rounded-[2px] px-1.5 py-0.5 text-[0.6rem] font-extrabold uppercase"
              style={{ fontFamily: 'var(--font-pixel)', background: rar.bg, color: rar.fg }}
            >
              {rar.label}
            </span>
          ) : (
            f.rarity && <Tag>R{f.rarity}</Tag>
          )}
          {f.size && (
            <span className="text-xs font-extrabold text-ink-mute" style={{ fontFamily: 'var(--font-pixel)' }}>
              {f.size}
            </span>
          )}
          {f.carpet && <Tag>Carpet</Tag>}
        </span>
        <span className="mt-1 flex items-center gap-1.5">
          {f.price != null && (
            <span className="text-xs font-extrabold text-lumen-deep" style={{ fontFamily: 'var(--font-pixel)' }}>
              {f.price.toLocaleString()}₲
            </span>
          )}
          {f.sold && <SourceDot title="Sold in shops" className="bg-lumen-deep" />}
          {f.questReward && <SourceDot title="Quest reward" className="bg-gold-deep" />}
        </span>
      </div>
    </Link>
  )
}

function SourceDot({ title, className }: { title: string; className: string }) {
  return <span title={title} className={`inline-block h-2 w-2 rounded-full ${className}`} aria-label={title} />
}
