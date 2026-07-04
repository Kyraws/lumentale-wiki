import { Link, useParams } from 'react-router-dom'
import { useApi } from '../lib/api'
import Sprite from '../components/Sprite'
import { TypeBadge, EmotionBadge } from '../components/Badge'
import { ErrorState, Skeleton } from '../components/States'
import { IconBack } from '../components/Icons'
import BossBattleGraph, { type BossGraphData, type SkillRef } from '../components/BossBattleGraph'
import { elementColor, fmtNum, titleCase } from '../lib/game'

// ----- page-local types (inspected from live /api/bosses/{guid} + /graph) -----

interface BossSkill {
  moveGuid: string
  moveName: string
  type?: string
  level?: number
  ord?: number
}

interface BossRef {
  guid: string
  species: string
  menuArt?: string | null
}

interface BossForm {
  guid: string
  species: string
  variant?: string
  menuArt?: string | null
}

/** Graph stub embedded in the detail payload. present is always true in seed,
 * but a `note` (with no nodes) means there's no usable graph to expand. */
interface GraphStub {
  present: boolean
  graphName?: string
  nodeCount?: number
  note?: string
}

interface BossDetailData {
  guid: string
  internalName: string
  display?: string
  level?: number
  ele?: string
  emotion?: string
  hiddenType?: string
  expGiven?: number
  targetBst?: number
  extraHealthBars?: number
  statsOverride?: number[] // observed as a 6-number array [HP,ATK,DEF,SpA,SpD,Spe]
  ai?: Record<string, number>
  originSpecies?: BossRef | null
  form?: BossForm | null
  skills: BossSkill[]
  graph?: GraphStub
}

const STAT_LABELS = ['HP', 'ATK', 'DEF', 'SpA', 'SpD', 'Spe']

