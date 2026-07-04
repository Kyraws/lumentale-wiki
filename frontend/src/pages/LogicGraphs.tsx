import { useEffect, useMemo, useState } from 'react'
import type { BehaviorTreeSummary, TimelineSummary, MinigameSummary } from '../lib/types'
import { Tag } from '../components/Badge'
import { Skeleton, ErrorState, EmptyState } from '../components/States'
import { IconSearch, IconGraph } from '../components/Icons'
import { titleCase, fmtNum } from '../lib/game'
import {
  taskInfo,
  categoryRole,
  trackBucket,
  trackLabel,
  clipLabel,
  TRACK_BUCKET_LABEL,
  TRACK_BUCKET_GLOSS,
  minigameInfo,
  minigameFieldLabel,
  parsePrizeField,
  isPrizeFieldKey,
  type PrizeEntry,
  type TrackBucket,
} from './logicgraphs/labels'
import { useApi } from '../lib/api'
import { Link } from 'react-router-dom'
import './logicgraphs/logicgraphs.css'

// ============================================================================
// LOGIC GRAPHS — the game's *behavioural* data, made readable.
//
// Three families, each a different kind of "what the game does behind the
// scenes" graph, extracted directly from the Unity bundles:
//   • Behavior Trees — how wild creatures decide what to do in the overworld.
//   • Timelines      — the step-by-step choreography of a cutscene or battle move.
//   • Minigames      — the side-game definitions (Voltar arcade, Cocopa, …).
//
// The presentation decodes the raw engine identifiers (BehaviorDesigner task
// classes, Unity-Timeline track/clip classes) into plain English via
// ./logicgraphs/labels.ts, so a reader sees verbs and roles, not class names.
//
// CRITICAL int64 NOTE (kept from the original): every entity id (pathId /
// directorPathId) is a Unity signed int64 well outside Number.MAX_SAFE_INTEGER.
// The shared `useApi` helper runs `res.json()`, which silently truncates those
// ids, and the backend then 404s on the truncated value. So we fetch every
// list/detail body with raw fetch + a BigInt-safe regex pass (`extractIds`) that
// pulls the EXACT id strings out of the wire text, kept aligned by array index
// with the parsed (lossy) summary objects. Detail is fetched by that exact id.
// ============================================================================

type TabKey = 'trees' | 'timelines' | 'minigames'

const TABS: { key: TabKey; label: string; endpoint: string }[] = [
  { key: 'trees', label: 'Creature AI', endpoint: '/api/behavior-trees' },
  { key: 'timelines', label: 'Cutscenes & Move FX', endpoint: '/api/timelines' },
  { key: 'minigames', label: 'Minigames', endpoint: '/api/minigames' },
]

const INTRO: Record<TabKey, { title: string; body: React.ReactNode }> = {
  trees: {
    title: 'How wild creatures think',
    body: (
      <>
        A <strong>behavior tree</strong> is the decision-making script a creature follows in the
        overworld. Read top-to-bottom: <em>start</em> at the top, then the creature works through{' '}
        <span className="lg-role lg-role--flow">flow</span> steps that group its options,{' '}
        <span className="lg-role lg-role--check">check</span> steps that sense the world (Can it see
        you? What's the weather?), and <span className="lg-role lg-role--action">action</span> steps
        that make it do something (chase you, change speed, flee). Most are named after an{' '}
        <strong>emotion type</strong> (Mestus, Furor, Felicis…) — that emotion's creatures share the
        behaviour, with weather/health variants.
      </>
    ),
  },
  timelines: {
    title: 'Choreography of a moment',
    body: (
      <>
        A <strong>timeline</strong> is the second-by-second choreography of a single moment — a
        story cutscene, or (far more often) the camera, animation, effects and sound of one battle{' '}
        <em>move</em>. Each timeline is built from parallel <strong>tracks</strong> (Camera,
        Animation, Effects, Audio…), and each track holds <strong>clips</strong> that fire at set
        times. Below, tracks are grouped by what they affect so you can see a move's "recipe" at a
        glance.
      </>
    ),
  },
  minigames: {
    title: 'The side-games',
    body: (
      <>
        These are the optional <strong>minigames</strong> dotted around the world — the Voltar
        arcade cabinets, Costa Linda's Cocopa drop, Altipetra's sand run and Borgo Iride's prize
        game. Each card shows where it's played and its configured settings (scoring, layout, prize
        tiers) pulled straight from the placed game object.
      </>
    ),
  },
}

// ---- page-local detail types (inspected from the live API) ------------------

interface BTNode {
  id?: number
  name?: string
  label?: string
  type?: string
  category?: string
  params?: Record<string, unknown>
  [k: string]: unknown
}
interface BTEdge {
  from?: number
  to?: number
  kind?: string
}
interface BehaviorTreeDetail {
  pathId: number
  bundle?: string | null
  objectName?: string | null
  behaviorName?: string | null
  bdVersion?: string | null
  kind?: string | null
  taskCount?: number
  flags?: Record<string, unknown>
  nodes?: BTNode[]
  edges?: BTEdge[]
}

interface TimelineClip {
  asset_type?: string
  display_name?: string
  start?: number
  duration?: number
  [k: string]: unknown
}
interface TimelineTrack {
  track_type?: string
  clips?: TimelineClip[]
  children?: TimelineTrack[]
  muted?: boolean
  locked?: boolean
  [k: string]: unknown
}
interface TimelineDetail {
  directorPathId: number
  bundle?: string | null
  gameobject?: string | null
  timelineName?: string | null
  nTracks?: number
  nClips?: number
  nSceneBindings?: number
  crossbundle?: boolean
  tracks?: TimelineTrack[]
}

