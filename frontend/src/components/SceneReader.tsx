import { Link } from 'react-router-dom'
import { useApi } from '../lib/api'
import { ErrorState, Skeleton } from './States'
import { Tag } from './Badge'
import { IconBack, IconScroll } from './Icons'
import { cleanSceneName, titleCase, rawStr, rawNum } from '../lib/game'

// --- scene endpoint types (inspected from the live API) ---
// `nodes` is a heterogeneous JSON array whose payloads keep the original
// snake_case keys (set_flag, item_guid, objective_id, level_min/max). We read
// every field defensively off Record<string, unknown>.
type RawNode = Record<string, unknown>

interface SceneFlag {
  flag: string
  mode?: string // 'check' | 'set'
}
interface SceneBattle {
  kind: string // 'trainer' | 'boss' | ...
  guid: string
  name?: string
}
interface SceneMap {
  guid?: string
  name?: string
  region?: string | null
  npc?: string | null
}
interface SceneRef {
  sceneId?: string
  name?: string
}
export interface Scene {
  sceneId: string
  region?: string | null
  track?: string | null
  name: string
  chapter?: number
  mainNum?: number
  nodes: RawNode[]
  edges: unknown[]
  entries: unknown[]
  flags: SceneFlag[]
  battles: SceneBattle[]
  maps: SceneMap[]
  prev?: SceneRef | null
  next?: SceneRef | null
}

/**
 * Renders one story scene (header, flow, flags, battles, maps, prev/next) given
 * its id. Used inline by the Story browser and standalone by /story/scene.
 * `sceneHref` decides where prev/next links point so each host keeps its URLs.
 */
export default function SceneReader({
  id,
  sceneHref,
}: {
  id: string
  sceneHref: (id: string) => string
}) {
  const { data, loading, error, reload } = useApi<Scene>(
    `/api/story/scene?id=${encodeURIComponent(id)}`,
  )

  if (error) return <ErrorState message={error} onRetry={reload} />
  if (loading || !data) return <Skeleton className="h-96 w-full" />

  return (
    <div className="flex flex-col gap-5">
      {/* Header */}
      <section className="dialog-box flex flex-wrap items-start gap-4 p-5 md:p-6">
        <span className="pixel-screen flex h-14 w-14 shrink-0 items-center justify-center text-2xl text-ink">
          <IconScroll />
        </span>
        <div className="min-w-0 flex-1">
          <h1 className="text-lg text-cream text-pixel-shadow md:text-xl">{cleanSceneName(data.name)}</h1>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            {data.region && <Tag>{titleCase(data.region)}</Tag>}
            {data.track && <Tag>{titleCase(data.track)}</Tag>}
            {data.chapter != null && <Tag>Chapter {data.chapter}</Tag>}
            {data.mainNum != null && <Tag>Main {data.mainNum}</Tag>}
          </div>
        </div>
      </section>

      {/* Scene reader */}
      <Panel title="Scene">
        {data.nodes.length === 0 ? (
          <p className="text-sm text-ink-mute">This scene has no recorded flow.</p>
        ) : (
          <ol className="flex flex-col gap-2">
            {data.nodes.map((n, i) => (
              <SceneNode key={(rawStr(n, 'id') ?? '') + i} node={n} battles={data.battles} />
            ))}
          </ol>
        )}
      </Panel>

      {/* Flags */}
      {data.flags.length > 0 && (
        <Panel title="Flags">
          <div className="flex flex-wrap gap-2">
            {data.flags.map((f, i) => (
              <span
                key={i}
                className="inline-flex items-center gap-1.5 rounded-[2px] border-2 border-ink/20 bg-parch px-2.5 py-1 text-xs font-bold text-ink"
              >
                {f.flag}
                {f.mode && (
                  <span
                    className="rounded-[2px] px-1 py-0.5 text-[0.5rem] font-extrabold uppercase tracking-wide text-night"
                    style={{ backgroundColor: f.mode === 'set' ? 'var(--color-gold)' : 'var(--color-sky)' }}
                  >
                    {f.mode}
                  </span>
                )}
              </span>
            ))}
          </div>
        </Panel>
      )}

      {/* Battles */}
      {data.battles.length > 0 && (
        <Panel title="Battles">
          <div className="flex flex-wrap gap-2">
            {data.battles.map((b, i) => {
              const path = b.kind === 'boss' ? `/bosses/${b.guid}` : b.kind === 'trainer' ? `/trainers/${b.guid}` : null
              const label = b.name || titleCase(b.kind)
              const inner = (
                <span className="inline-flex items-center gap-2 rounded-[2px] border-2 border-berry/40 bg-berry/10 px-3 py-1.5 text-sm font-bold text-ink">
                  <span className="text-[0.5rem] font-extrabold uppercase tracking-wide text-ink-mute">{b.kind}</span>
                  {label}
                </span>
              )
              return path ? (
                <Link key={i} to={path} className="hover:opacity-80">
                  {inner}
                </Link>
              ) : (
                <span key={i}>{inner}</span>
              )
            })}
          </div>
        </Panel>
      )}

      {/* Maps */}
      {data.maps.length > 0 && (
        <Panel title="Maps">
          <div className="flex flex-wrap gap-2">
            {data.maps.map((m, i) => {
              const label = m.name || m.npc || 'Map'
              const inner = (
                <span className="inline-flex items-center gap-2 rounded-[2px] border-2 border-gold/40 bg-gold/10 px-3 py-1.5 text-sm font-bold text-ink">
                  {label}
                  {m.region && <span className="text-xs text-ink-mute">{titleCase(m.region)}</span>}
                </span>
              )
              return m.guid ? (
                <Link key={i} to={`/maps/${m.guid}`} className="hover:opacity-80">
                  {inner}
                </Link>
              ) : (
                <span key={i}>{inner}</span>
              )
            })}
          </div>
        </Panel>
      )}

      {/* Prev / next */}
      {(data.prev || data.next) && (
        <nav className="flex flex-wrap items-center justify-between gap-3">
          {data.prev?.sceneId ? (
            <Link to={sceneHref(data.prev.sceneId)} className="pixel-btn inline-flex items-center gap-1">
              <IconBack /> {data.prev.name ? cleanSceneName(data.prev.name) : 'Previous'}
            </Link>
          ) : (
            <span />
          )}
          {data.next?.sceneId ? (
            <Link to={sceneHref(data.next.sceneId)} className="pixel-btn pixel-btn--primary inline-flex items-center gap-1">
              {data.next.name ? cleanSceneName(data.next.name) : 'Next'} →
            </Link>
          ) : (
            <span />
          )}
        </nav>
      )}
    </div>
  )
}

