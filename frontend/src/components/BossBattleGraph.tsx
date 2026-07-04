import { Link } from 'react-router-dom'
import { TypeBadge } from './Badge'
import { IconSpark, IconGraph } from './Icons'
import './boss-graph.css'

/* =========================================================================
 * Boss battle-graph reader.
 *
 * The raw payload is a flat node list + edge list (see /api/bosses/{id}/graph).
 * Semantics (from logic-graphs/LOGIC.md "W2"):
 *   - battleNodeType EntryPoint  = a TRIGGER (On Battle Start / On Turn Passed /
 *     On AI Action Planner Generation). Each entry point begins one rule chain.
 *   - battleNodeType Advancement = a CONDITION gate (Battle Branch Action,
 *     Battle Branch AND Action, Battle Progress On HP). Edges out: `next`
 *     (sequential) or `true` (taken when the condition passes).
 *   - battleNodeType Action      = an EFFECT (Set Rotation, Trigger Strong Skill,
 *     Change Phase, Set Stat Override, Defeat Switch, Music, End Battle, …).
 *
 * We walk each entry point's chain via the edges and present it as a human rule:
 *   WHEN <trigger>  ·  IF <conditions>  ·  DO <effects>.
 * `ai_skill_rotation` indices are resolved to the boss's skill names by `ord`.
 * ===================================================================== */

export interface GraphNode {
  path_id?: number
  node?: string
  battleNodeType?: string
  next?: number
  true?: number
  conditions?: number
  ai_skill_rotation?: number[]
  skill?: string
  skill_guid?: string
  target_formula?: string
  HP?: number
  Operator?: string
  Percentage?: number
  Target?: number
  Side?: number
  [key: string]: unknown
}

export interface GraphEdge {
  from?: number
  to?: number
  kind?: string
}

export interface StrongSkill {
  moveGuid: string
  moveName: string
  targetForm?: string
  targetFormula?: string
}

export interface BossGraphData {
  bossGuid: string
  graphName?: string
  nodeCount?: number
  nodes: GraphNode[]
  edges: GraphEdge[]
  strongSkills: StrongSkill[]
  note?: string
}

/** A boss skill, ord-indexed, used to resolve rotation indices to names. */
export interface SkillRef {
  moveGuid: string
  moveName: string
  type?: string
  ord?: number
}

/* ---------- trigger labels ---------- */

const TRIGGER_LABEL: Record<string, string> = {
  'On Battle Start': 'At battle start',
  'On Battle Turn Passed': 'Each turn',
  'On AI Action Planner Generation': 'When choosing a move',
}

/* ---------- a "rule" = one entry-point chain, flattened ---------- */

interface Rule {
  trigger: string
  triggerRaw: string
  conditions: string[]
  effects: Effect[]
}

interface Effect {
  kind:
    | 'rotation'
    | 'strongSkill'
    | 'changePhase'
    | 'statOverride'
    | 'resetDebuffs'
    | 'defeatSwitch'
    | 'changeForm'
    | 'music'
    | 'endBattle'
    | 'other'
  label: string
  detail?: string
  rotationSkills?: { name: string; type?: string; guid?: string }[]
  strong?: { name: string; guid?: string; target?: string }
}

function humanFormula(f?: string): string | undefined {
  if (!f) return undefined
  // e.g. "species:Iotamor;side:1" -> "Iotamor (enemy side)"
  const parts: Record<string, string> = {}
  f.split(';').forEach((p) => {
    const [k, v] = p.split(':')
    if (k && v) parts[k.trim()] = v.trim()
  })
  const bits: string[] = []
  if (parts.species) bits.push(parts.species)
  if (parts.side != null) bits.push(parts.side === '1' ? 'enemy side' : 'player side')
  return bits.length ? bits.join(' · ') : f
}

function rotationNames(idx: number[], skills: SkillRef[]) {
  const byOrd = new Map<number, SkillRef>()
  skills.forEach((s) => byOrd.set(s.ord ?? -1, s))
  return idx.map((i) => {
    const s = byOrd.get(i)
    return s
      ? { name: s.moveName, type: s.type, guid: s.moveGuid }
      : { name: `Skill #${i}`, type: undefined, guid: undefined }
  })
}

function conditionText(n: GraphNode): string {
  if (n.node === 'Battle Progress On HP') {
    const op = n.Operator ?? '<='
    const isPct = n.Percentage === 1
    const hp = n.HP
    if (hp != null) return `Boss HP ${op} ${hp}${isPct ? '%' : ''}`
    return `Boss HP threshold reached (${op})`
  }
  if (n.node === 'Battle Branch AND Action') {
    return `All of ${n.conditions ?? '?'} condition(s) met`
  }
  if (n.node === 'Battle Branch Action') {
    return n.conditions && n.conditions > 1 ? `${n.conditions} condition(s) met` : 'Condition met'
  }
  return n.node ?? 'Condition'
}

