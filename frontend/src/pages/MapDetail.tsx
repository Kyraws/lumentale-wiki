import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { MapDetail as MDetail, Pos, SpawnForm, Connection } from '../lib/types'
import Sprite from '../components/Sprite'
import { TypeBadge, RegionBadge, Tag } from '../components/Badge'
import { ErrorState, Skeleton, EmptyState } from '../components/States'
import { IconBack } from '../components/Icons'
import { cleanMapName } from '../lib/mapName'
import { elementColor } from '../lib/game'

/** Humanize a map-object dev name: "Interactables_NPC_CampShop" → "Camp Shop",
 *  "NPC12 - ShopFurniture3" → "Shop Furniture 3". */
function cleanNpcName(name?: string | null): string | null {
  if (!name) return null
  return (
    name
      .replace(/Interact[ai]bles?/gi, '')
      .replace(/NPC\s*\d*/gi, '')
      .replace(/[_-]+/g, ' ')
      .replace(/(?<=[a-z])(?=[A-Z])/g, ' ')
      .replace(/(?<=[a-zA-Z])(?=\d)/g, ' ')
      .replace(/\s+/g, ' ')
      .trim() || null
  )
}

type Bounds = NonNullable<MDetail['bounds']>

/** A marker counts as "on the map" only if it lies within the playable gameplay-bounds
 * rect (a small pad lets border exits, which sit just outside, through). Some maps carry
 * SpawnArea volumes positioned well beyond their own bounds (unreachable / peripheral);
 * those would plot into the navy padding, so we cull them — the creatures still appear in
 * the Wild-Creatures list below. */
const BOUNDS_PAD = 0.04
function inPlayableBounds(pos: Pos, b: Bounds) {
  if (!b.sizeX || !b.sizeZ) return true
  const padX = b.sizeX * BOUNDS_PAD
  const padZ = b.sizeZ * BOUNDS_PAD
  return (
    pos.x! >= b.offsetX - b.sizeX / 2 - padX && pos.x! <= b.offsetX + b.sizeX / 2 + padX &&
    pos.z! >= b.offsetZ - b.sizeZ / 2 - padZ && pos.z! <= b.offsetZ + b.sizeZ / 2 + padZ
  )
}

/**
 * Project a map-local (x,z) marker onto the tile. The baked UI-map texture is a square
 * render covering `tileWorldSize` world units (= 3840px × MapScaleValue) centred on the
 * bounds offset — that square, NOT the (often non-square) gameplay bounds rect, is the
 * frame the image fills, so markers must project against it:
 *   span  = tileWorldSize
 *   fracX = (x − (offsetX − span/2)) / span
 *   fracZ = (z − (offsetZ − span/2)) / span
 *   left% = fracX·100;  top% = (1 − fracZ)·100   (z flipped → screen-up)
 * Falls back to the gameplay bounds rect if tileWorldSize is missing (older payloads).
 * Markers outside the playable bounds (beyond the tile's depicted area) are culled.
 */
function project(pos: Pos | null | undefined, b: Bounds) {
  if (!pos || pos.x == null || pos.z == null) return null
  if (!inPlayableBounds(pos, b)) return null
  const span = b.tileWorldSize
  // Calibrated frame (when the bake was rendered off the default framing) wins.
  const spanX = b.tileSpanX ?? (span > 0 ? span : b.sizeX)
  const spanZ = b.tileSpanZ ?? (span > 0 ? span : b.sizeZ)
  const centerX = b.tileCenterX ?? b.offsetX
  const centerZ = b.tileCenterZ ?? b.offsetZ
  if (!spanX || !spanZ) return null
  const fracX = (pos.x - (centerX - spanX / 2)) / spanX
  const fracZ = (pos.z - (centerZ - spanZ / 2)) / spanZ
  const left = fracX * 100
  const top = (1 - fracZ) * 100
  if (left < -2 || left > 102 || top < -2 || top > 102) return null
  return { left: `${left}%`, top: `${top}%` }
}

/** Route wrapper for a single map (deep link, e.g. from a creature's "Where to Find"). */
export default function MapDetail() {
  const { guid = '' } = useParams()
  return (
    <div className="flex flex-col gap-5">
      <Link to="/maps" className="inline-flex w-fit items-center gap-1 text-sm font-bold text-cream/70 hover:text-cream">
        <IconBack /> Back to World Map
      </Link>
      <MapView guid={guid} mapHref={(g) => `/maps/${g}`} />
    </div>
  )
}

