import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { CreatureSummary } from '../lib/types'
import { TypeBadge, EmotionBadge } from '../components/Badge'
import { GridSkeleton, ErrorState, EmptyState } from '../components/States'
import { IconSearch, IconStar } from '../components/Icons'
import { ALL_ELEMENTS, ALL_EMOTIONS, dexNo, elementColor, emotionColor, EMOTION_GLOSS } from '../lib/game'

type RegionFilter = 'all' | 'north' | 'south'

/** Variants of one species (e.g. a colour set or a regional pair) share ONE
 *  card; the art/name/types reflect the previewed variant. Each form stays
 *  independently linkable. */
type Group = { dex: number; species: string; forms: CreatureSummary[] }

/** True on touch / no-hover devices, kept in sync if the capability changes. */
function useCoarsePointer() {
  const [coarse, setCoarse] = useState(
    () => typeof window !== 'undefined' && window.matchMedia('(hover: none)').matches,
  )
  useEffect(() => {
    const mq = window.matchMedia('(hover: none)')
    const on = () => setCoarse(mq.matches)
    mq.addEventListener('change', on)
    return () => mq.removeEventListener('change', on)
  }, [])
  return coarse
}

export default function DexGrid() {
  const { data, loading, error, reload } = useApi<CreatureSummary[]>('/api/creatures')
  const [q, setQ] = useState('')
  const [ele, setEle] = useState<string | null>(null)
  const [emo, setEmo] = useState<string | null>(null)
  const [region, setRegion] = useState<RegionFilter>('all')

  // Filters NARROW which variants a card shows; a group survives if any shown
  // variant also matches the search box.
  const groups = useMemo<Group[]>(() => {
    if (!data) return []
    const needle = q.trim().toLowerCase()
    const shows = (c: CreatureSummary) => {
      if (ele && c.ele !== ele) return false
      if (emo && c.emo !== emo) return false
      if (region !== 'all' && !c.regions.includes(region)) return false
      return true
    }
    const searchMatch = (c: CreatureSummary) =>
      !needle ||
      c.species.toLowerCase().includes(needle) ||
      c.variant.toLowerCase().includes(needle) ||
      String(c.dex) === needle ||
      dexNo(c.dex).includes(needle)

    const byDex = new Map<number, Group>()
    for (const c of data) {
      if (!byDex.has(c.dex)) byDex.set(c.dex, { dex: c.dex, species: c.species, forms: [] })
      byDex.get(c.dex)!.forms.push(c)
    }
    return [...byDex.values()]
      .map((g) => ({ ...g, forms: g.forms.filter(shows) }))
      .filter((g) => g.forms.length > 0 && g.forms.some(searchMatch))
      .sort((a, b) => a.dex - b.dex)
  }, [data, q, ele, emo, region])

  const formCount = useMemo(() => groups.reduce((n, g) => n + g.forms.length, 0), [groups])

  return (
    // Full-bleed breakout from the Shell's max-w-7xl, re-capped wider so the
    // dex uses more of the screen (bigger cards, less side gutter).
    <div className="mx-[calc(50%-50vw)] px-4 sm:px-6">
    <div className="mx-auto flex max-w-[100rem] flex-col gap-5">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-lg text-cream text-pixel-shadow">Lumendex</h1>
          <p className="mt-1 text-sm text-cream/60">
            {data ? `${groups.length} species · ${formCount} forms` : 'Loading creatures…'}
          </p>
        </div>
      </header>

      {/* Filter console */}
      <div className="dialog-box flex flex-col gap-4 p-4">
        {/* Search */}
        <label className="relative block">
          <span className="sr-only">Search creatures by name or dex number</span>
          <IconSearch className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-lg text-ink-mute" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            type="search"
            inputMode="search"
            placeholder="Search by name or #number…"
            className="w-full rounded-[2px] border-3 border-ink bg-parch py-2.5 pl-10 pr-3 text-sm font-bold text-ink placeholder:text-ink-mute/70 focus:outline-none"
            style={{ borderWidth: 3 }}
          />
        </label>

        {/* Region toggle */}
        <div className="flex flex-wrap items-center gap-2">
          <span className="mr-1 text-[0.6rem] uppercase tracking-wide text-cream/50">Route</span>
          {(['all', 'north', 'south'] as RegionFilter[]).map((r) => (
            <button
              key={r}
              onClick={() => setRegion(r)}
              aria-pressed={region === r}
              className={`rounded-[2px] border-2 px-3 py-1.5 text-[0.6rem] font-extrabold uppercase tracking-wide transition-colors ${
                region === r
                  ? 'border-gold bg-gold/25 text-gold'
                  : 'border-cream/15 text-cream/60 hover:bg-white/5'
              }`}
            >
              {r === 'all' ? 'Both' : r}
            </button>
          ))}
        </div>

        {/* Element filter */}
        <FilterRow
          label="Element"
          options={ALL_ELEMENTS}
          active={ele}
          onPick={setEle}
          colorOf={elementColor}
        />
        {/* Emotion filter */}
        <FilterRow
          label="Emotion"
          options={ALL_EMOTIONS}
          active={emo}
          onPick={setEmo}
          colorOf={emotionColor}
          glossOf={(o) => EMOTION_GLOSS[o]}
        />
      </div>

      {/* Grid */}
      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading ? (
        <GridSkeleton count={18} />
      ) : groups.length === 0 ? (
        <EmptyState title="No creatures match." hint="Try clearing a filter or the search box." />
      ) : (
        <div className="grid grid-cols-3 gap-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 xl:grid-cols-8">
          {groups.map((g, i) => (
            <DexCard key={g.dex} g={g} index={i} />
          ))}
        </div>
      )}
    </div>
    </div>
  )
}

