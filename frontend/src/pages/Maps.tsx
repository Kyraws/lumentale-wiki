import { useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { MapSummary } from '../lib/types'
import { RegionBadge, Tag } from '../components/Badge'
import { ErrorState, Skeleton } from '../components/States'
import { IconSearch } from '../components/Icons'
import { cleanMapName } from '../lib/mapName'
import { MapView } from './MapDetail'

type RegionFilter = 'all' | 'north' | 'south' | 'other'

/** Prologue / hub / center maps belong to EVERY playthrough, so they show under
 *  both the north and the south selection (project rule). Hub = Magnolia city;
 *  Center = the central wilderness ring (Areas 01–05 + caves); Prologue = Iris
 *  Hamlet. The refined region comes from the curated layer (the engine itself
 *  tags these RegionSide 0). */
const isEveryRun = (m: MapSummary) =>
  m.region === 'hub' || m.region === 'center' || m.region === 'prologue'

/** Bucket a map into a rail group header. */
function groupOf(m: MapSummary): (typeof GROUP_ORDER)[number] {
  if (m.region === 'prologue') return 'Prologue — Iris Hamlet'
  if (m.region === 'hub') return 'Magnolia Hub'
  if (m.region === 'center') return 'Center'
  if (m.region === 'north') return 'North'
  if (m.region === 'south') return 'South'
  return 'Other'
}
const GROUP_ORDER = ['Prologue — Iris Hamlet', 'Magnolia Hub', 'Center', 'North', 'South', 'Other'] as const

/**
 * World map browser — the two-pane layout mirrored from the Story page: the left
 * rail is a searchable, region-grouped list of locations and the selected map
 * reads inline on the right (tile, markers, connections, wild creatures, …).
 */
export default function Maps() {
  const { data, loading, error, reload } = useApi<MapSummary[]>('/api/maps')
  const [q, setQ] = useState('')
  const [region, setRegion] = useState<RegionFilter>('all')
  const [onlySpawns, setOnlySpawns] = useState(false)
  const [params, setParams] = useSearchParams()
  const sel = params.get('map')
  const pick = (guid: string) => setParams({ map: guid })

  const filtered = useMemo(() => {
    if (!data) return []
    const needle = q.trim().toLowerCase()
    return data
      .filter((m) => {
        if (onlySpawns && m.spawns === 0) return false
        if (region === 'north' || region === 'south') {
          if (m.region !== region && !isEveryRun(m)) return false
        } else if (region === 'other') {
          if (m.region === 'north' || m.region === 'south' || isEveryRun(m)) return false
        }
        if (
          needle &&
          !cleanMapName(m.name, m.mapName, m.displayName).toLowerCase().includes(needle) &&
          !m.name.toLowerCase().includes(needle)
        )
          return false
        return true
      })
      .sort(
        (a, b) =>
          b.spawns - a.spawns ||
          cleanMapName(a.name, a.mapName, a.displayName).localeCompare(
            cleanMapName(b.name, b.mapName, b.displayName),
          ),
      )
  }, [data, q, region, onlySpawns])

  // Group the filtered list under region headers, in a fixed order.
  const groups = useMemo(() => {
    const by: Record<string, MapSummary[]> = {}
    for (const m of filtered) (by[groupOf(m)] ??= []).push(m)
    return GROUP_ORDER.filter((g) => by[g]?.length).map((g) => ({ title: g, maps: by[g] }))
  }, [filtered])

  // Keep the selected row anchored in the rail (deep links / connection jumps).
  const listRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const c = listRef.current
    const el = c?.querySelector<HTMLElement>('[aria-current="true"]')
    if (!c || !el) return
    const cr = c.getBoundingClientRect()
    const er = el.getBoundingClientRect()
    c.scrollTo({
      top: c.scrollTop + (er.top - cr.top) - (c.clientHeight / 2 - el.clientHeight / 2),
      behavior: 'smooth',
    })
  }, [sel, region, q, data])

  return (
    // Breakout: the map view + rail want more width than the global container.
    <div className="mx-[calc(50%-50vw)] px-4 sm:px-6">
      <div className="mx-auto flex max-w-[100rem] flex-col gap-5">
        <header className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-lg text-cream text-pixel-shadow">World Map</h1>
            <p className="mt-1 max-w-prose text-sm text-cream/60">
              {data ? `${filtered.length} of ${data.length} locations` : 'Loading the overworld…'} — pick one on the left.
            </p>
          </div>
          <div className="flex gap-1.5">
            {(['all', 'north', 'south', 'other'] as RegionFilter[]).map((r) => (
              <button
                key={r}
                onClick={() => setRegion(r)}
                aria-pressed={region === r}
                className={`rounded-[2px] border-2 px-3 py-1.5 text-[0.6rem] font-extrabold uppercase tracking-wide transition-colors ${
                  region === r ? 'border-gold bg-gold/25 text-gold' : 'border-cream/15 text-cream/60 hover:bg-white/5'
                }`}
              >
                {r === 'all' ? '◆ All' : r === 'north' ? '▲ North' : r === 'south' ? '▼ South' : 'Other'}
              </button>
            ))}
          </div>
        </header>

        {error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : loading || !data ? (
          <Skeleton className="h-96 w-full" />
        ) : (
          <div className="grid items-start gap-5 lg:grid-cols-[340px_1fr]">
            {/* --- left rail: location browser --- */}
            <aside className="dialog-box flex flex-col gap-3 p-3 lg:sticky lg:top-4 lg:max-h-[calc(100vh-2rem)]">
              <label className="relative block">
                <span className="sr-only">Search locations</span>
                <IconSearch className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-base text-ink-mute" />
                <input
                  value={q}
                  onChange={(e) => setQ(e.target.value)}
                  type="search"
                  placeholder="Search locations…"
                  className="w-full rounded-[2px] border-ink bg-parch py-2 pl-9 pr-3 text-sm font-bold text-ink placeholder:text-ink-mute/70 focus:outline-none"
                  style={{ borderWidth: 3 }}
                />
              </label>
              <button
                onClick={() => setOnlySpawns((v) => !v)}
                aria-pressed={onlySpawns}
                className={`shrink-0 rounded-[2px] border-2 px-3 py-1.5 text-[0.6rem] font-extrabold uppercase tracking-wide transition-colors ${
                  onlySpawns ? 'border-lumen bg-lumen/20 text-lumen' : 'border-cream/15 text-cream/60 hover:bg-white/5'
                }`}
              >
                Has creatures
              </button>

              <div ref={listRef} className="flex-1 overflow-y-auto pr-1">
                {groups.length === 0 ? (
                  <p className="px-1 py-2 text-sm text-cream/60">No locations match.</p>
                ) : (
                  <div className="flex flex-col gap-3">
                    {groups.map((g) => (
                      <div key={g.title}>
                        <div className="mb-1 px-1 text-[0.62rem] font-extrabold uppercase tracking-wide text-cream/50">
                          {g.title} <span className="text-cream/30">· {g.maps.length}</span>
                        </div>
                        <ul className="flex flex-col gap-1">
                          {g.maps.map((m) => (
                            <MapRow key={m.guid} m={m} on={m.guid === sel} pick={pick} />
                          ))}
                        </ul>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </aside>

            {/* --- right pane: the selected map, inline --- */}
            <main className="min-w-0">
              {sel ? (
                <MapView key={sel} guid={sel} mapHref={(g) => `/maps?map=${g}`} />
              ) : (
                <div className="pixel-panel flex flex-col items-center gap-3 p-10 text-center">
                  <span className="text-4xl text-ink-mute" style={{ fontFamily: 'var(--font-display)' }}>
                    ▦
                  </span>
                  <p className="text-sm font-bold text-ink-soft">Pick a location from the list to explore it.</p>
                  <p className="max-w-prose text-xs text-ink-mute">
                    Each map shows its tile with spawn, item and connection markers, the wild creatures and levels, shops, and where it leads.
                  </p>
                </div>
              )}
            </main>
          </div>
        )}
      </div>
    </div>
  )
}

function MapRow({ m, on, pick }: { m: MapSummary; on: boolean; pick: (guid: string) => void }) {
  return (
    <li>
      <button
        type="button"
        aria-current={on}
        onClick={() => pick(m.guid)}
        className={`flex w-full items-center gap-2.5 rounded-[2px] border-2 p-1.5 text-left transition-colors ${
          on ? 'border-gold bg-gold/15' : 'border-transparent hover:bg-white/5'
        }`}
      >
        <span className="pixel-screen h-11 w-11 shrink-0 overflow-hidden">
          {m.tile ? (
            <img src={m.tile} alt="" loading="lazy" className="sprite h-full w-full object-cover" />
          ) : (
            <span className="flex h-full w-full items-center justify-center text-ink-mute/50" style={{ fontFamily: 'var(--font-display)' }}>
              ?
            </span>
          )}
        </span>
        <span className="min-w-0 flex-1">
          <span className={`block truncate text-sm font-extrabold leading-snug ${on ? 'text-gold' : 'text-cream'}`}>
            {cleanMapName(m.name, m.mapName, m.displayName)}
          </span>
          <span className="mt-0.5 flex flex-wrap items-center gap-1.5">
            {m.region && (m.region === 'north' || m.region === 'south') && <RegionBadge region={m.region} />}
            {m.region === 'hub' && (
              <span className="inline-flex items-center gap-1 rounded-[2px] border border-gold/50 bg-gold/15 px-1 py-0.5 text-[0.55rem] font-extrabold uppercase tracking-wide text-gold">
                ✦ Hub
              </span>
            )}
            {m.region === 'center' && (
              <span className="inline-flex items-center gap-1 rounded-[2px] border border-gold/50 bg-gold/10 px-1 py-0.5 text-[0.55rem] font-extrabold uppercase tracking-wide text-gold/90">
                ● Center
              </span>
            )}
            {m.interior && <Tag>Interior</Tag>}
            {m.spawns > 0 && <span className="text-[0.65rem] font-extrabold text-lumen-deep">{m.spawns} spawns</span>}
          </span>
        </span>
      </button>
    </li>
  )
}
