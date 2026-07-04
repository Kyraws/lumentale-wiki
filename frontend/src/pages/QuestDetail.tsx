import { useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useApi } from '../lib/api'
import { ErrorState, Skeleton } from '../components/States'
import { Tag } from '../components/Badge'
import { IconBack, IconQuest } from '../components/Icons'
import { fmtNum, titleCase, cleanSceneName } from '../lib/game'
import { IconMap, IconScroll } from '../components/Icons'

// --- page-local types (graph endpoint, inspected from the live API) ---
// NOTE: `pathid` / transition `to` are 64-bit ints that lose precision once
// JSON.parse turns them into JS numbers. We only use them as opaque keys and
// for best-effort transition resolution — never for arithmetic.
interface QGItem {
  kind: string
  guid?: string
  amount?: number
  name?: string
}
interface QGRewards {
  money?: number
  exp?: number
  items: QGItem[]
}
interface QGTransition {
  to: number
  port?: string
}
interface QGCondition {
  rid?: number
}
interface QGNode {
  pathid: number
  kind: string // 'state' | 'branch'
  stateId?: string
  stateName?: string
  objective?: string
  conditions?: QGCondition[]
  transitions: QGTransition[]
}
// --- start / completion recovery (from the quest state-machine, see QuestService) ---
interface QGMapLink {
  guid: string
  name?: string
  region?: string
}
interface QGStep {
  pathid: number
  stateId?: string
  objective?: string
  map?: QGMapLink
}
interface QGSceneLink {
  sceneId: string
  name?: string
  region?: string
  flag?: string
  mode?: string
}
interface QGEndpoint {
  steps: QGStep[]
  flag?: string
  scenes: QGSceneLink[]
}
interface QGFlow {
  start?: QGEndpoint
  end?: QGEndpoint
  linkedScenes: QGSceneLink[]
}
interface QuestGraph {
  guid: string
  internalName: string
  name: string
  description?: string
  giver?: string
  type?: number
  rewards: QGRewards
  flow?: QGFlow
  nodes: QGNode[]
}

const QUEST_TYPE: Record<number, string> = { 0: 'Main story', 1: 'Side quest', 2: 'Task' }