/**
 * The full map view (tile + markers + connections + creatures + …), reusable both as
 * the standalone /maps/:guid page and inline in the World two-pane browser. {@code mapHref}
 * builds links to OTHER maps so they navigate within whichever context is hosting it.
 */
export function MapView({ guid, mapHref }: { guid: string; mapHref: (guid: string) => string }) {
  const { data, loading, error, reload } = useApi<MDetail>(`/api/maps/${guid}`)
  // Markers are ON by default — the bounds projection is verified accurate.
  const [layers, setLayers] = useState({ spawns: true, exits: true, pickups: true })

  if (error) return <ErrorState message={error} onRetry={reload} />
  if (loading || !data) return <Skeleton className="h-[60vh] w-full" />

  const name = cleanMapName(data.internalName, data.mapName, data.displayName)
  const showCodename = !!data.displayName && data.displayName.trim() !== data.internalName
  const bounds = data.bounds ?? null
  // Plot only when there's both a tile to plot on and the bounds to project with.
  const canPlot = !!data.tile && !!bounds

  return (
    <div className="flex flex-col gap-5">
      <header className="flex flex-wrap items-center gap-3">
        <div className="flex min-w-0 flex-col">
          <h1 className="text-lg text-cream text-pixel-shadow md:text-xl">{name}</h1>
          {showCodename && <span className="text-xs font-semibold text-cream/50">{data.internalName}</span>}
        </div>
        {data.region && (data.region === 'north' || data.region === 'south') && (
          <RegionBadge region={data.region} />
        )}
        {data.interior && <Tag>Interior</Tag>}
      </header>

      {/* Story-state switcher: this location exists as several maps the story swaps
          between (e.g. Iris Hamlet → Borgo Iride). Each state is its own page; the
          switcher links between them so they read as one place. */}
      {data.stateGroup.length > 1 && (
        <div className="dialog-box flex flex-wrap items-center gap-2 p-3">
          <span className="text-[0.7rem] font-extrabold uppercase tracking-wide text-ink-mute">
            Story state
          </span>
          {data.stateGroup.map((v) =>
            v.current ? (
              <span
                key={v.guid}
                aria-current="true"
                className="rounded-[2px] border-2 border-lumen bg-lumen/15 px-2.5 py-1 text-xs font-extrabold text-ink"
              >
                {v.label}
                {v.displayName && <span className="ml-1 font-bold text-ink-mute">· {v.displayName}</span>}
              </span>
            ) : (
              <Link
                key={v.guid}
                to={mapHref(v.guid)}
                className="rounded-[2px] border-2 border-ink/20 bg-parch px-2.5 py-1 text-xs font-bold text-ink-mute hover:border-ink/40 hover:text-ink"
              >
                {v.label}
              </Link>
            ),
          )}
        </div>
      )}

      {/* No overworld tile: this location isn't registered in the game's world-map
          UI graph (only 65 of 300 maps carry a UIMapReference), so the game itself
          renders no menu-map for it. We say so plainly instead of a broken frame. */}
      {!data.tile && (
        <section className="dialog-box p-4 md:p-5">
          <div className="pixel-screen flex aspect-[2/1] w-full max-w-2xl mx-auto flex-col items-center justify-center gap-2 p-6 text-center">
            <span className="text-3xl text-ink-mute/40" style={{ fontFamily: 'var(--font-display)' }}>?</span>
            <p className="text-sm font-extrabold text-ink">No menu map for this location.</p>
            <p className="max-w-md text-[0.7rem] font-bold leading-snug text-ink-mute">
              {data.interior
                ? 'Interiors are entered by walking and have no overworld map screen in the game.'
                : 'This area has no registered menu-map texture in the game files. The wiki shows its creatures, exits and items below.'}
            </p>
          </div>
        </section>
      )}

      {/* Tile screen with plotted markers */}
      {data.tile && (
        <section className="dialog-box p-3 md:p-4">
          <div className="pixel-screen relative mx-auto aspect-square w-full max-w-2xl overflow-hidden">
            <img
              src={data.tile}
              alt={`${name} map`}
              className="sprite h-full w-full object-contain"
            />
            {canPlot && (
              <>
                {layers.spawns && (
                  <PinLayer kp="s" items={data.spawnPoints} getPos={(s) => s.pos} bounds={bounds!}
                    clusterBg="var(--color-lumen)"
                    dot={(s) => (
                      <span className="block h-3.5 w-3.5 rounded-full border-2 border-ink"
                        style={{ backgroundColor: elementColor(s.forms[0]?.ele) }} />
                    )}
                    label={(s) => {
                      // species list reads better than the spawner's dev name ("Spawn01 (5)")
                      const sp = [...new Set(s.forms.map((f) => f.species))]
                      const head = sp.slice(0, 3).join(', ')
                      return sp.length ? (sp.length > 3 ? `${head} +${sp.length - 3}` : head)
                        : s.name || 'Wild encounter'
                    }} />
                )}
                {layers.pickups && (
                  <PinLayer kp="p" items={data.pickups} getPos={(p) => p.pos} bounds={bounds!}
                    clusterBg="var(--color-gold)"
                    dot={() => <span className="block h-2.5 w-2.5 rotate-45 border-2 border-ink bg-gold" />}
                    href={(p) => (p.itemGuid ? `/items/${p.itemGuid}` : undefined)}
                    label={(p) => `${p.itemName || p.name || 'Item'}${p.amount && p.amount > 1 ? ` ×${p.amount}` : ''}`} />
                )}
                {layers.exits && <ConnectionPins connections={data.connections} bounds={bounds!} mapHref={mapHref} />}
              </>
            )}
          </div>

          {canPlot && (
            <div className="mt-3 flex flex-col items-center gap-1.5">
              <div className="flex flex-wrap items-center justify-center gap-2 text-[0.6rem]">
                <LayerToggle on={layers.spawns} onClick={() => setLayers((l) => ({ ...l, spawns: !l.spawns }))} dot="var(--color-lumen)">
                  Spawns
                </LayerToggle>
                <LayerToggle on={layers.pickups} onClick={() => setLayers((l) => ({ ...l, pickups: !l.pickups }))} dot="var(--color-gold)">
                  Items
                </LayerToggle>
                <LayerToggle on={layers.exits} onClick={() => setLayers((l) => ({ ...l, exits: !l.exits }))} dot="var(--color-sky)">
                  Connections
                </LayerToggle>
              </div>
            </div>
          )}
        </section>
      )}

      {/* Spawns */}
      <Section title={`Wild Creatures (${data.spawns.length})`}>
        {data.spawns.length === 0 ? (
          <EmptyState title="No wild creatures here." />
        ) : (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
            {data.spawns.map((s) => (
              <SpawnCard key={s.guid} s={s} />
            ))}
          </div>
        )}
      </Section>

      {/* Shops */}
      {data.shops.length > 0 && (
        <Section title="Shops">
          <div className="flex flex-col gap-4">
            {data.shops.map((shop, i) => (
              <div key={i} className="rounded-[2px] bg-ink/5 p-3">
                <h3 className="mb-2 text-sm font-extrabold text-ink">{cleanNpcName(shop.npc) || 'Merchant'}</h3>
                <div className="flex flex-wrap gap-2">
                  {shop.entries.map((e, j) => (
                    <span key={j} className="inline-flex items-center gap-2 rounded-[2px] border-2 border-ink/20 bg-parch px-2 py-1 text-xs font-bold text-ink">
                      {e.icon && <img src={e.icon} alt="" className="sprite h-5 w-5" />}
                      {e.name || e.kind}
                      {e.price != null && <span className="text-lumen-deep">{e.price}₲</span>}
                    </span>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </Section>
      )}

      {/* Battles */}
      {data.battles.length > 0 && (
        <Section title="Trainers Here">
          <div className="flex flex-col gap-3">
            {data.battles.map((b, i) => {
              // Prefer the resolved trainer/boss name over the map-object codename,
              // and link it to its page.
              const named = b.fights.find((fi) => fi.name && fi.guid)
              const route = named?.kind === 'boss' ? 'bosses' : 'trainers'
              return (
                <div key={i} className="rounded-[2px] bg-ink/5 p-3">
                  <h3 className="mb-2 text-sm font-extrabold text-ink">
                    {named ? (
                      <Link to={`/${route}/${named.guid}`} className="hover:underline">
                        {named.name}
                      </Link>
                    ) : (
                      cleanNpcName(b.npc) || 'Scripted battle'
                    )}
                  </h3>
                  <div className="flex flex-wrap gap-2">
                    {b.fights.flatMap((fi) => fi.forms).map((fm, j) => (
                      <Link key={j} to={`/dex/${fm.guid}`} className="flex flex-col items-center" title={fm.species}>
                        <Sprite src={fm.menuArt} alt={fm.species} size={40} />
                      </Link>
                    ))}
                  </div>
                </div>
              )
            })}
          </div>
        </Section>
      )}

      {/* Connections: the full connectivity graph (seamless border crossings + doors),
          plus any blocked/unresolved door-exits not covered by the graph. */}
      {(data.connections.length > 0 || data.exits.some((e) => !e.targetGuid)) && (
        <Section title="Connections">
          <p className="mb-3 text-[0.7rem] font-bold leading-snug text-ink-mute">
            Where this area leads, plotted on the map. Doors / teleports sit at their exact
            spot; seamless border crossings (⇄) — where you walk into the next area — are
            placed on the edge facing that area, from the in-game world-map layout.
          </p>
          <div className="flex flex-wrap gap-2">
            {data.connections.map((c) => (
              <Link
                key={c.guid}
                to={mapHref(c.guid)}
                className="inline-flex flex-col rounded-[2px] border-2 border-sky/40 bg-sky/10 px-3 py-2 text-sm font-bold text-ink hover:bg-sky/20"
              >
                <span className="flex items-center gap-1.5">
                  <span aria-hidden className="text-sky-deep">{c.viaExit ? '↦' : '⇄'}</span>
                  {connLabel(c)}
                </span>
                <span className="mt-0.5 text-[0.6rem] font-bold text-ink-mute">
                  {c.viaExit ? 'Door / teleport' : 'Seamless border'}
                  {c.stateCount > 1 && ` · ${c.stateCount} story states`}
                  {c.stateCount <= 1 && c.direction === 'in' && ' · one-way in'}
                  {c.stateCount <= 1 && c.direction === 'out' && ' · one-way out'}
                  {c.stateCount <= 1 && c.conditions.length > 0 && ` · needs ${c.conditions.join(', ')}`}
                </span>
              </Link>
            ))}
            {/* blocked / unresolved door-exits the graph doesn't carry */}
            {data.exits
              .filter((e) => !e.targetGuid)
              .map((ex, i) => (
                <span
                  key={`x${i}`}
                  className="inline-flex items-center gap-2 rounded-[2px] border-2 border-ink/15 bg-ink/5 px-3 py-2 text-sm font-bold text-ink-mute"
                >
                  {ex.name || 'Blocked exit'}
                </span>
              ))}
          </div>
        </Section>
      )}
    </div>
  )
}

/* ---------- pieces ---------- */

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="pixel-panel p-4 md:p-5">
      <h2 className="mb-4 inline-block rounded-[2px] bg-ink px-2 py-1 text-[0.7rem] text-cream" style={{ fontFamily: 'var(--font-display)' }}>
        {title}
      </h2>
      {children}
    </section>
  )
}

/**
 * A clustered pin layer. Projects each item, then greedily merges pins within
 * CLUSTER_R% into a single count marker (hover lists them) so dense towns stay
 * readable instead of an overlapping blob. Lone pins render normally and link
 * when {@code href} is provided (exits → maps, items → items).
 */
const CLUSTER_R = 3.5
function PinLayer<T>({ kp, items, getPos, bounds, dot, label, href, clusterBg }: {
  kp: string
  items: T[]
  getPos: (t: T) => Pos | null | undefined
  bounds: Bounds
  dot: (t: T) => React.ReactNode
  label: (t: T) => string
  href?: (t: T) => string | undefined
  clusterBg: string
}) {
  const [open, setOpen] = useState<number | null>(null)
  const placed = items
    .map((it) => ({ it, p: project(getPos(it), bounds) }))
    .filter((x): x is { it: T; p: { left: string; top: string } } => !!x.p)

  const groups: { cx: number; cy: number; arr: T[] }[] = []
  for (const { it, p } of placed) {
    const L = parseFloat(p.left)
    const Y = parseFloat(p.top)
    const g = groups.find((g) => Math.hypot(g.cx - L, g.cy - Y) < CLUSTER_R)
    if (g) {
      g.arr.push(it)
      g.cx = (g.cx * (g.arr.length - 1) + L) / g.arr.length
      g.cy = (g.cy * (g.arr.length - 1) + Y) / g.arr.length
    } else groups.push({ cx: L, cy: Y, arr: [it] })
  }

  const base = 'absolute -translate-x-1/2 -translate-y-1/2'
  return (
    <>
      {groups.map((g, gi) => {
        const style = { left: `${g.cx}%`, top: `${g.cy}%`, zIndex: open === gi ? 30 : 10 }
        const h = {
          onMouseEnter: () => setOpen(gi), onMouseLeave: () => setOpen(null),
          onFocus: () => setOpen(gi), onBlur: () => setOpen(null),
        }
        if (g.arr.length === 1) {
          const it = g.arr[0]
          const to = href?.(it)
          const inner = (<>{dot(it)}{open === gi && <Tip>{label(it)}</Tip>}</>)
          return to ? (
            <Link key={kp + gi} to={to} className={`${base} cursor-pointer`} style={style} {...h}>{inner}</Link>
          ) : (
            <button key={kp + gi} type="button" className={`${base} cursor-help`} style={style} {...h}>{inner}</button>
          )
        }
        const labels = g.arr.map(label)
        return (
          <button key={kp + gi} type="button" className={`${base} cursor-help`} style={style} {...h}>
            <span className="flex h-5 w-5 items-center justify-center rounded-full border-2 border-ink text-[0.6rem] font-black text-night"
              style={{ backgroundColor: clusterBg }}>
              {g.arr.length}
            </span>
            {open === gi && <Tip>{labels.slice(0, 10).join(' · ')}{labels.length > 10 ? ` +${labels.length - 10}` : ''}</Tip>}
          </button>
        )
      })}
    </>
  )
}

function connDot(c: Connection) {
  const oneWay = c.direction !== 'both'
  return (
    <span
      className={`flex h-4 w-4 items-center justify-center rounded-[2px] border-2 border-ink text-[0.6rem] font-black text-night ${oneWay ? 'bg-gold' : 'bg-sky'}`}
      title={oneWay ? 'one-way' : 'two-way'}
    >
      {oneWay ? '→' : c.viaExit ? '↦' : '⇄'}
    </span>
  )
}
const connName = (c: Connection) => cleanMapName(c.internalName, c.mapName, c.displayName)

/** Some locations exist as several story-state variants of one place (e.g.
 * IRIS_HAMLET_NORMAL → "Iris Hamlet" before, IRIS_HAMLET_DESTROYED_POST →
 * "Borgo Iride" after), reached through the same exit and gated by a flag. Parse
 * the state from the internal name so the otherwise-identical entries read apart. */
function stateTag(internalName: string): string | null {
  const s = internalName.toUpperCase()
  if (/_DESTROYED_BOSS$/.test(s) || /_BOSS$/.test(s)) return 'boss event'
  if (/_DESTROYED_POST$/.test(s) || /_POST2?$/.test(s)) return 'post-event'
  if (/_DESTROYED$/.test(s)) return 'destroyed'
  if (/_NORMAL$/.test(s) || /_PRE$/.test(s)) return 'before event'
  if (/_PT2$/.test(s)) return 'part 2'
  return null
}
const connLabel = (c: Connection) => {
  if (c.stateCount > 1) return connName(c) // collapsed place: states shown separately
  const t = stateTag(c.internalName)
  return t ? `${connName(c)} (${t})` : connName(c)
}

/**
 * Connection pins with destination-aware clustering. Every crossing is projected
 * (doors → each exact entrance; seamless borders → one bearing pin). Pins within
 * CLUSTER_R% merge: a cluster that's all ONE destination collapses to a single
 * clickable pin ("→ Area 02", never "Area 02 ×3"); a cluster spanning several
 * destinations becomes a numbered marker whose popover lists each as a link.
 */
const CONN_CLUSTER_R = 3.5
function ConnectionPins({ connections, bounds, mapHref }: { connections: Connection[]; bounds: Bounds; mapHref: (guid: string) => string }) {
  const [open, setOpen] = useState<number | null>(null)
  const placed = connections
    .flatMap((c) => (c.crossings || []).map((pos) => ({ c, p: project(pos, bounds) })))
    .filter((x): x is { c: Connection; p: { left: string; top: string } } => !!x.p)

  const groups: { cx: number; cy: number; arr: Connection[] }[] = []
  for (const { c, p } of placed) {
    const L = parseFloat(p.left)
    const Y = parseFloat(p.top)
    const g = groups.find((g) => Math.hypot(g.cx - L, g.cy - Y) < CONN_CLUSTER_R)
    if (g) {
      g.arr.push(c)
      g.cx = (g.cx * (g.arr.length - 1) + L) / g.arr.length
      g.cy = (g.cy * (g.arr.length - 1) + Y) / g.arr.length
    } else groups.push({ cx: L, cy: Y, arr: [c] })
  }

  const base = 'absolute -translate-x-1/2 -translate-y-1/2'
  return (
    <>
      {groups.map((g, gi) => {
        const dests = Array.from(new Map(g.arr.map((c) => [c.guid, c])).values())
        const style = { left: `${g.cx}%`, top: `${g.cy}%`, zIndex: open === gi ? 30 : 10 }
        if (dests.length === 1) {
          // one destination (one or more co-located entrances) → single clickable pin
          const c = dests[0]
          const extra = g.arr.length > 1 ? ` · ${g.arr.length} entrances` : ''
          return (
            <Link
              key={'c' + gi}
              to={mapHref(c.guid)}
              className={`${base} cursor-pointer`}
              style={style}
              onMouseEnter={() => setOpen(gi)}
              onMouseLeave={() => setOpen(null)}
              onFocus={() => setOpen(gi)}
              onBlur={() => setOpen(null)}
            >
              {connDot(c)}
              {open === gi && (
                <Tip>
                  {connLabel(c)} · {c.viaExit ? 'door' : 'border'}
                  {c.direction !== 'both' ? ' (one-way)' : ''}
                  {extra}
                </Tip>
              )}
            </Link>
          )
        }
        // several destinations at one spot → a connection marker (door/border icon),
        // click to list them. Icon reflects the cluster: door ↦ (default), border ⇄,
        // or one-way → when every link is one-way.
        const allBorder = dests.every((c) => !c.viaExit)
        const allOneWay = dests.every((c) => c.direction !== 'both')
        const glyph = allOneWay ? '→' : allBorder ? '⇄' : '↦'
        return (
          <div key={'c' + gi} className={base} style={style}>
            <button
              type="button"
              className={`flex h-4 w-4 items-center justify-center rounded-[2px] border-2 border-ink text-[0.6rem] font-black text-night ${allOneWay ? 'bg-gold' : 'bg-sky'}`}
              onClick={() => setOpen(open === gi ? null : gi)}
              aria-label={`${dests.length} connections here`}
            >
              {glyph}
            </button>
            {open === gi && (
              <span className="dialog-box absolute bottom-full left-1/2 z-30 mb-2 flex w-max max-w-[220px] -translate-x-1/2 flex-col gap-1 p-2 text-[0.65rem] font-bold leading-tight">
                {dests.map((c) => (
                  <Link key={c.guid} to={mapHref(c.guid)} className="flex items-center gap-1.5 hover:text-lumen-deep">
                    <span aria-hidden>{c.direction !== 'both' ? '→' : c.viaExit ? '↦' : '⇄'}</span>
                    {connLabel(c)}
                  </Link>
                ))}
              </span>
            )}
          </div>
        )
      })}
    </>
  )
}

function Tip({ children }: { children: React.ReactNode }) {
  return (
    <span className="dialog-box absolute bottom-full left-1/2 z-30 mb-2 w-max max-w-[200px] -translate-x-1/2 px-2 py-1 text-[0.65rem] font-bold leading-tight">
      {children}
    </span>
  )
}

function LayerToggle({ on, onClick, dot, children }: { on: boolean; onClick: () => void; dot: string; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      aria-pressed={on}
      className={`inline-flex items-center gap-1.5 rounded-[2px] border-2 px-2 py-1 font-extrabold uppercase tracking-wide transition-opacity ${
        on ? 'border-cream/30 text-cream' : 'border-cream/10 text-cream/40'
      }`}
    >
      <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: dot, opacity: on ? 1 : 0.4 }} />
      {children}
    </button>
  )
}

function SpawnCard({ s }: { s: SpawnForm }) {
  return (
    <Link to={`/dex/${s.guid}`} className="pixel-screen flex flex-col items-center p-2 text-center transition-transform hover:-translate-y-0.5">
      <Sprite src={s.menuArt} alt={s.species} size={56} />
      <span className="mt-1 line-clamp-1 text-xs font-extrabold text-ink">{s.species}</span>
      {s.levelMin != null && (
        <span className="text-[0.65rem] font-bold text-ink-mute">
          Lv {s.levelMin}–{s.levelMax ?? s.levelMin}
        </span>
      )}
      <span className="mt-1">
        <TypeBadge type={s.ele} small />
      </span>
    </Link>
  )
}