function effectOf(n: GraphNode, skills: SkillRef[]): Effect {
  switch (n.node) {
    case 'Battle Set Rotation':
      return {
        kind: 'rotation',
        label: 'Use this skill rotation',
        rotationSkills: rotationNames(n.ai_skill_rotation ?? [], skills),
      }
    case 'Battle Trigger Strong Skill Incoming':
      return {
        kind: 'strongSkill',
        label: 'Telegraph a strong skill',
        strong: {
          name: n.skill ?? 'Strong skill',
          guid: n.skill_guid,
          target: humanFormula(n.target_formula),
        },
      }
    case 'Battle Boss Change Phase':
      return { kind: 'changePhase', label: 'Advance to the next phase' }
    case 'Battle Set Stat Override':
      return { kind: 'statOverride', label: 'Override the boss stats' }
    case 'Battle Reset Stat Debuffs':
      return {
        kind: 'resetDebuffs',
        label: 'Clear stat debuffs',
        detail: n.Side === 1 ? 'on the enemy side' : undefined,
      }
    case 'Battle Boss Defeat Switch':
      return { kind: 'defeatSwitch', label: 'On defeat, switch in the next form' }
    case 'Battle Animon Change Form':
      return { kind: 'changeForm', label: 'Change form' }
    case 'Battle Wwise Music Event':
      return { kind: 'music', label: 'Change battle music' }
    case 'End Battle With Result Action':
      return { kind: 'endBattle', label: 'End the battle' }
    default:
      return { kind: 'other', label: n.node ?? 'Effect' }
  }
}

/** Build readable rules by walking each EntryPoint chain through the edges. */
function buildRules(nodes: GraphNode[], edges: GraphEdge[], skills: SkillRef[]): Rule[] {
  const byId = new Map<number, GraphNode>()
  nodes.forEach((n) => {
    if (n.path_id != null) byId.set(n.path_id, n)
  })
  // adjacency: from -> [to...] (any edge kind advances the chain)
  const adj = new Map<number, number[]>()
  edges.forEach((e) => {
    if (e.from == null || e.to == null) return
    if (!adj.has(e.from)) adj.set(e.from, [])
    adj.get(e.from)!.push(e.to)
  })

  const entries = nodes.filter((n) => n.battleNodeType === 'EntryPoint')
  const rules: Rule[] = []

  for (const entry of entries) {
    const conditions: string[] = []
    const effects: Effect[] = []
    const seen = new Set<number>()
    const queue: number[] = entry.path_id != null ? [entry.path_id] : []

    while (queue.length) {
      const id = queue.shift()!
      if (seen.has(id)) continue
      seen.add(id)
      const node = byId.get(id)
      if (!node) continue
      if (node.battleNodeType === 'Advancement') {
        const c = conditionText(node)
        if (c && !conditions.includes(c)) conditions.push(c)
      } else if (node.battleNodeType === 'Action') {
        effects.push(effectOf(node, skills))
      }
      const outs = adj.get(id) ?? []
      outs.forEach((to) => queue.push(to))
    }

    // Drop bare triggers that lead nowhere meaningful.
    if (effects.length === 0 && conditions.length === 0) continue

    rules.push({
      trigger: TRIGGER_LABEL[entry.node ?? ''] ?? entry.node ?? 'Trigger',
      triggerRaw: entry.node ?? '',
      conditions,
      effects,
    })
  }
  return rules
}

/* =========================================================================
 * Render
 * ===================================================================== */