export default function BossDetail() {
  const { guid = '' } = useParams()
  const { data, loading, error, reload } = useApi<BossDetailData>(`/api/bosses/${guid}`)

  if (error) return <ErrorState message={error} onRetry={reload} />
  if (loading || !data) return <DetailSkeleton />

  const name = data.display || data.internalName
  const stub = data.graph

  return (
    <div className="flex flex-col gap-5">
      <Link to="/bosses" className="pixel-btn inline-flex w-fit items-center gap-1">
        <IconBack /> Back to Bosses
      </Link>

      {/* Hero header */}
      <section className="dialog-box relative overflow-hidden p-5 md:p-7" style={{ borderColor: '#0c0d1d' }}>
        <div
          className="pointer-events-none absolute inset-0 opacity-25"
          style={{ background: `radial-gradient(120% 90% at 20% 0%, ${elementColor(data.ele)}, transparent 60%)` }}
        />
        <div className="relative flex flex-col gap-3 sm:flex-row sm:items-start">
          {/* Boss icon (origin-form menu art) */}
          {data.form?.menuArt && (
            <div className="flex h-24 w-24 shrink-0 items-center justify-center rounded-[3px] border-3 border-cream/30 bg-black/20">
              <Sprite src={data.form.menuArt} alt={name} size={84} bob />
            </div>
          )}
          <div className="flex flex-1 flex-col gap-3">
          <div className="flex flex-wrap items-center gap-3">
            {data.level != null && (
              <span className="text-lg text-berry" style={{ fontFamily: 'var(--font-pixel)' }}>
                Lv {data.level}
              </span>
            )}
            <h1 className="text-xl text-cream text-pixel-shadow md:text-2xl">{name}</h1>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <TypeBadge type={data.ele} />
            <EmotionBadge emo={data.emotion} />
            {data.hiddenType && (
              <span className="type-chip" style={{ backgroundColor: elementColor(data.hiddenType), opacity: 0.7 }}>
                {data.hiddenType.toUpperCase()} (hidden)
              </span>
            )}
            {data.extraHealthBars != null && data.extraHealthBars > 0 && (
              <span className="inline-flex items-center gap-1 text-[0.6rem] font-extrabold uppercase tracking-wide text-cream/70">
                Health bars
                <span className="inline-flex items-center gap-0.5">
                  <span className="h-2.5 w-4 rounded-[1px] bg-cream/80" />
                  {Array.from({ length: data.extraHealthBars }).map((_, i) => (
                    <span key={i} className="h-2.5 w-4 rounded-[1px] bg-berry/90" />
                  ))}
                </span>
              </span>
            )}
          </div>

          <div className="flex flex-wrap gap-2">
            {data.expGiven != null && (
              <span className="rounded-[2px] bg-black/20 px-2 py-1 text-xs font-bold text-gold">
                {fmtNum(data.expGiven)} EXP
              </span>
            )}
            {data.targetBst != null && (
              <span className="rounded-[2px] bg-black/20 px-2 py-1 text-xs font-bold text-cream/70">
                Target BST {fmtNum(data.targetBst)}
              </span>
            )}
          </div>
          </div>
        </div>
      </section>

      {/* Origin species + boss form */}
      {(data.originSpecies || data.form) && (
        <Panel title="Lineage">
          <div className="flex flex-wrap gap-3">
            {data.form && (
              <Link
                to={`/dex/${data.form.guid}`}
                className="flex w-32 flex-col items-center rounded-[2px] border-2 border-lumen bg-lumen/10 p-3 text-center transition-transform hover:-translate-y-0.5"
              >
                <Sprite src={data.form.menuArt} alt={data.form.species} size={64} />
                <span className="mt-1 line-clamp-1 text-xs font-extrabold text-ink">{data.form.species}</span>
                {data.form.variant && data.form.variant !== 'Base Form' && (
                  <span className="line-clamp-1 text-[0.6rem] font-bold text-ink-mute">{data.form.variant}</span>
                )}
                <span className="mt-1 text-[0.55rem] font-bold text-lumen-deep">BOSS FORM</span>
              </Link>
            )}
            {data.originSpecies && (
              <Link
                to={`/dex/${data.originSpecies.guid}`}
                className="flex w-32 flex-col items-center justify-center rounded-[2px] border-2 border-ink/15 bg-ink/5 p-3 text-center transition-transform hover:-translate-y-0.5"
              >
                {data.originSpecies.menuArt && (
                  <Sprite src={data.originSpecies.menuArt} alt={data.originSpecies.species} size={64} />
                )}
                <span className="mt-1 line-clamp-2 text-xs font-extrabold text-ink">{data.originSpecies.species}</span>
                <span className="mt-1 text-[0.55rem] font-bold text-ink-mute">ORIGIN SPECIES</span>
              </Link>
            )}
          </div>
        </Panel>
      )}

      {/* Skill kit */}
      <Panel title="Skill Kit">
        {data.skills.length === 0 ? (
          <p className="text-sm text-ink-mute">No skill kit recorded for this boss.</p>
        ) : (
          <ul className="divide-y divide-ink/10">
            {[...data.skills]
              .sort((a, b) => (a.ord ?? 0) - (b.ord ?? 0))
              .map((s) => (
                <li key={s.moveGuid}>
                  <Link
                    to={`/moves/${s.moveGuid}`}
                    className="flex items-center justify-between gap-2 py-2 text-sm hover:bg-ink/5"
                  >
                    <span className="flex items-center gap-2">
                      <TypeBadge type={s.type} small />
                      <span className="font-bold text-ink">{s.moveName}</span>
                    </span>
                    {s.level != null && s.level > 0 && (
                      <span className="text-xs font-bold text-ink-mute">Lv {s.level}</span>
                    )}
                  </Link>
                </li>
              ))}
          </ul>
        )}
      </Panel>

      {/* Stats override + AI (jsonb, only if present) */}
      {((data.statsOverride && data.statsOverride.length > 0) ||
        (data.ai && Object.keys(data.ai).length > 0)) && (
        <div className="grid gap-5 lg:grid-cols-2">
          {data.statsOverride && data.statsOverride.length > 0 && (
            <Panel title="Stats Override">
              <div className="grid grid-cols-3 gap-2 sm:grid-cols-6">
                {data.statsOverride.map((v, i) => (
                  <div key={i} className="rounded-[2px] bg-ink/5 px-2 py-1.5 text-center">
                    <div className="text-[0.6rem] font-bold uppercase tracking-wide text-ink-mute">
                      {STAT_LABELS[i] ?? `#${i}`}
                    </div>
                    <div className="text-base font-extrabold text-ink" style={{ fontFamily: 'var(--font-pixel)' }}>
                      {v}
                    </div>
                  </div>
                ))}
              </div>
            </Panel>
          )}
          {data.ai && Object.keys(data.ai).length > 0 && (
            <Panel title="AI Profile">
              <div className="grid grid-cols-2 gap-2">
                {Object.entries(data.ai).map(([k, v]) => (
                  <div key={k} className="flex items-center justify-between rounded-[2px] bg-ink/5 px-2 py-1.5">
                    <span className="text-[0.65rem] font-bold text-ink-soft">{titleCase(k)}</span>
                    <span className="text-sm font-extrabold text-ink" style={{ fontFamily: 'var(--font-pixel)' }}>
                      {typeof v === 'number' ? v : String(v)}
                    </span>
                  </div>
                ))}
              </div>
            </Panel>
          )}
        </div>
      )}

      {/* Battle graph (only fetch when present) */}
      {stub?.present && <BattleGraphSection guid={guid} skills={data.skills} />}
    </div>
  )
}

/* ---------- battle graph ---------- */

function BattleGraphSection({ guid, skills }: { guid: string; skills: SkillRef[] }) {
  const { data, loading, error } = useApi<BossGraphData>(`/api/bosses/${guid}/graph`)

  return (
    <Panel title="Battle Graph">
      {error ? (
        <p className="text-sm text-berry">Could not load graph: {error}</p>
      ) : loading || !data ? (
        <Skeleton className="h-40 w-full" />
      ) : (
        <BossBattleGraph graph={data} skills={skills} />
      )}
    </Panel>
  )
}

/* ---------- shared ---------- */

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="pixel-panel p-4 md:p-5">
      <h2
        className="mb-4 inline-block rounded-[2px] bg-ink px-2 py-1 text-[0.7rem] text-cream"
        style={{ fontFamily: 'var(--font-display)' }}
      >
        {title}
      </h2>
      {children}
    </section>
  )
}

function DetailSkeleton() {
  return (
    <div className="flex flex-col gap-5">
      <Skeleton className="h-9 w-36" />
      <Skeleton className="h-40 w-full" />
      <Skeleton className="h-32 w-full" />
      <Skeleton className="h-64 w-full" />
    </div>
  )
}
