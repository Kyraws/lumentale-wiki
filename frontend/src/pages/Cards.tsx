import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { CardSummary } from '../lib/types'
import { TypeBadge, Tag } from '../components/Badge'
import { GridSkeleton, ErrorState, EmptyState } from '../components/States'
import { IconCard } from '../components/Icons'
import { elementColor } from '../lib/game'
import HoloCard from './HoloCard'

export default function Cards() {
  const { data, loading, error, reload } = useApi<CardSummary[]>('/api/cards')
  const [rarity, setRarity] = useState<string | null>(null)
  const [ele, setEle] = useState<string | null>(null)

  const rarities = useMemo(
    () => (data ? [...new Set(data.map((c) => c.rarity).filter(Boolean) as string[])].sort() : []),
    [data],
  )
  const elements = useMemo(
    () => (data ? [...new Set(data.map((c) => c.ele).filter(Boolean) as string[])].sort() : []),
    [data],
  )

  const rows = useMemo(() => {
    if (!data) return []
    return data.filter((c) => {
      if (rarity && c.rarity !== rarity) return false
      if (ele && c.ele !== ele) return false
      return true
    })
  }, [data, rarity, ele])

  return (
    <div className="flex flex-col gap-5">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="flex items-center gap-2 text-lg text-cream text-pixel-shadow">
            <IconCard className="text-xl" /> Cards
          </h1>
          <p className="mt-1 text-sm text-cream/60">
            {data ? `${rows.length} of ${data.length} cards` : 'Shuffling the deck…'}
          </p>
        </div>
      </header>

      {/* Filter console */}
      <div className="dialog-box flex flex-col gap-4 p-4">
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="mr-1 w-14 text-[0.6rem] uppercase tracking-wide text-cream/50">Rarity</span>
          {rarities.map((r) => {
            const on = rarity === r
            return (
              <button
                key={r}
                onClick={() => setRarity(on ? null : r)}
                aria-pressed={on}
                className="type-chip transition-transform hover:-translate-y-0.5"
                style={{
                  backgroundColor: 'var(--color-gold)',
                  opacity: !rarity || on ? 1 : 0.4,
                  boxShadow: on ? '0 0 0 2px var(--color-gold), 2px 2px 0 0 rgba(0,0,0,0.3)' : undefined,
                }}
              >
                {r}
              </button>
            )
          })}
        </div>
        {elements.length > 0 && (
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="mr-1 w-14 text-[0.6rem] uppercase tracking-wide text-cream/50">Element</span>
            {elements.map((e) => {
              const on = ele === e
              return (
                <button
                  key={e}
                  onClick={() => setEle(on ? null : e)}
                  aria-pressed={on}
                  className="type-chip transition-transform hover:-translate-y-0.5"
                  style={{
                    backgroundColor: elementColor(e),
                    opacity: !ele || on ? 1 : 0.4,
                    boxShadow: on ? '0 0 0 2px var(--color-gold), 2px 2px 0 0 rgba(0,0,0,0.3)' : undefined,
                  }}
                >
                  {e}
                </button>
              )
            })}
          </div>
        )}
      </div>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading ? (
        <GridSkeleton count={18} />
      ) : rows.length === 0 ? (
        <EmptyState title="No cards match." hint="Try clearing a filter." />
      ) : (
        // Full-bleed breakout (like Dex/Items) with fewer columns: each card
        // tile renders much larger and the art fills its rectangle.
        <div className="mx-[calc(50%-50vw)] px-4 sm:px-6">
          <div className="mx-auto grid max-w-[100rem] grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
            {rows.map((c, i) => (
              <CardCell key={c.guid} c={c} index={i} />
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function CardCell({ c, index }: { c: CardSummary; index: number }) {
  return (
    <Link
      to={`/cards/${c.guid}`}
      className="holo-cell anim-pop group flex flex-col gap-2 text-center transition-transform hover:-translate-y-1 focus-visible:outline-none"
      style={{ animationDelay: `${Math.min(index, 24) * 16}ms` }}
      aria-label={`${c.name ?? 'Unknown card'}${c.rarity ? `, ${c.rarity}` : ''}`}
    >
      <HoloCard
        art={c.art} alt={c.name ?? 'Card'} rarity={c.rarity}
        holo={c.holo} mask={c.mask} holoTilingX={c.holoTilingX} holoTilingY={c.holoTilingY}
      />
      <span className="line-clamp-1 text-sm font-extrabold text-cream text-pixel-shadow">
        {c.name ?? 'Unknown card'}
      </span>
      <span className="flex flex-wrap items-center justify-center gap-1.5">
        {c.rarity && <Tag>{c.rarity}</Tag>}
        {c.ele && <TypeBadge type={c.ele} small />}
      </span>
    </Link>
  )
}