function FilterRow({
  label,
  options,
  active,
  onPick,
  colorOf,
  glossOf,
}: {
  label: string
  options: string[]
  active: string | null
  onPick: (v: string | null) => void
  colorOf: (v: string) => string
  glossOf?: (v: string) => string | undefined
}) {
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <span className="mr-1 w-14 text-[0.6rem] uppercase tracking-wide text-cream/50">{label}</span>
      {options.map((o) => {
        const on = active === o
        return (
          <button
            key={o}
            onClick={() => onPick(on ? null : o)}
            aria-pressed={on}
            title={glossOf?.(o)}
            className="type-chip transition-transform hover:-translate-y-0.5"
            style={{
              backgroundColor: colorOf(o),
              opacity: !active || on ? 1 : 0.4,
              boxShadow: on ? '0 0 0 2px var(--color-gold), 2px 2px 0 0 rgba(0,0,0,0.3)' : undefined,
            }}
          >
            {o}
          </button>
        )
      })}
    </div>
  )
}

/** One stacked, preloaded sprite layer with a graceful "?" fallback (mirrors
 *  the standalone <Sprite>, but layered so the variant swap is instant). */
function ArtLayer({ src, alt, on }: { src?: string | null; alt: string; on: boolean }) {
  const [failed, setFailed] = useState(false)
  const base = `absolute inset-0 flex items-center justify-center transition duration-150 ${
    on ? 'opacity-100' : 'opacity-0'
  }`
  if (!src || failed) {
    return (
      <div className={base} aria-hidden={!on}>
        <span className="text-ink-mute/50" style={{ fontFamily: 'var(--font-display)', fontSize: 26 }}>
          ?
        </span>
      </div>
    )
  }
  return (
    <img
      src={src}
      alt={on ? alt : ''}
      loading="lazy"
      onError={() => setFailed(true)}
      className={`sprite h-full w-full object-contain p-1 group-hover:scale-110 ${base}`}
      aria-hidden={!on}
    />
  )
}

function DexCard({ g, index }: { g: Group; index: number }) {
  const coarse = useCoarsePointer()
  const [sel, setSel] = useState(0)
  const multi = g.forms.length > 1
  const cur = Math.min(sel, g.forms.length - 1)
  const c = g.forms[cur] ?? g.forms[0]

  return (
    <div
      className="pixel-panel anim-pop group relative flex flex-col items-center p-3 text-center transition-transform hover:-translate-y-1"
      style={{ animationDelay: `${Math.min(index, 24) * 18}ms` }}
      onMouseLeave={() => {
        if (!coarse) setSel(0)
      }}
    >
      <span
        className="absolute left-2 top-2 z-10 rounded-[2px] bg-ink/8 px-1 text-[0.82rem] font-extrabold leading-none text-ink-soft"
        style={{ fontFamily: 'var(--font-pixel)' }}
      >
        {dexNo(g.dex)}
      </span>
      {g.forms.some((f) => f.hasLost) && (
        <span className="absolute right-2 top-2 z-10 text-berry" title="Has a 'lost' form">
          <IconStar className="text-sm" />
        </span>
      )}

      {/* Art well: all variant sprites stacked + preloaded; hotspots split the
          image so hovering a slice previews that variant, clicking navigates. */}
      <div className="pixel-screen relative mt-4 aspect-square w-full">
        {g.forms.map((f, i) => (
          <ArtLayer key={f.guid} src={f.menuArt} alt={f.species} on={i === cur} />
        ))}
        <div className="absolute inset-0 z-10 flex">
          {multi && !coarse ? (
            g.forms.map((f, i) => (
              <Link
                key={f.guid}
                to={`/dex/${f.guid}`}
                className="flex-1"
                onMouseEnter={() => setSel(i)}
                onFocus={() => setSel(i)}
                title={`${f.species} · ${f.variant}`}
                aria-label={`${f.species} — ${f.variant}`}
              />
            ))
          ) : (
            <Link
              to={`/dex/${c.guid}`}
              className="flex-1"
              aria-label={multi ? `${c.species} — ${c.variant}` : c.species}
            />
          )}
        </div>
      </div>

      {/* Variant chooser dots (multi only); on touch they tap-select, on web
          they hover-preview / click-navigate. Reserves height so cards align. */}
      {multi && (
        <div className="mt-1.5 flex items-center justify-center gap-1">
          {g.forms.map((f, i) =>
            coarse ? (
              <button
                key={f.guid}
                type="button"
                onClick={() => setSel(i)}
                aria-pressed={i === cur}
                aria-label={`Show ${f.species} ${f.variant}`}
                className="h-2 w-2 rounded-full border border-ink/40 transition-colors"
                style={{ backgroundColor: i === cur ? 'var(--color-gold)' : 'transparent' }}
              />
            ) : (
              <Link
                key={f.guid}
                to={`/dex/${f.guid}`}
                tabIndex={-1}
                onMouseEnter={() => setSel(i)}
                aria-label={`${f.species} ${f.variant}`}
                className="h-2 w-2 rounded-full border border-ink/40 transition-colors"
                style={{ backgroundColor: i === cur ? 'var(--color-gold)' : 'transparent' }}
              />
            ),
          )}
        </div>
      )}

      <Link to={`/dex/${c.guid}`} className="mt-1 flex flex-col items-center" tabIndex={-1}>
        <span className="line-clamp-1 text-sm font-extrabold text-ink">{c.species}</span>
        {c.variant !== 'Base Form' && (
          <span className="line-clamp-1 text-[0.65rem] font-bold text-ink-mute">{c.variant}</span>
        )}
      </Link>
      <span className="mt-1.5 flex flex-wrap items-center justify-center gap-1">
        <TypeBadge type={c.ele} small />
        <EmotionBadge emo={c.emo} small />
      </span>
    </div>
  )
}