interface MinigameDetail {
  pathId: number
  className: string
  bundle?: string | null
  gameobjectName?: string | null
  fields?: Record<string, unknown>
  prizes?: unknown[]
}

// ---- BigInt-safe id extraction ---------------------------------------------
function extractIds(rawText: string, key: string): string[] {
  const re = new RegExp(`"${key}"\\s*:\\s*(-?\\d+)`, 'g')
  const out: string[] = []
  let m: RegExpExecArray | null
  while ((m = re.exec(rawText)) !== null) out.push(m[1])
  return out
}

type Row<T> = { id: string; item: T }

interface ListState<T> {
  rows: Row<T>[] | null
  loading: boolean
  error: string | null
}

function useIdSafeList<T>(endpoint: string, idField: string, active: boolean): ListState<T> & { reload: () => void } {
  const [state, setState] = useState<ListState<T>>({ rows: null, loading: false, error: null })
  const [nonce, setNonce] = useState(0)

  useEffect(() => {
    if (!active) return
    let alive = true
    setState((s) => ({ ...s, loading: !s.rows, error: null }))
    fetch(endpoint)
      .then(async (res) => {
        const text = await res.text()
        if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
        const items = JSON.parse(text) as T[]
        const ids = extractIds(text, idField)
        const rows: Row<T>[] = items.map((item, i) => ({ id: ids[i] ?? String(i), item }))
        if (alive) setState({ rows, loading: false, error: null })
      })
      .catch((e: Error) => {
        if (alive) setState({ rows: null, loading: false, error: e.message })
      })
    return () => {
      alive = false
    }
  }, [endpoint, idField, active, nonce])

  return { ...state, reload: () => setNonce((n) => n + 1) }
}

interface DetailState<T> {
  data: T | null
  loading: boolean
  error: string | null
}
function useIdSafeDetail<T>(endpoint: string | null): DetailState<T> & { reload: () => void } {
  const [state, setState] = useState<DetailState<T>>({ data: null, loading: false, error: null })
  const [nonce, setNonce] = useState(0)

  useEffect(() => {
    if (!endpoint) {
      setState({ data: null, loading: false, error: null })
      return
    }
    let alive = true
    setState({ data: null, loading: true, error: null })
    fetch(endpoint)
      .then(async (res) => {
        const text = await res.text()
        if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
        const data = JSON.parse(text) as T
        if (alive) setState({ data, loading: false, error: null })
      })
      .catch((e: Error) => {
        if (alive) setState({ data: null, loading: false, error: e.message })
      })
    return () => {
      alive = false
    }
  }, [endpoint, nonce])

  return { ...state, reload: () => setNonce((n) => n + 1) }
}

// ============================================================================

