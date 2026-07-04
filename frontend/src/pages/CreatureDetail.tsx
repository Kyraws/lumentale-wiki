import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useApi } from '../lib/api'
import { useImageExists } from '../lib/useImageExists'
import type { CreatureDetail as CDetail, CreatureSummary, EvoNode, TypeChart } from '../lib/types'
import Sprite from '../components/Sprite'
import { TypeBadge, EmotionBadge, RegionBadge, Tag } from '../components/Badge'
import { StatBlock } from '../components/StatBar'
import { ErrorState, Skeleton } from '../components/States'
import { IconBack } from '../components/Icons'
import { rawStr, rawNum, titleCase, dexNo, EFFECTIVENESS, emotionColor } from '../lib/game'
import type { LearnsetEntry } from '../lib/types'

const sprites = (guid: string) => ({
  front: `/data/forms/${guid}/front.png`,
  menu: `/data/forms/${guid}/menu.png`,
  over: `/data/forms/${guid}/overworld.png`,
  lost: `/data/forms/${guid}/lost_menu.png`,
})

/** PossibleQuirks entry from the raw form. */
type RawQuirk = { Type?: { m_Name?: string }; IsHidden?: number }

export default function CreatureDetail() {
  const { guid = '' } = useParams()
  const { data, loading, error, reload } = useApi<CDetail>(`/api/creatures/${guid}`)
  const [lost, setLost] = useState(false)
  const lostUrl = sprites(guid).lost
  const hasLost = useImageExists(lostUrl)
  const hasOver = useImageExists(sprites(guid).over)

  // Prev/next within the dex: order all forms by dex number (stable sort keeps
  // a species' variants in their natural order) and step to the neighbours.
  const { data: list } = useApi<CreatureSummary[]>('/api/creatures')
  const { prev, next } = useMemo(() => {
    if (!list) return { prev: null, next: null }
    const ordered = [...list].sort((a, b) => a.dex - b.dex)
    const i = ordered.findIndex((c) => c.guid === guid)
    return {
      prev: i > 0 ? ordered[i - 1] : null,
      next: i >= 0 && i < ordered.length - 1 ? ordered[i + 1] : null,
    }
  }, [list, guid])

  if (error) return <ErrorState message={error} onRetry={reload} />
  if (loading || !data) return <DetailSkeleton />

  const f = data.form
  // v3.1: species/variant/dex/ele/description are English-resolved siblings from
  // the API (no more parsing raw Italian). Fall back to the evo-chain node only
  // if the backend didn't supply them.
  const me = data.evoChain.flat().find((n) => n.current)
  const species = data.species ?? me?.species ?? 'Unknown'
  const variant = data.variant ?? me?.variant ?? ''
  const emo = data.typeChart.emotion
  const desc = data.description ?? rawStr(f, 'Description')
  const hiddenTypes = (Array.isArray(f.PossibleHiddenTypes) ? f.PossibleHiddenTypes : []) as unknown[]
  const s = sprites(guid)
  const heroSrc = lost && hasLost ? s.lost : s.front

  return (
    <div className="flex flex-col gap-5">
      <nav className="flex items-stretch justify-between gap-2">
        <NeighborLink c={prev} dir="prev" />
        <Link
          to="/dex"
          className="inline-flex shrink-0 items-center gap-1 self-center px-2 text-sm font-bold text-cream/70 hover:text-cream"
        >
          <IconBack /> <span className="hidden sm:inline">Back to Dex</span>
        </Link>
        <NeighborLink c={next} dir="next" />
      </nav>

      {/* Hero */}
      <section
        className="dialog-box relative overflow-hidden p-5 md:p-7"
        style={{ borderColor: '#0c0d1d' }}
      >
        <div
          className="pointer-events-none absolute inset-0 opacity-25"
          style={{ background: `radial-gradient(120% 90% at 20% 0%, ${emotionColor(emo)}, transparent 60%)` }}
        />
        <div className="relative grid gap-6 md:grid-cols-[auto_1fr] md:items-center">
          <div className="flex items-center justify-center gap-4">
            <div className="pixel-screen flex h-44 w-44 items-center justify-center p-2">
              <Sprite src={heroSrc} alt={species} size={150} className="anim-float" />
            </div>
            {hasOver && (
              <div className="hidden self-end sm:block">
                <Sprite src={s.over} alt={`${species} overworld`} size={56} bob />
              </div>
            )}
          </div>

          <div>
            <div className="mb-2 flex items-center gap-3">
              {data.regions.map((r) => (
                <RegionBadge key={r} region={r} />
              ))}
            </div>
            {data.dex != null && (
              <span className="text-sm font-extrabold text-cream/50" style={{ fontFamily: 'var(--font-pixel)' }}>
                {dexNo(data.dex)}
              </span>
            )}
            <h1 className="text-xl text-cream text-pixel-shadow md:text-2xl">{species}</h1>
            {variant && variant !== 'Base Form' && (
              <p className="mt-1 text-sm font-bold text-lumen">{variant}</p>
            )}
            <div className="mt-3 flex flex-wrap gap-2">
              {data.ele && <TypeBadge type={data.ele} />}
              {emo && <EmotionBadge emo={emo} />}
              {hiddenTypes.map((h, i) => (
                <Tag key={i}>{titleCase(h)} (hidden)</Tag>
              ))}
            </div>

            {desc && (
              <p className="mt-4 max-w-prose border-l-4 border-lumen/40 pl-3 text-sm italic leading-relaxed text-cream/80">
                {desc}
              </p>
            )}

            {hasLost && (
              <button
                onClick={() => setLost((v) => !v)}
                aria-pressed={lost}
                className={`mt-4 rounded-[2px] border-2 px-3 py-1.5 text-[0.6rem] font-extrabold uppercase tracking-wide transition-colors ${
                  lost ? 'border-berry bg-berry/25 text-berry' : 'border-cream/20 text-cream/70 hover:bg-white/5'
                }`}
              >
                {lost ? '◆ Showing Lost form' : '◇ View Lost form'}
              </button>
            )}
          </div>
        </div>
      </section>

      <div className="grid gap-5 lg:grid-cols-2">
        {/* Stats */}
        <Panel title="Base Stats">
          <StatBlock
            grades={data.statGrades}
            values={Array.isArray(f.StatMinValues) ? (f.StatMinValues as number[]) : undefined}
          />
          <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-3">
            <Fact label="Catch Rate" value={rawNum(f, 'CatchRate')} />
            <Fact label="SP Cost" value={rawNum(f, 'SPAmount')} />
            <Fact label="EXP Mult" value={rawNum(f, 'ExpGivenMultiplier')?.toFixed(2)} />
            <Fact label="Affection" value={rawNum(f, 'BaseAffection')} />
            <Fact label="Height" value={range(f.RangeHeight, 'm')} />
            <Fact label="Weight" value={range(f.RangeWeight, 'kg')} />
          </div>
        </Panel>

        {/* Quirks */}
        <Panel title="Quirks">
          <Quirks quirks={f.PossibleQuirks as RawQuirk[] | undefined} />
        </Panel>
      </div>

      {/* Flagship type chart */}
      <Panel title="Type Chart">
        <TypeChartSection chart={data.typeChart} />
      </Panel>

      {/* Learnset */}
      {data.learnset && data.learnset.length > 0 && (
        <Panel title="Moves">
          <Learnset moves={data.learnset} />
        </Panel>
      )}

      {/* Evolution chain */}
      {data.evoChain?.some((stage) => stage.length) && (
        <Panel title="Evolution Line">
          <EvoChain stages={data.evoChain} />
        </Panel>
      )}

      <div className="grid gap-5 lg:grid-cols-2">
        {/* Where to find */}
        <Panel title="Habitat">
          {data.spawns.length === 0 ? (
            <p className="text-sm text-ink-mute">Not found wild — obtained via evolution, gift or event.</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {data.spawns.map((sp) => (
                <li key={sp.guid}>
                  <Link
                    to={`/maps/${sp.guid}`}
                    className="flex items-center justify-between gap-2 rounded-[2px] bg-ink/5 px-3 py-2 text-sm font-bold text-ink hover:bg-ink/10"
                  >
                    <span className="truncate">{sp.name}</span>
                    {sp.levelMin != null && (
                      <span className="shrink-0 text-xs text-ink-mute">
                        Lv {sp.levelMin}–{sp.levelMax ?? sp.levelMin}
                      </span>
                    )}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Panel>

        {/* Used by trainers */}
        <Panel title="Trained By">
          {data.usedBy.length === 0 ? (
            <p className="text-sm text-ink-mute">No trainers field this creature.</p>
          ) : (
            <ul className="flex flex-wrap gap-2">
              {data.usedBy.map((u) => (
                <li key={u.guid}>
                  <span className="inline-flex items-center gap-2 rounded-[2px] border-2 border-ink/30 bg-ink/5 px-3 py-1.5 text-sm font-bold text-ink">
                    {u.kind === 'boss' && <span className="text-berry">★</span>}
                    {titleCase(u.name)}
                    {u.level != null && <span className="text-xs text-ink-mute">Lv {u.level}</span>}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Panel>
      </div>
    </div>
  )
}

/* ---------- small building blocks ---------- */

/** Prev/next dex neighbour pill, with dex no., species and a tiny sprite.
 *  Renders an empty spacer (keeps the nav balanced) at the dex ends. */
function NeighborLink({ c, dir }: { c: CreatureSummary | null; dir: 'prev' | 'next' }) {
  if (!c) return <span className="flex-1" />
  const next = dir === 'next'
  return (
    <Link
      to={`/dex/${c.guid}`}
      className={`group flex flex-1 items-center gap-2 rounded-[2px] border-2 border-cream/15 px-2 py-1.5 text-cream/80 transition-colors hover:border-gold hover:bg-white/5 ${
        next ? 'flex-row-reverse text-right' : ''
      }`}
      title={`${dexNo(c.dex)} ${c.species}`}
    >
      <span className="text-base leading-none text-cream/50 group-hover:text-gold">
        {next ? '›' : '‹'}
      </span>
      <Sprite src={c.menuArt} alt={c.species} size={32} />
      <span className={`flex min-w-0 flex-col ${next ? 'items-end' : ''}`}>
        <span className="text-[0.6rem] font-bold text-cream/45" style={{ fontFamily: 'var(--font-pixel)' }}>
          {dexNo(c.dex)}
        </span>
        <span className="truncate text-xs font-extrabold">{c.species}</span>
      </span>
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

function Fact({ label, value }: { label: string; value?: string | number }) {
  return (
    <div className="rounded-[2px] bg-ink/5 px-2 py-1.5">
      <div className="text-[0.6rem] font-bold uppercase tracking-wide text-ink-mute">{label}</div>
      <div className="text-xl font-extrabold text-ink" style={{ fontFamily: 'var(--font-pixel)' }}>
        {value ?? '—'}
      </div>
    </div>
  )
}

function range(v: unknown, unit: string): string | undefined {
  if (Array.isArray(v) && v.length === 2 && typeof v[0] === 'number') {
    const [a, b] = v as number[]
    return `${a.toFixed(1)}–${b.toFixed(1)}${unit}`
  }
  return undefined
}

/* ---------- flagship type chart ---------- */

const ELEM_GROUPS = ['WEAKNESS', 'RESISTANCE', 'IMMUNITY', 'NORMAL'] as const

function TypeChartSection({ chart }: { chart: TypeChart }) {
  // Group attacking elements by how this creature takes them.
  const byEff: Record<string, string[]> = {}
  for (const { attacker, effectiveness } of chart.elemental) {
    ;(byEff[effectiveness] ??= []).push(attacker)
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Elemental defense */}
      <div>
        <h3 className="mb-3 text-[0.65rem] font-extrabold uppercase tracking-wide text-ink-mute">
          Elemental defense — how this creature takes incoming attacks
        </h3>
        <div className="flex flex-col gap-3">
          {ELEM_GROUPS.map((eff) => {
            const types = byEff[eff] ?? []
            if (types.length === 0) return null
            const info = EFFECTIVENESS[eff]
            return (
              <div key={eff} className="rounded-[2px] bg-ink/5 p-3">
                <div
                  className="mb-2 flex items-center gap-2 text-[0.7rem] font-extrabold uppercase tracking-wide"
                  style={{ color: info.tint }}
                >
                  <span className="leading-none" aria-hidden>{info.glyph}</span>
                  <span>{info.label}</span>
                  <span className="rounded-[2px] bg-ink/10 px-1.5 py-0.5 text-ink-soft">{types.length}</span>
                </div>
                <div className="flex flex-wrap gap-1.5">
                  {types.map((t) => (
                    <span
                      key={t}
                      className="inline-flex items-center gap-1"
                      style={{ outline: `2px solid ${info.tint}`, outlineOffset: 1, borderRadius: 2 }}
                      title={`${info.label}: ${t}`}
                    >
                      <TypeBadge type={t} small />
                    </span>
                  ))}
                </div>
              </div>
            )
          })}
        </div>
      </div>

      {/* Emotion matchups — one row per emotion: damage dealt ◂ emotion ▸ damage taken */}
      <EmotionMatchups offense={chart.emotionOffense} defense={chart.emotionDefense} />
    </div>
  )
}

/** Tint a multiplier by whether it's *good* for this creature. On offense a
 *  higher number is good (more damage dealt); on defense it's bad (more taken). */
function emoTint(m: number, kind: 'off' | 'def'): string {
  if (m === 1) return 'var(--color-ink-mute)'
  const good = kind === 'off' ? m > 1 : m < 1
  return good ? 'var(--color-lumen)' : 'var(--color-berry)'
}

function EmotionMatchups({
  offense,
  defense,
}: {
  offense: { other: string; multiplier: number }[]
  defense: { other: string; multiplier: number }[]
}) {
  if (!offense.length && !defense.length) return null
  const defBy = new Map(defense.map((d) => [d.other, d.multiplier]))
  // Preserve offense ordering; fall back to defense for any emotion only there.
  const emotions = offense.map((o) => o.other)
  for (const d of defense) if (!emotions.includes(d.other)) emotions.push(d.other)
  const offBy = new Map(offense.map((o) => [o.other, o.multiplier]))

  return (
    <div>
      <h3 className="mb-1 text-[0.65rem] font-extrabold uppercase tracking-wide text-ink-mute">
        Emotion matchups — damage dealt vs taken
      </h3>
      <p className="mb-3 text-[0.6rem] text-ink-mute/80">
        Each cell reads: damage dealt <span className="text-ink-soft">·</span> emotion{' '}
        <span className="text-ink-soft">·</span> damage taken.
      </p>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
        {emotions.map((emo) => {
          const off = offBy.get(emo)
          const def = defBy.get(emo)
          return (
            <div
              key={emo}
              className="flex items-center justify-center gap-2 rounded-[2px] bg-ink/5 px-2 py-2.5"
            >
              <span
                className="text-base font-extrabold"
                style={{ fontFamily: 'var(--font-pixel)', color: off == null ? undefined : emoTint(off, 'off') }}
                title="Damage dealt"
              >
                {off == null ? '—' : `×${off}`}
              </span>
              <EmotionBadge emo={emo} small />
              <span
                className="text-base font-extrabold"
                style={{ fontFamily: 'var(--font-pixel)', color: def == null ? undefined : emoTint(def, 'def') }}
                title="Damage taken"
              >
                {def == null ? '—' : `×${def}`}
              </span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

/* ---------- learnset ---------- */

function Learnset({ moves }: { moves: LearnsetEntry[] }) {
  // Level-up moves carry a real level; everything else (tutor / event / egg)
  // arrives at level 0. Split so the two ways of learning read separately.
  const levelUp = moves
    .filter((m) => m.level != null && m.level > 0)
    .sort((a, b) => (a.level ?? 0) - (b.level ?? 0))
  const tutor = moves.filter((m) => m.level == null || m.level <= 0)

  return (
    <div className="flex flex-col gap-5">
      {levelUp.length > 0 && (
        <LearnGroup label="By Level-Up" moves={levelUp} showLevel />
      )}
      {tutor.length > 0 && <LearnGroup label="By Tutor" moves={tutor} />}
    </div>
  )
}

function LearnGroup({
  label,
  moves,
  showLevel = false,
}: {
  label: string
  moves: LearnsetEntry[]
  showLevel?: boolean
}) {
  return (
    <div>
      <h3 className="mb-2 flex items-center gap-2 text-[0.65rem] font-extrabold uppercase tracking-wide text-ink-mute">
        {label}
        <span className="rounded-[2px] bg-ink/10 px-1.5 py-0.5 text-ink-soft">{moves.length}</span>
      </h3>
      <div className="grid grid-cols-1 gap-1.5 sm:grid-cols-2 lg:grid-cols-3">
        {moves.map((m) => (
          <Link
            key={m.moveGuid + (m.level ?? '')}
            to={`/moves/${m.moveGuid}`}
            className="flex items-center gap-2 rounded-[2px] bg-ink/5 px-2.5 py-2 transition-colors hover:bg-ink/10"
          >
            {showLevel ? (
              <span
                className="w-7 shrink-0 text-center text-xs font-extrabold text-ink-mute"
                style={{ fontFamily: 'var(--font-pixel)' }}
                title="Learned at level"
              >
                {m.level}
              </span>
            ) : (
              <span className="w-7 shrink-0 text-center text-[0.55rem] font-bold text-ink-mute/70" title={m.method ?? ''}>
                ◆
              </span>
            )}
            <span className="flex-1 truncate text-sm font-bold text-ink">{m.name}</span>
            {m.type && m.type !== 'NONE' && <TypeBadge type={m.type} small />}
          </Link>
        ))}
      </div>
    </div>
  )
}

/* ---------- quirks ---------- */

/** One row of /api/quirks, keyed by the form's quirk `class` (Type.m_Name). */
type QuirkInfo = { class: string; name: string; description?: string }

function Quirks({ quirks }: { quirks?: RawQuirk[] }) {
  const { data: all } = useApi<QuirkInfo[]>('/api/quirks')
  if (!quirks?.length) return <p className="text-sm text-ink-mute">No quirks recorded.</p>
  const byClass = new Map((all ?? []).map((q) => [q.class, q]))
  return (
    <ul className="flex flex-col gap-2">
      {quirks.map((q, i) => {
        const cls = q.Type?.m_Name ?? ''
        const info = byClass.get(cls)
        const title = info?.name ?? titleCase(cls || 'Unknown')
        return (
          <li key={i}>
            <Link
              to={`/quirks#${cls}`}
              className="block rounded-[2px] bg-ink/5 px-3 py-2 transition-colors hover:bg-ink/10"
              title={`See ${title} on the Quirks page`}
            >
              <span className="flex items-center justify-between gap-2">
                <span className="text-sm font-extrabold text-ink">{title}</span>
                {q.IsHidden ? <Tag>Hidden</Tag> : null}
              </span>
              {info?.description && (
                <span className="mt-1 block text-xs leading-relaxed text-ink-soft">{info.description}</span>
              )}
            </Link>
          </li>
        )
      })}
    </ul>
  )
}

/* ---------- evolution chain ---------- */

function EvoChain({ stages }: { stages: EvoNode[][] }) {
  return (
    <div className="flex flex-wrap items-stretch gap-2">
      {stages.map((stage, si) => (
        <div key={si} className="flex items-center gap-2">
          {si > 0 && (
            <div className="flex shrink-0 flex-col items-center px-1 text-ink-mute">
              <span className="text-xl leading-none">→</span>
              {stage[0]?.level && (
                <span className="text-[0.6rem] font-bold">Lv {stage[0].level}</span>
              )}
              {stage[0]?.methodClass && !stage[0]?.level && (
                <span className="max-w-16 text-center text-[0.55rem] font-bold leading-tight">
                  {titleCase(stage[0].methodClass.replace(/^EvolveBy/, ''))}
                </span>
              )}
            </div>
          )}
          <div className="flex flex-wrap gap-2">
            {stage.map((n) => (
              <Link
                key={n.formGuid}
                to={`/dex/${n.formGuid}`}
                className={`flex w-24 flex-col items-center rounded-[2px] border-2 p-2 text-center transition-transform hover:-translate-y-0.5 ${
                  n.current ? 'border-lumen bg-lumen/10' : 'border-ink/15 bg-ink/5'
                }`}
              >
                <Sprite src={n.menuArt ?? undefined} alt={n.species} size={56} />
                <span className="mt-1 line-clamp-1 text-xs font-extrabold text-ink">{n.species}</span>
                {n.current && <span className="text-[0.55rem] font-bold text-lumen-deep">CURRENT</span>}
              </Link>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}

function DetailSkeleton() {
  return (
    <div className="flex flex-col gap-5">
      <Skeleton className="h-5 w-28" />
      <Skeleton className="h-56 w-full" />
      <div className="grid gap-5 lg:grid-cols-2">
        <Skeleton className="h-64 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    </div>
  )
}