export default function BossBattleGraph({
  graph,
  skills,
}: {
  graph: BossGraphData
  skills: SkillRef[]
}) {
  const hasNodes = graph.nodes.length > 0
  const rules = hasNodes ? buildRules(graph.nodes, graph.edges, skills) : []

  return (
    <div className="bgraph flex flex-col gap-5">
      {graph.graphName && (
        <p className="text-xs text-ink-mute">
          <IconGraph className="mr-1 text-sm text-lumen-deep" />
          <span className="font-bold text-ink-soft">{graph.graphName}</span>
          {graph.nodeCount != null && <span> · {graph.nodeCount} nodes</span>}
        </p>
      )}

      {/* Telegraphed strong skills — the moves the boss can power-charge */}
      {graph.strongSkills.length > 0 && (
        <section>
          <h3 className="bgraph-h">Telegraphed Strong Skills</h3>
          <p className="bgraph-sub">
            Powerful moves the boss charges and announces before unleashing.
          </p>
          <div className="mt-2 flex flex-wrap gap-2">
            {graph.strongSkills.map((s) => (
              <Link
                key={s.moveGuid}
                to={`/moves/${s.moveGuid}`}
                className="bgraph-strong"
                title="View move"
              >
                <IconSpark className="text-sm text-gold" />
                <span className="font-bold">{s.moveName}</span>
                {(s.targetForm || s.targetFormula) && (
                  <span className="bgraph-strong-target">
                    {humanFormula(s.targetFormula) ?? s.targetForm}
                  </span>
                )}
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* Note-only graph */}
      {graph.note && !hasNodes && (
        <div className="dialog-box p-4 text-sm text-ink-soft">
          <span className="font-extrabold text-ink">No scripted graph: </span>
          {graph.note} — this boss uses the default battle AI.
        </div>
      )}

      {/* Readable rule chains */}
      {rules.length > 0 && (
        <section>
          <h3 className="bgraph-h">Battle Script ({rules.length} rules)</h3>
          <p className="bgraph-sub">
            Each rule fires on a trigger; if its conditions hold, the boss performs
            the effects in order.
          </p>
          <ol className="mt-3 flex flex-col gap-3">
            {rules.map((r, i) => (
              <RuleCard key={i} rule={r} index={i} />
            ))}
          </ol>
        </section>
      )}

      {/* Legend */}
      {(rules.length > 0 || graph.strongSkills.length > 0) && <Legend />}

      {!hasNodes && !graph.note && graph.strongSkills.length === 0 && (
        <p className="text-sm text-ink-mute">No graph detail available.</p>
      )}
    </div>
  )
}

function RuleCard({ rule, index }: { rule: Rule; index: number }) {
  return (
    <li className="bgraph-rule">
      <div className="bgraph-rule-head">
        <span className="bgraph-step">{index + 1}</span>
        <span className="bgraph-when">WHEN</span>
        <span className="bgraph-trigger" title={rule.triggerRaw}>
          {rule.trigger}
        </span>
      </div>

      {rule.conditions.length > 0 && (
        <div className="bgraph-row">
          <span className="bgraph-tag bgraph-tag-if">IF</span>
          <div className="flex flex-wrap gap-1.5">
            {rule.conditions.map((c, i) => (
              <span key={i} className="bgraph-cond">
                {c}
              </span>
            ))}
          </div>
        </div>
      )}

      {rule.effects.length > 0 && (
        <div className="bgraph-row">
          <span className="bgraph-tag bgraph-tag-do">DO</span>
          <div className="flex flex-1 flex-col gap-1.5">
            {rule.effects.map((e, i) => (
              <EffectRow key={i} effect={e} />
            ))}
          </div>
        </div>
      )}
    </li>
  )
}

function EffectRow({ effect }: { effect: Effect }) {
  if (effect.kind === 'rotation' && effect.rotationSkills) {
    return (
      <div className="bgraph-effect">
        <span className="bgraph-eff-label">Skill rotation</span>
        <div className="mt-1 flex flex-wrap items-center gap-1">
          {effect.rotationSkills.map((s, i) => (
            <span key={i} className="inline-flex items-center gap-1">
              {i > 0 && <span className="bgraph-arrow">→</span>}
              {s.guid ? (
                <Link to={`/moves/${s.guid}`} className="bgraph-rot">
                  {s.type && <TypeBadge type={s.type} small />}
                  {s.name}
                </Link>
              ) : (
                <span className="bgraph-rot bgraph-rot-unknown">{s.name}</span>
              )}
            </span>
          ))}
        </div>
      </div>
    )
  }

  if (effect.kind === 'strongSkill' && effect.strong) {
    return (
      <div className="bgraph-effect bgraph-effect-strong">
        <span className="inline-flex items-center gap-1">
          <IconSpark className="text-sm text-gold" />
          <span className="bgraph-eff-label">Strong skill</span>
        </span>
        {effect.strong.guid ? (
          <Link to={`/moves/${effect.strong.guid}`} className="bgraph-rot ml-1">
            {effect.strong.name}
          </Link>
        ) : (
          <span className="font-bold text-ink ml-1">{effect.strong.name}</span>
        )}
        {effect.strong.target && (
          <span className="bgraph-eff-detail">→ {effect.strong.target}</span>
        )}
      </div>
    )
  }

  return (
    <div className={`bgraph-effect bgraph-eff-${effect.kind}`}>
      <span className="bgraph-eff-label">{effect.label}</span>
      {effect.detail && <span className="bgraph-eff-detail">{effect.detail}</span>}
    </div>
  )
}

function Legend() {
  const items: { label: string; cls: string }[] = [
    { label: 'WHEN — trigger', cls: 'bgraph-leg-when' },
    { label: 'IF — condition / HP gate', cls: 'bgraph-leg-if' },
    { label: 'DO — effect', cls: 'bgraph-leg-do' },
    { label: 'Strong skill (telegraphed)', cls: 'bgraph-leg-strong' },
    { label: 'Phase change', cls: 'bgraph-leg-phase' },
  ]
  return (
    <div className="bgraph-legend">
      <span className="bgraph-legend-title">Legend</span>
      {items.map((it) => (
        <span key={it.label} className="bgraph-legend-item">
          <span className={`bgraph-legend-swatch ${it.cls}`} />
          {it.label}
        </span>
      ))}
    </div>
  )
}