export default function LogicGraphs() {
  const [tab, setTab] = useState<TabKey>('trees')
  const [q, setQ] = useState('')
  const [selected, setSelected] = useState<Record<TabKey, string | null>>({
    trees: null,
    timelines: null,
    minigames: null,
  })

  const cfg = TABS.find((t) => t.key === tab)!

  const trees = useIdSafeList<BehaviorTreeSummary>('/api/behavior-trees', 'pathId', tab === 'trees')
  const timelines = useIdSafeList<TimelineSummary>('/api/timelines', 'directorPathId', tab === 'timelines')
  const minigames = useIdSafeList<MinigameSummary>('/api/minigames', 'pathId', tab === 'minigames')

  const list = tab === 'trees' ? trees : tab === 'timelines' ? timelines : minigames

  const selId = selected[tab]
  const detailPath = selId ? `${cfg.endpoint}/${selId}` : null
  const intro = INTRO[tab]

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="flex items-center gap-2 text-lg text-cream text-pixel-shadow">
          <IconGraph className="text-2xl" /> Logic Graphs
        </h1>
        <p className="mt-1 text-sm text-cream/60">
          A peek behind the curtain: the behaviour scripts, cutscene choreography and minigame rules
          extracted straight from the game.
        </p>
      </header>

      {/* Segmented tab switcher */}
      <div className="flex flex-wrap gap-2" role="tablist" aria-label="Logic graph category">
        {TABS.map((t) => (
          <button
            key={t.key}
            role="tab"
            aria-selected={tab === t.key}
            onClick={() => {
              setTab(t.key)
              setQ('')
            }}
            className={`pixel-btn ${tab === t.key ? 'pixel-btn--primary' : ''}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Plain-language intro for the active family */}
      <div className={`pixel-panel lg-intro lg-intro--${tab} p-4`}>
        <h2 className="font-extrabold text-ink">{intro.title}</h2>
        <p className="mt-1 text-sm leading-relaxed text-ink-soft">{intro.body}</p>
      </div>

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,24rem)_minmax(0,1fr)]">
        {/* LEFT: searchable, grouped list */}
        <div className="flex flex-col gap-3">
          <label className="relative block">
            <span className="sr-only">Search {cfg.label}</span>
            <IconSearch className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-lg text-ink-mute" />
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              type="search"
              placeholder={`Search ${cfg.label.toLowerCase()}…`}
              className="w-full rounded-[2px] border-ink bg-parch py-2.5 pl-10 pr-3 text-sm font-bold text-ink placeholder:text-ink-mute/70 focus:outline-none"
              style={{ borderWidth: 3 }}
            />
          </label>

          {list.error ? (
            <ErrorState message={list.error} onRetry={list.reload} />
          ) : list.loading || !list.rows ? (
            <Skeleton className="h-96 w-full" />
          ) : (
            <ListColumn
              tab={tab}
              q={q}
              rows={list.rows}
              selectedId={selId}
              onSelect={(id) => setSelected((s) => ({ ...s, [tab]: id }))}
            />
          )}
        </div>

        {/* RIGHT: detail panel */}
        <div>
          {!selId ? (
            <EmptyState
              title="Pick one to read it."
              hint={`Choose a ${tab === 'trees' ? 'creature behaviour' : tab === 'timelines' ? 'cutscene or move' : 'minigame'} from the list to see a plain-language breakdown.`}
            />
          ) : (
            <DetailPanel tab={tab} path={detailPath} />
          )}
        </div>
      </div>
    </div>
  )
}

// ============================================================================
// LIST COLUMN — grouped by purpose, with a collapsed "technical" bucket.
// ============================================================================

const PAGE = 60

type AnySummary = BehaviorTreeSummary | TimelineSummary | MinigameSummary

interface Group {
  key: string
  label: string
  hint?: string
  technical?: boolean
  rows: Row<AnySummary>[]
}

function ListColumn({
  tab,
  q,
  rows,
  selectedId,
  onSelect,
}: {
  tab: TabKey
  q: string
  rows: Row<AnySummary>[]
  selectedId: string | null
  onSelect: (id: string) => void
}) {
  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase()
    if (!needle) return rows
    return rows.filter(({ item }) => searchHaystack(tab, item).includes(needle))
  }, [rows, q, tab])

  const groups = useMemo(() => groupRows(tab, filtered), [tab, filtered])

  if (filtered.length === 0) return <EmptyState title="No matches." hint="Try a different search." />

  return (
    <div className="flex flex-col gap-4">
      {groups.map((g) => (
        <GroupBlock key={g.key} group={g} tab={tab} selectedId={selectedId} onSelect={onSelect} />
      ))}
    </div>
  )
}

function GroupBlock({
  group,
  tab,
  selectedId,
  onSelect,
}: {
  group: Group
  tab: TabKey
  selectedId: string | null
  onSelect: (id: string) => void
}) {
  // Technical groups start collapsed; others open. Reset on group identity.
  const [open, setOpen] = useState(!group.technical)
  const [limit, setLimit] = useState(PAGE)
  useEffect(() => setLimit(PAGE), [group.key])

  const visible = group.rows.slice(0, limit)

  return (
    <section className="flex flex-col gap-2">
      <button
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center gap-2 px-1 text-left"
        aria-expanded={open}
      >
        <span className="text-ink-mute">{open ? '▾' : '▸'}</span>
        <span className="text-[0.7rem] font-extrabold uppercase tracking-wide text-cream/80">
          {group.label}
        </span>
        <span className="text-[0.6rem] font-bold text-cream/40" style={{ fontFamily: 'var(--font-pixel)' }}>
          {group.rows.length}
        </span>
      </button>
      {open && group.hint && <p className="px-1 text-[0.7rem] leading-snug text-cream/45">{group.hint}</p>}
      {open && (
        <>
          <ul className="flex flex-col gap-2">
            {visible.map(({ id, item }) => (
              <li key={id}>
                <button
                  onClick={() => onSelect(id)}
                  aria-pressed={selectedId === id}
                  className={`pixel-panel w-full p-3 text-left transition-transform hover:-translate-y-0.5 ${
                    selectedId === id ? 'ring-[3px] ring-lumen' : ''
                  }`}
                >
                  <ListRow tab={tab} item={item} />
                </button>
              </li>
            ))}
          </ul>
          {group.rows.length > limit && (
            <button className="pixel-btn self-center" onClick={() => setLimit((n) => n + PAGE)}>
              Show more ({group.rows.length - limit})
            </button>
          )}
        </>
      )}
    </section>
  )
}

// --- grouping logic per tab -------------------------------------------------

function groupRows(tab: TabKey, rows: Row<AnySummary>[]): Group[] {
  if (tab === 'trees') return groupTrees(rows)
  if (tab === 'timelines') return groupTimelines(rows)
  return groupMinigames(rows)
}

function groupTrees(rows: Row<AnySummary>[]): Group[] {
  const real: Row<AnySummary>[] = []
  const empty: Row<AnySummary>[] = []
  const repel: Row<AnySummary>[] = []
  for (const r of rows) {
    const it = r.item as BehaviorTreeSummary
    const tasks = it.taskCount ?? 0
    const name = it.objectName || ''
    if (tasks === 0) empty.push(r)
    else if (/^Repel/i.test(name)) repel.push(r)
    else real.push(r)
  }
  const groups: Group[] = []
  if (real.length)
    groups.push({
      key: 'ai',
      label: 'Creature behaviours',
      hint: 'Named per emotion type, with weather / health / time-of-day variants.',
      rows: real,
    })
  if (repel.length)
    groups.push({
      key: 'repel',
      label: 'Repel item logic',
      hint: 'How creatures flee when you use a Repel / Repel+ / Repel EX.',
      rows: repel,
    })
  if (empty.length)
    groups.push({
      key: 'empty',
      label: 'Empty stubs (technical)',
      hint: 'Behavior components with no task graph — placeholder hooks on world prefabs.',
      technical: true,
      rows: empty,
    })
  return groups
}

function groupTimelines(rows: Row<AnySummary>[]): Group[] {
  // Heuristic: a timeline named after a move/VFX bundle (the vast majority) is a
  // battle-move effect; a handful are story cutscenes (named *_Cutscene / placed
  // on a map gameobject). We can only use what's in the summary, so we split on
  // the name carrying "Cutscene" or a clearly map-y gameobject.
  const cutscenes: Row<AnySummary>[] = []
  const moves: Row<AnySummary>[] = []
  for (const r of rows) {
    const it = r.item as TimelineSummary
    const n = `${it.timelineName ?? ''} ${it.gameobject ?? ''}`.toLowerCase()
    if (n.includes('cutscene') || n.includes('intro') || n.includes('outro') || n.includes('ending')) {
      cutscenes.push(r)
    } else moves.push(r)
  }
  const groups: Group[] = []
  if (cutscenes.length)
    groups.push({
      key: 'cutscenes',
      label: 'Story cutscenes',
      hint: 'Scripted story moments (camera, characters, fades).',
      rows: cutscenes,
    })
  if (moves.length)
    groups.push({
      key: 'moves',
      label: 'Move & battle effects',
      hint: 'The camera / animation / VFX / sound choreography of individual battle moves.',
      rows: moves,
    })
  return groups
}

function groupMinigames(rows: Row<AnySummary>[]): Group[] {
  const byPlace = new Map<string, Row<AnySummary>[]>()
  const internal: Row<AnySummary>[] = []
  for (const r of rows) {
    const it = r.item as MinigameSummary
    const info = minigameInfo(it.className)
    if (info.internal) {
      internal.push(r)
      continue
    }
    const list = byPlace.get(info.where) ?? []
    list.push(r)
    byPlace.set(info.where, list)
  }
  const groups: Group[] = []
  for (const [place, list] of [...byPlace.entries()].sort((a, b) => a[0].localeCompare(b[0]))) {
    groups.push({ key: `place-${place}`, label: place, rows: list })
  }
  if (internal.length)
    groups.push({
      key: 'internal',
      label: 'Loaders & HUD (technical)',
      hint: 'Plumbing that loads a minigame or draws its shared score/time overlay.',
      technical: true,
      rows: internal,
    })
  return groups
}

function searchHaystack(tab: TabKey, item: AnySummary): string {
  if (tab === 'trees') {
    const it = item as BehaviorTreeSummary
    return `${it.behaviorName ?? ''} ${it.objectName ?? ''} ${it.kind ?? ''} ${it.bundle ?? ''}`.toLowerCase()
  }
  if (tab === 'timelines') {
    const it = item as TimelineSummary
    return `${it.timelineName ?? ''} ${it.gameobject ?? ''} ${it.bundle ?? ''}`.toLowerCase()
  }
  const it = item as MinigameSummary
  const info = minigameInfo(it.className)
  return `${info.name} ${info.where} ${it.className ?? ''} ${it.gameobjectName ?? ''}`.toLowerCase()
}

function ListRow({ tab, item }: { tab: TabKey; item: AnySummary }) {
  if (tab === 'trees') {
    const it = item as BehaviorTreeSummary
    const name = it.objectName || it.behaviorName || 'Behavior'
    return (
      <div className="flex flex-col gap-1.5">
        <div className="flex items-center justify-between gap-2">
          <span className="truncate font-extrabold text-ink">{prettyTreeName(name)}</span>
          {(it.taskCount ?? 0) > 0 && (
            <span className="shrink-0 text-[0.6rem] font-bold text-ink-mute" style={{ fontFamily: 'var(--font-pixel)' }}>
              {fmtNum(it.taskCount)} steps
            </span>
          )}
        </div>
        {treeVariantNote(name) && <span className="text-xs text-ink-mute">{treeVariantNote(name)}</span>}
      </div>
    )
  }
  if (tab === 'timelines') {
    const it = item as TimelineSummary
    return (
      <div className="flex flex-col gap-1.5">
        <div className="flex items-center justify-between gap-2">
          <span className="truncate font-extrabold text-ink">{it.timelineName || it.gameobject || 'Timeline'}</span>
          {it.crossbundle && (
            <span
              className="shrink-0 type-chip"
              style={{ backgroundColor: 'var(--color-gold-deep)', fontSize: '0.42rem' }}
              title="References assets in another bundle (not fully walked)"
            >
              CROSS
            </span>
          )}
        </div>
        <div className="flex flex-wrap gap-1.5">
          <Tag>{fmtNum(it.nTracks)} tracks</Tag>
          <Tag>{fmtNum(it.nClips)} clips</Tag>
        </div>
      </div>
    )
  }
  const it = item as MinigameSummary
  const info = minigameInfo(it.className)
  return (
    <div className="flex flex-col gap-1">
      <span className="truncate font-extrabold text-ink">{info.name}</span>
      {it.gameobjectName && <span className="truncate text-xs text-ink-mute">{it.gameobjectName}</span>}
    </div>
  )
}

// Mestus_FasterIfRain → "Mestus"; the variant goes in a sub-note.
function prettyTreeName(raw: string): string {
  const base = raw.split('_')[0]
  return titleCase(base)
}
function treeVariantNote(raw: string): string | null {
  const idx = raw.indexOf('_')
  if (idx < 0) return null
  const rest = raw.slice(idx + 1)
  const map: Record<string, string> = {
    Default: 'default behaviour',
    Disappear: 'vanishes',
    FasterIfRain: 'faster in rain',
    FasterIfSnow: 'faster in snow',
    FasterIfLowHealth: 'faster when your party is hurt',
    FasterIfWithFoodEffect: 'faster near food lure',
    LowPartyHealth: 'when your party is low on HP',
    OnlyAtFullPartyHealth: 'only at full party HP',
    DayStillButChaseNight: 'still by day, chases at night',
    DefaultButMestusWithRain: 'default, but Mestus-like in rain',
  }
  return map[rest] || rest.replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase()
}

// ============================================================================
// DETAIL PANEL
// ============================================================================

function DetailPanel({ tab, path }: { tab: TabKey; path: string | null }) {
  const bt = useIdSafeDetail<BehaviorTreeDetail>(tab === 'trees' ? path : null)
  const tl = useIdSafeDetail<TimelineDetail>(tab === 'timelines' ? path : null)
  const mg = useIdSafeDetail<MinigameDetail>(tab === 'minigames' ? path : null)

  const active = tab === 'trees' ? bt : tab === 'timelines' ? tl : mg

  if (active.error) return <ErrorState message={active.error} onRetry={active.reload} />
  if (active.loading || !active.data) return <Skeleton className="h-96 w-full" />

  if (tab === 'trees') return <BehaviorTreeView d={bt.data as BehaviorTreeDetail} />
  if (tab === 'timelines') return <TimelineView d={tl.data as TimelineDetail} />
  return <MinigameView d={mg.data as MinigameDetail} />
}

function PanelHeader({ title, sub, lead }: { title: string; sub?: React.ReactNode; lead?: React.ReactNode }) {
  return (
    <div className="dialog-box mb-4 p-4">
      <h2 className="text-sm text-cream text-pixel-shadow">{title}</h2>
      {lead && <p className="mt-2 text-xs leading-relaxed text-cream/70">{lead}</p>}
      {sub && <div className="mt-2 flex flex-wrap items-center gap-1.5">{sub}</div>}
    </div>
  )
}

// ----------------------------------------------------------------------------
// BEHAVIOR TREE — rendered as a real indented outline built from `edges`.
// ----------------------------------------------------------------------------

function BehaviorTreeView({ d }: { d: BehaviorTreeDetail }) {
  const nodes = useMemo(() => (Array.isArray(d.nodes) ? d.nodes : []), [d.nodes])
  const edges = useMemo(() => (Array.isArray(d.edges) ? d.edges : []), [d.edges])

  // Build id → node and parent → children adjacency, then find the root.
  const { byId, childrenOf, roots } = useMemo(() => {
    const byId = new Map<number, BTNode>()
    nodes.forEach((n, i) => byId.set(n.id ?? i, n))
    const childrenOf = new Map<number, number[]>()
    const hasParent = new Set<number>()
    for (const e of edges) {
      if (e.from == null || e.to == null) continue
      const arr = childrenOf.get(e.from) ?? []
      arr.push(e.to)
      childrenOf.set(e.from, arr)
      hasParent.add(e.to)
    }
    // Roots = nodes with no incoming edge (usually the EntryTask). If the entry
    // node exists, prefer it.
    const roots: number[] = []
    for (const n of nodes) {
      const id = n.id ?? -1
      if (!hasParent.has(id)) roots.push(id)
    }
    return { byId, childrenOf, roots: roots.length ? roots : nodes.length ? [nodes[0].id ?? 0] : [] }
  }, [nodes, edges])

  const baseName = prettyTreeName(d.objectName || d.behaviorName || 'Behavior')
  const variant = treeVariantNote(d.objectName || '')

  return (
    <div>
      <PanelHeader
        title={`${baseName}${variant ? ` — ${variant}` : ''}`}
        lead={
          <>
            How this creature decides what to do, step by step. Indented steps run <em>inside</em>{' '}
            their parent.
          </>
        }
        sub={
          <>
            <Tag>{fmtNum(d.taskCount)} steps</Tag>
            <span className="lg-role lg-role--check">check</span>
            <span className="lg-role lg-role--action">action</span>
            <span className="lg-role lg-role--flow">flow</span>
          </>
        }
      />
      {nodes.length === 0 ? (
        <EmptyState title="No behaviour." hint="This is an empty placeholder component with no decision graph." />
      ) : (
        <div className="pixel-panel p-3">
          <ul className="flex flex-col gap-1.5">
            {roots.map((rid) => (
              <BTTreeNode
                key={rid}
                id={rid}
                byId={byId}
                childrenOf={childrenOf}
                depth={0}
                seen={new Set()}
              />
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function BTTreeNode({
  id,
  byId,
  childrenOf,
  depth,
  seen,
}: {
  id: number
  byId: Map<number, BTNode>
  childrenOf: Map<number, number[]>
  depth: number
  seen: Set<number>
}) {
  const node = byId.get(id)
  if (!node || seen.has(id)) return null
  const nextSeen = new Set(seen)
  nextSeen.add(id)

  const kids = childrenOf.get(id) ?? []
  const info = taskInfo(node.type || node.label || node.name)
  const role = categoryRole(typeof node.category === 'string' ? node.category : undefined)
  const params = node.params && typeof node.params === 'object' ? (node.params as Record<string, unknown>) : null
  const paramSummary = params ? summariseParams(params) : null

  return (
    <li>
      <div className="lg-tree-row" style={{ marginLeft: depth ? 14 : 0 }}>
        <div className={`flex flex-col gap-0.5 py-1 ${depth ? 'lg-tree-rail pl-2' : ''}`}>
          <div className="flex items-center gap-2">
            <span className={`lg-role lg-role--${role}`}>{role}</span>
            <span className="font-bold text-ink">{info.verb}</span>
            {kids.length > 0 && (
              <span className="text-[0.6rem] text-ink-mute" style={{ fontFamily: 'var(--font-pixel)' }}>
                {kids.length} sub-step{kids.length === 1 ? '' : 's'}
              </span>
            )}
          </div>
          {info.gloss && <span className="text-xs text-ink-mute">{info.gloss}</span>}
          {paramSummary && <span className="text-xs text-ink-soft">{paramSummary}</span>}
        </div>
      </div>
      {kids.length > 0 && (
        <ul className="flex flex-col gap-1.5">
          {kids.map((cid) => (
            <BTTreeNode
              key={cid}
              id={cid}
              byId={byId}
              childrenOf={childrenOf}
              depth={depth + 1}
              seen={nextSeen}
            />
          ))}
        </ul>
      )}
    </li>
  )
}

// Pull the few meaningful scalar params into a one-line note (e.g. hear range,
// repeat-forever). Skips empty shared-variable wrappers and GameObject handles.
function summariseParams(params: Record<string, unknown>): string | null {
  const bits: string[] = []
  for (const [k, v] of Object.entries(params)) {
    const val = unwrapShared(v)
    if (val == null || val === '' || val === false) continue
    const label = paramLabel(k)
    if (!label) continue
    if (typeof val === 'boolean') {
      if (val) bits.push(label)
    } else {
      bits.push(`${label}: ${formatScalar(val)}`)
    }
    if (bits.length >= 3) break
  }
  return bits.length ? bits.join(' · ') : null
}

function unwrapShared(v: unknown): unknown {
  if (v && typeof v === 'object' && 'value' in (v as Record<string, unknown>)) {
    return (v as Record<string, unknown>).value
  }
  if (v && typeof v === 'object') return null // bare shared-var wrapper with no value
  return v
}

function paramLabel(rawKey: string): string | null {
  // Keys come in like "SharedFloathearDistance", "SharedBoolrepeatForever".
  const stripped = rawKey.replace(/^Shared(Float|Int|Bool|Vector3|GameObject|String|Transform)?/, '')
  const map: Record<string, string> = {
    hearDistance: 'hearing range',
    repeatForever: 'repeats forever',
    endOnFailure: 'stops on failure',
    abortType: 'abort',
    AbortTypeabortType: 'abort',
  }
  if (map[stripped]) return map[stripped]
  if (map[rawKey]) return map[rawKey]
  // Skip noisy internal handles.
  if (/object|transform|position|target/i.test(stripped)) return null
  if (!stripped) return null
  return stripped.replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase()
}

function formatScalar(v: unknown): string {
  if (typeof v === 'number') return fmtNum(v)
  return String(v)
}

// ----------------------------------------------------------------------------
// TIMELINE — bucket summary + collapsible per-bucket track list.
// ----------------------------------------------------------------------------

function TimelineView({ d }: { d: TimelineDetail }) {
  const tracks = useMemo(() => (Array.isArray(d.tracks) ? d.tracks : []), [d.tracks])

  // Flatten nested track groups into a flat list (children rarely carry clips,
  // but we count everything) and bucket them.
  const flat = useMemo(() => {
    const out: TimelineTrack[] = []
    const walk = (t: TimelineTrack) => {
      out.push(t)
      for (const c of t.children ?? []) walk(c)
    }
    for (const t of tracks) walk(t)
    return out
  }, [tracks])

  const buckets = useMemo(() => {
    const m = new Map<TrackBucket, { trackCount: number; clipCount: number; tracks: TimelineTrack[] }>()
    for (const t of flat) {
      const b = trackBucket(t.track_type)
      const entry = m.get(b) ?? { trackCount: 0, clipCount: 0, tracks: [] }
      entry.trackCount += 1
      entry.clipCount += Array.isArray(t.clips) ? t.clips.length : 0
      entry.tracks.push(t)
      m.set(b, entry)
    }
    return m
  }, [flat])

  const maxClips = Math.max(1, ...[...buckets.values()].map((b) => b.clipCount))
  const order: TrackBucket[] = ['camera', 'animation', 'effects', 'audio', 'ui', 'control', 'other']
  const present = order.filter((b) => buckets.has(b))

  return (
    <div>
      <PanelHeader
        title={d.timelineName || d.gameobject || 'Timeline'}
        lead="What plays out, broken down by what each track controls. The bars show how busy each layer is."
        sub={
          <>
            <Tag>{fmtNum(d.nTracks)} tracks</Tag>
            <Tag>{fmtNum(d.nClips)} clips</Tag>
            {d.crossbundle && (
              <span className="type-chip" style={{ backgroundColor: 'var(--color-gold-deep)', fontSize: '0.42rem' }}>
                CROSS-BUNDLE
              </span>
            )}
          </>
        }
      />
      {flat.length === 0 ? (
        <EmptyState title="No tracks." />
      ) : (
        <div className="flex flex-col gap-3">
          {present.map((b) => {
            const entry = buckets.get(b)!
            return <TimelineBucket key={b} bucket={b} entry={entry} maxClips={maxClips} />
          })}
        </div>
      )}
    </div>
  )
}

function TimelineBucket({
  bucket,
  entry,
  maxClips,
}: {
  bucket: TrackBucket
  entry: { trackCount: number; clipCount: number; tracks: TimelineTrack[] }
  maxClips: number
}) {
  const [open, setOpen] = useState(false)
  const fill = `${Math.round((entry.clipCount / maxClips) * 100)}%`
  const tint =
    bucket === 'camera'
      ? 'var(--color-sky)'
      : bucket === 'audio'
        ? 'var(--color-el-aura)'
        : bucket === 'effects'
          ? 'var(--color-gold-deep)'
          : bucket === 'animation'
            ? 'var(--color-lumen-deep)'
            : 'var(--color-ink-mute)'

  return (
    <div className="pixel-panel p-3">
      <button onClick={() => setOpen((o) => !o)} className="flex w-full items-center gap-2 text-left" aria-expanded={open}>
        <span className="text-ink-mute">{open ? '▾' : '▸'}</span>
        <span className="font-extrabold text-ink">{TRACK_BUCKET_LABEL[bucket]}</span>
        <span className="text-[0.6rem] font-bold text-ink-mute" style={{ fontFamily: 'var(--font-pixel)' }}>
          {entry.trackCount} track{entry.trackCount === 1 ? '' : 's'} · {entry.clipCount} clip
          {entry.clipCount === 1 ? '' : 's'}
        </span>
      </button>
      <p className="mt-1 text-xs text-ink-mute">{TRACK_BUCKET_GLOSS[bucket]}</p>
      <div className="lg-bucket-bar mt-2">
        <div className="lg-bucket-fill" style={{ width: fill, backgroundColor: tint }} />
      </div>
      {open && (
        <ul className="mt-3 flex flex-col gap-2 border-l-2 border-ink/10 pl-3">
          {entry.tracks.map((t, i) => (
            <li key={i}>
              <div className="flex items-center gap-2">
                <span className="font-bold text-ink-soft">{trackLabel(t.track_type)}</span>
                {t.muted && <Tag>muted</Tag>}
              </div>
              {Array.isArray(t.clips) && t.clips.length > 0 && (
                <ClipStrip clips={t.clips} />
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function ClipStrip({ clips }: { clips: TimelineClip[] }) {
  // Group consecutive clips by label, sorted by start time, as a compact list.
  const ordered = [...clips].sort((a, b) => (a.start ?? 0) - (b.start ?? 0))
  const MAX = 10
  const shown = ordered.slice(0, MAX)
  return (
    <ol className="mt-1 flex flex-wrap items-center gap-1.5">
      {shown.map((c, i) => (
        <li
          key={i}
          className="rounded-[2px] bg-cream-2 px-1.5 py-0.5 text-[0.65rem] text-ink-soft"
          title={`${clipLabel(c.asset_type)} — starts ${fmtNum(c.start)}s, lasts ${fmtNum(c.duration)}s`}
        >
          {clipLabel(c.asset_type)}
        </li>
      ))}
      {ordered.length > shown.length && (
        <li className="text-[0.65rem] text-ink-mute">+{ordered.length - shown.length} more</li>
      )}
    </ol>
  )
}

// ----------------------------------------------------------------------------
// MINIGAME — readable card with location + decoded settings.
// ----------------------------------------------------------------------------

function MinigameView({ d }: { d: MinigameDetail }) {
  const info = minigameInfo(d.className)
  const fields = d.fields && typeof d.fields === 'object' ? d.fields : {}
  const entries = Object.entries(fields).filter(([, v]) => v != null)

  // Pull prize tiers out first — they carry real item GUIDs and are the most
  // fan-relevant info ("what can I win?"). The backend `prizes` array is empty
  // (prize tables live inside `fields`), so we parse them here.
  const prizeTiers = entries
    .filter(([k]) => isPrizeFieldKey(k))
    .map(([k, v]) => ({ key: k, prizes: parsePrizeField(v) }))
    .filter((t): t is { key: string; prizes: PrizeEntry[] } => t.prizes != null)

  // Surface the meaningful, human-friendly fields; bury the rest as "raw config".
  const nonPrize = entries.filter(([k]) => !isPrizeFieldKey(k))
  const readable = nonPrize.filter(([k]) => isReadableField(k))
  const rest = nonPrize.filter(([k]) => !isReadableField(k))

  return (
    <div>
      <PanelHeader
        title={info.name}
        lead={
          <>
            {info.blurb}{' '}
            {info.where !== 'Unknown' && info.where !== 'Shared' && (
              <span className="text-cream/90">Played at {info.where}.</span>
            )}
          </>
        }
        sub={
          <>
            {info.where !== 'Unknown' && <Tag>{info.where}</Tag>}
            {prizeTiers.length > 0 && <Tag>{fmtNum(prizeTiers.length)} prize tiers</Tag>}
            {d.gameobjectName && <span className="text-xs text-cream/50">{d.gameobjectName}</span>}
          </>
        }
      />

      {prizeTiers.length > 0 && (
        <div className="pixel-panel mb-3 p-3">
          <h3 className="mb-2 text-[0.7rem] font-extrabold uppercase tracking-wide text-ink-mute">Prizes</h3>
          <div className="flex flex-col gap-3">
            {prizeTiers.map((t) => (
              <div key={t.key}>
                <p className="mb-1 text-xs font-extrabold text-ink-soft">{minigameFieldLabel(t.key)}</p>
                <ul className="flex flex-col gap-1.5">
                  {t.prizes.map((p, i) => (
                    <PrizeRow key={i} prize={p} />
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </div>
      )}

      {readable.length > 0 && (
        <div className="pixel-panel p-3">
          <h3 className="mb-2 text-[0.7rem] font-extrabold uppercase tracking-wide text-ink-mute">Settings</h3>
          <dl className="flex flex-col divide-y-2 divide-ink/10">
            {readable.map(([k, v]) => (
              <div key={k} className="flex flex-col gap-1 py-2 sm:flex-row sm:gap-3">
                <dt className="shrink-0 font-extrabold text-ink sm:w-44 sm:truncate" title={k}>
                  {minigameFieldLabel(k)}
                </dt>
                <dd className="min-w-0 flex-1 text-ink-soft">
                  <JsonView value={v} />
                </dd>
              </div>
            ))}
          </dl>
        </div>
      )}

      {rest.length > 0 && <RawConfig entries={rest} />}

      {readable.length === 0 && rest.length === 0 && <EmptyState title="No configuration." />}
    </div>
  )
}

// One prize: amount × item, linking to the item page. Item GUIDs are normal
// strings (not int64), so the shared cached useApi is safe here.
function PrizeRow({ prize }: { prize: PrizeEntry }) {
  const { data } = useApi<{ guid: string; name?: string }>(`/api/items/${prize.guid}`)
  const name = data?.name || 'Unknown item'
  return (
    <li>
      <Link
        to={`/items/${prize.guid}`}
        className="flex items-center gap-2 rounded-[2px] bg-cream-2 px-2 py-1 text-sm hover:bg-parch"
      >
        <span className="font-bold text-lumen-deep" style={{ fontFamily: 'var(--font-pixel)' }}>
          ×{prize.amount}
        </span>
        <span className="font-extrabold text-ink">{name}</span>
      </Link>
    </li>
  )
}

// Whitelist of fields worth showing in plain language; everything else (object
// references, transforms, containers) drops into the collapsed raw block.
function isReadableField(key: string): boolean {
  const k = key.replace(/^m_/, '')
  if (/Prize/i.test(k)) return true
  return [
    'OST',
    'StartSFX',
    'ScoreIncrease',
    'XSpacing',
    'ZSpacing',
    'Rows',
    'Lines',
    'WaterAmount',
    'Name',
  ].includes(k)
}

function RawConfig({ entries }: { entries: [string, unknown][] }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="mt-3">
      <button onClick={() => setOpen((o) => !o)} className="flex items-center gap-2 px-1 text-left" aria-expanded={open}>
        <span className="text-cream/60">{open ? '▾' : '▸'}</span>
        <span className="text-[0.7rem] font-extrabold uppercase tracking-wide text-cream/70">
          Raw config ({entries.length})
        </span>
      </button>
      <p className="px-1 text-[0.7rem] text-cream/45">Object references and internal handles — for the curious.</p>
      {open && (
        <div className="pixel-panel mt-2 p-3">
          <dl className="flex flex-col divide-y-2 divide-ink/10">
            {entries.map(([k, v]) => (
              <div key={k} className="flex flex-col gap-1 py-2 sm:flex-row sm:gap-3">
                <dt className="shrink-0 font-extrabold text-ink sm:w-44 sm:truncate" title={k}>
                  {titleCase(k.replace(/^m_/, ''))}
                </dt>
                <dd className="min-w-0 flex-1 text-ink-soft">
                  <JsonView value={v} />
                </dd>
              </div>
            ))}
          </dl>
        </div>
      )}
    </div>
  )
}

// ---- JsonView: compact pretty-printer for heterogeneous payloads -----------

const INLINE_CHARS = 60
const MAX_ITEMS = 12
const MAX_DEPTH = 2

function JsonView({ value, depth = 0 }: { value: unknown; depth?: number }) {
  if (value === null || value === undefined) return <span className="text-ink-mute">—</span>
  const t = typeof value
  if (t === 'string') {
    const s = value as string
    return <span className="break-all font-bold text-ink">{s === '' ? '""' : s}</span>
  }
  if (t === 'number' || t === 'boolean') {
    return (
      <span className="font-bold text-lumen-deep" style={{ fontFamily: 'var(--font-pixel)' }}>
        {String(value)}
      </span>
    )
  }

  const compact = safeStringify(value)
  if (depth >= MAX_DEPTH || (compact != null && compact.length <= INLINE_CHARS)) {
    return <Screen text={compact ?? String(value)} />
  }

  if (Array.isArray(value)) {
    if (value.length === 0) return <span className="text-ink-mute">empty list</span>
    const shown = value.slice(0, MAX_ITEMS)
    return (
      <div className="flex flex-col gap-1">
        <span className="text-[0.6rem] uppercase tracking-wide text-ink-mute">{value.length} items</span>
        <ul className="flex flex-col gap-1 border-l-2 border-ink/15 pl-2">
          {shown.map((v, i) => (
            <li key={i} className="min-w-0">
              <JsonView value={v} depth={depth + 1} />
            </li>
          ))}
          {value.length > shown.length && (
            <li className="text-xs text-ink-mute">+{value.length - shown.length} more…</li>
          )}
        </ul>
      </div>
    )
  }

  const obj = value as Record<string, unknown>
  const entries = Object.entries(obj)
  if (entries.length === 0) return <span className="text-ink-mute">empty</span>
  const shown = entries.slice(0, MAX_ITEMS)
  return (
    <div className="flex flex-col gap-1 border-l-2 border-ink/15 pl-2">
      {shown.map(([k, v]) => (
        <div key={k} className="flex min-w-0 flex-col gap-0.5 sm:flex-row sm:gap-2">
          <span className="shrink-0 text-xs font-extrabold text-ink-mute">{k}</span>
          <span className="min-w-0 flex-1">
            <JsonView value={v} depth={depth + 1} />
          </span>
        </div>
      ))}
      {entries.length > shown.length && (
        <span className="text-xs text-ink-mute">+{entries.length - shown.length} more keys…</span>
      )}
    </div>
  )
}

function Screen({ text }: { text: string }) {
  const clipped = text.length > 400 ? `${text.slice(0, 400)}…` : text
  return (
    <pre
      className="pixel-screen overflow-x-auto whitespace-pre-wrap break-all p-2 text-sm text-ink-soft"
      style={{ fontFamily: 'var(--font-pixel)' }}
    >
      {clipped}
    </pre>
  )
}

function safeStringify(v: unknown): string | null {
  try {
    return JSON.stringify(v)
  } catch {
    return null
  }
}