const NODE_LABEL: Record<string, string> = {
  branch: 'Branch',
  set_flag: 'Set Flag',
  quest: 'Quest',
  trainer: 'Trainer Battle',
  boss: 'Boss Battle',
  battle: 'Wild Battle',
  teleport: 'Teleport',
  shop: 'Shop',
  sell: 'Sell',
  item: 'Give Item',
  heal: 'Heal',
}

/** Render a single heterogeneous scene node. Dialogue gets a speech bubble;
 *  everything else gets a compact labelled event row. */
function SceneNode({ node, battles }: { node: RawNode; battles: SceneBattle[] }) {
  const kind = rawStr(node, 'kind') ?? 'unknown'

  if (kind === 'dialogue') {
    const speaker = rawStr(node, 'speaker')
    const rawLines = Array.isArray(node.lines) ? (node.lines as RawNode[]) : []
    const lines = rawLines.map((l) => rawStr(l, 't')).filter((t): t is string => !!t)
    const answers = Array.isArray(node.answers)
      ? (node.answers as unknown[]).filter((a): a is string => typeof a === 'string')
      : []
    return (
      <li className="rounded-[2px] bg-ink/5 px-3 py-2.5">
        {speaker && <p className="mb-1 text-[0.7rem] font-extrabold uppercase tracking-wide text-lumen-deep">{speaker}</p>}
        {lines.length > 0 ? (
          lines.map((t, i) => (
            <p key={i} className="text-sm leading-relaxed text-ink">
              {t}
            </p>
          ))
        ) : (
          <p className="text-sm italic text-ink-mute">(no text)</p>
        )}
        {answers.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1.5">
            {answers.map((a, i) => (
              <span
                key={i}
                className="inline-flex items-center rounded-[2px] border-2 border-ink/20 bg-parch px-2 py-1 text-xs font-bold text-ink-soft"
              >
                ▸ {a}
              </span>
            ))}
          </div>
        )}
      </li>
    )
  }

  // Event nodes — compact labelled rows.
  const label = NODE_LABEL[kind] ?? titleCase(kind)
  let detail: React.ReactNode = null

  if (kind === 'set_flag') {
    const flag = rawStr(node, 'flag')
    const value = rawNum(node, 'value')
    detail = (
      <span>
        {flag}
        {value != null && <span className="ml-1 text-ink-mute">= {value}</span>}
      </span>
    )
  } else if (kind === 'branch') {
    const conds = Array.isArray(node.conditions) ? (node.conditions as RawNode[]) : []
    detail =
      conds.length > 0 ? (
        <span>
          {conds
            .map((c) => {
              const flag = rawStr(c, 'flag')
              const check = rawNum(c, 'Check')
              return flag ? `${flag}${check != null ? ` = ${check}` : ''}` : null
            })
            .filter(Boolean)
            .join(', ') || `${conds.length} condition${conds.length === 1 ? '' : 's'}`}
        </span>
      ) : null
  } else if (kind === 'quest') {
    const obj = rawStr(node, 'objective_id')
    detail = obj ? <span>{obj}</span> : null
  } else if (kind === 'item') {
    const guid = rawStr(node, 'item_guid')
    const name = rawStr(node, 'item_name')
    const amount = rawNum(node, 'amount')
    const label = name ?? 'Item'
    detail = guid ? (
      <Link to={`/items/${guid}`} className="font-bold text-lumen-deep hover:underline">
        {label}
        {amount != null && amount !== 1 && <span className="ml-1 text-ink-mute">×{amount}</span>}
      </Link>
    ) : amount != null ? (
      <span>×{amount}</span>
    ) : null
  } else if (kind === 'trainer' || kind === 'boss' || kind === 'battle') {
    const guid = rawStr(node, 'guid')
    const named = guid ? battles.find((b) => b.guid === guid) : undefined
    const path = kind === 'boss' ? `/bosses/${guid}` : kind === 'trainer' ? `/trainers/${guid}` : null
    const name = named?.name
    if (name && path) {
      detail = (
        <Link to={path} className="font-bold text-lumen-deep hover:underline">
          {name}
        </Link>
      )
    } else if (name) {
      detail = <span>{name}</span>
    }
  }

  return (
    <li className="flex items-center gap-2 rounded-[2px] bg-ink/5 px-3 py-1.5 text-sm">
      <span className="shrink-0 rounded-[2px] bg-ink/15 px-1.5 py-0.5 text-[0.5rem] font-extrabold uppercase tracking-wide text-ink-soft">
        {label}
      </span>
      {detail && <span className="min-w-0 truncate font-bold text-ink">{detail}</span>}
    </li>
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