export default function QuestDetail() {
  const { guid = '' } = useParams()
  const { data, loading, error, reload } = useApi<QuestGraph>(`/api/quests/${guid}/graph?lang=en`)

  // Best-effort index from pathid -> node, for resolving transition targets to a
  // readable label. Keyed by String(pathid) to dodge number formatting drift.
  const byId = useMemo(() => {
    const m = new Map<string, QGNode>()
    if (data) for (const n of data.nodes) m.set(String(n.pathid), n)
    return m
  }, [data])

  if (error) return <ErrorState message={error} onRetry={reload} />
  if (loading || !data) return <Skeleton className="h-72 w-full" />

  const typeLabel = data.type != null ? QUEST_TYPE[data.type] : undefined
  const r = data.rewards
  const hasRewards = !!(r && (r.money || r.exp || (r.items && r.items.length > 0)))
  const stateNodes = data.nodes.filter((n) => n.kind === 'state')
  const branchNodes = data.nodes.filter((n) => n.kind === 'branch')

  const labelFor = (id: number) => {
    const n = byId.get(String(id))
    if (!n) return `#${String(id)}`
    return n.objective || n.stateName || n.stateId || `#${String(id)}`
  }

  return (
    <div className="flex flex-col gap-5">
      <Link to="/quests" className="inline-flex w-fit items-center gap-1 text-sm font-bold text-cream/70 hover:text-cream">
        <IconBack /> Back to Quests
      </Link>

      {/* Header */}
      <section className="dialog-box flex flex-wrap items-start gap-4 p-5 md:p-7">
        <span className="pixel-screen flex h-14 w-14 shrink-0 items-center justify-center text-2xl text-ink">
          <IconQuest />
        </span>
        <div className="min-w-0 flex-1">
          <h1 className="text-xl text-cream text-pixel-shadow md:text-2xl">{data.name}</h1>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            {typeLabel && <Tag>{typeLabel}</Tag>}
            {data.giver ? <Tag>from {data.giver}</Tag> : null}
            {data.internalName && <Tag>{data.internalName}</Tag>}
          </div>
          {data.description && <p className="mt-3 max-w-prose text-sm text-cream/75">{data.description}</p>}
        </div>
      </section>

      {/* Where the quest starts & completes (recovered from the quest flags) */}
      {data.flow && (data.flow.start || data.flow.end) && (
        <div className="grid gap-4 md:grid-cols-2">
          {data.flow.start && (
            <Endpoint kind="start" title="Starts" ep={data.flow.start} giver={data.giver} />
          )}
          {data.flow.end && <Endpoint kind="end" title="Completes" ep={data.flow.end} />}
        </div>
      )}

      {/* Rewards */}
      {hasRewards && (
        <Panel title="Rewards">
          <div className="flex flex-wrap items-center gap-2">
            {r.money ? (
              <span className="inline-flex items-center gap-1 rounded-[2px] border-2 border-gold/40 bg-gold/10 px-3 py-1.5 text-sm font-extrabold text-ink">
                {fmtNum(r.money)}₲
              </span>
            ) : null}
            {r.exp ? (
              <span className="inline-flex items-center gap-1 rounded-[2px] border-2 border-lumen/40 bg-lumen/10 px-3 py-1.5 text-sm font-extrabold text-ink">
                {fmtNum(r.exp)} EXP
              </span>
            ) : null}
            {r.items.map((it, i) => {
              const inner = (
                <span className="inline-flex items-center gap-2 rounded-[2px] border-2 border-ink/20 bg-parch px-3 py-1.5 text-sm font-bold text-ink">
                  {it.name || titleCase(it.kind)}
                  {it.amount != null && it.amount > 1 && <span className="text-lumen-deep">×{it.amount}</span>}
                </span>
              )
              return it.kind === 'item' && it.guid ? (
                <Link key={i} to={`/items/${it.guid}`} className="hover:opacity-80">
                  {inner}
                </Link>
              ) : (
                <span key={i}>{inner}</span>
              )
            })}
          </div>
        </Panel>
      )}

      {/* Quest flow */}
      <Panel title="Quest Flow">
        {stateNodes.length === 0 ? (
          <p className="text-sm text-ink-mute">No steps recorded for this quest.</p>
        ) : (
          <ol className="flex flex-col gap-2">
            {stateNodes.map((n, i) => (
              <li key={String(n.pathid)} className="rounded-[2px] bg-ink/5 px-3 py-2.5">
                <div className="flex items-start gap-3">
                  <span
                    className="flex h-6 w-6 shrink-0 items-center justify-center rounded-[2px] bg-ink text-[0.65rem] text-cream"
                    style={{ fontFamily: 'var(--font-pixel)' }}
                  >
                    {i + 1}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-bold text-ink">{n.objective || titleCase(n.stateName) || 'Step'}</p>
                    {(n.stateName || n.stateId) && (
                      <p className="text-[0.6rem] uppercase tracking-wide text-ink-mute">{n.stateName || n.stateId}</p>
                    )}
                    {n.transitions.length > 0 && (
                      <div className="mt-1.5 flex flex-wrap gap-1.5">
                        {n.transitions.map((t, ti) => (
                          <span
                            key={ti}
                            className="inline-flex items-center gap-1 rounded-[2px] bg-ink/8 px-2 py-0.5 text-[0.6rem] font-bold text-ink-soft"
                          >
                            {t.port && t.port !== 'Next' ? `${t.port}: ` : '→ '}
                            <span className="text-ink">{labelFor(t.to)}</span>
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              </li>
            ))}
          </ol>
        )}
      </Panel>

      {/* Branch nodes (conditional gates) */}
      {branchNodes.length > 0 && (
        <Panel title="Branches">
          <ul className="flex flex-col gap-2">
            {branchNodes.map((n) => (
              <li key={String(n.pathid)} className="rounded-[2px] bg-ink/5 px-3 py-2 text-sm">
                <span className="font-bold text-ink">Conditional gate</span>
                {n.conditions && n.conditions.length > 0 && (
                  <span className="ml-2 text-[0.65rem] text-ink-mute">
                    {n.conditions.length} condition{n.conditions.length === 1 ? '' : 's'}
                  </span>
                )}
                {n.transitions.length > 0 && (
                  <div className="mt-1.5 flex flex-wrap gap-1.5">
                    {n.transitions.map((t, ti) => (
                      <span key={ti} className="inline-flex items-center gap-1 rounded-[2px] bg-ink/8 px-2 py-0.5 text-[0.6rem] font-bold text-ink-soft">
                        {t.port ? `${t.port}: ` : '→ '}
                        <span className="text-ink">{labelFor(t.to)}</span>
                      </span>
                    ))}
                  </div>
                )}
              </li>
            ))}
          </ul>
        </Panel>
      )}

      {/* Every story scene that touches one of this quest's flags */}
      {data.flow && data.flow.linkedScenes.length > 0 && (
        <Panel title="Story Scenes (flags)">
          <p className="mb-3 text-xs text-ink-mute">
            Scenes that set or check this quest's flags — how the game drives it forward.
          </p>
          <ul className="flex flex-col gap-1.5">
            {data.flow.linkedScenes.map((s, i) => (
              <li key={i}>
                <SceneRow s={s} />
              </li>
            ))}
          </ul>
        </Panel>
      )}
    </div>
  )
}

/** A "Starts" / "Completes" card: the opening/closing step(s), the gating flag,
 *  and the story scene(s) that set it. */
function Endpoint({
  kind,
  title,
  ep,
  giver,
}: {
  kind: 'start' | 'end'
  title: string
  ep: QGEndpoint
  giver?: string
}) {
  const tint = kind === 'start' ? 'var(--color-gold)' : 'var(--color-sky)'
  const hasContent = ep.steps.length > 0 || ep.flag || ep.scenes.length > 0 || (kind === 'start' && giver)
  return (
    <section className="pixel-panel p-4 md:p-5" style={{ borderTop: `4px solid ${tint}` }}>
      <h2
        className="mb-3 inline-block rounded-[2px] px-2 py-1 text-[0.7rem] text-night"
        style={{ fontFamily: 'var(--font-display)', backgroundColor: tint }}
      >
        {title}
      </h2>
      {!hasContent ? (
        <p className="text-sm text-ink-mute">Not recovered from the quest data.</p>
      ) : (
        <div className="flex flex-col gap-3">
          {kind === 'start' && giver && (
            <Row label="Quest giver">
              <span className="font-bold text-ink">{giver}</span>
            </Row>
          )}
          {ep.steps.length > 0 && (
            <Row label={kind === 'start' ? 'First step' : 'Final step'}>
              <div className="flex flex-col gap-1.5">
                {ep.steps.map((st, i) => (
                  <div key={i} className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-bold text-ink">
                      {st.objective || titleCase(st.stateId) || `#${String(st.pathid)}`}
                    </span>
                    {st.map?.guid && (
                      <Link
                        to={`/maps/${st.map.guid}`}
                        className="inline-flex items-center gap-1 rounded-[2px] border-2 border-gold/40 bg-gold/10 px-2 py-0.5 text-xs font-bold text-ink hover:opacity-80"
                      >
                        <IconMap /> {st.map.name || 'Map'}
                      </Link>
                    )}
                  </div>
                ))}
              </div>
            </Row>
          )}
          {ep.flag && (
            <Row label={kind === 'start' ? 'Trigger flag' : 'Completion flag'}>
              <code className="rounded-[2px] bg-ink/10 px-1.5 py-0.5 text-xs font-bold text-ink">{ep.flag}</code>
            </Row>
          )}
          {ep.scenes.length > 0 && (
            <Row label={kind === 'start' ? 'Set in scene' : 'Set in scene'}>
              <div className="flex flex-col gap-1.5">
                {ep.scenes.map((s, i) => (
                  <SceneRow key={i} s={s} hideFlag />
                ))}
              </div>
            </Row>
          )}
        </div>
      )}
    </section>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="text-[0.55rem] font-extrabold uppercase tracking-wide text-ink-mute">{label}</span>
      {children}
    </div>
  )
}

/** A linked story-scene row with its city, the flag it touches, and set/check mode. */
function SceneRow({ s, hideFlag }: { s: QGSceneLink; hideFlag?: boolean }) {
  return (
    <Link
      to={`/story/scene?id=${encodeURIComponent(s.sceneId)}`}
      className="inline-flex flex-wrap items-center gap-2 rounded-[2px] bg-ink/5 px-2.5 py-1.5 text-sm hover:bg-ink/10"
    >
      <IconScroll />
      <span className="font-bold text-ink">{s.name ? cleanSceneName(s.name) : s.sceneId}</span>
      {s.region && <span className="text-xs text-ink-mute">{titleCase(s.region)}</span>}
      {!hideFlag && s.flag && (
        <code className="rounded-[2px] bg-ink/10 px-1 py-0.5 text-[0.6rem] font-bold text-ink-soft">{s.flag}</code>
      )}
      {s.mode && (
        <span
          className="rounded-[2px] px-1 py-0.5 text-[0.5rem] font-extrabold uppercase tracking-wide text-night"
          style={{ backgroundColor: s.mode === 'set' ? 'var(--color-gold)' : 'var(--color-sky)' }}
        >
          {s.mode}
        </span>
      )}
    </Link>
  )
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="pixel-panel p-4 md:p-5">
      <h2 className="mb-4 inline-block rounded-[2px] bg-ink px-2 py-1 text-[0.7rem] text-cream" style={{ fontFamily: 'var(--font-display)' }}>
        {title}
      </h2>
      {children}
    </section>
  )
}
