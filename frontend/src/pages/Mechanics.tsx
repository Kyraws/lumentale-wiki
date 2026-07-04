import { useState } from 'react'
import { useApi } from '../lib/api'
import type { MechanicsOverview, FormulaSummary } from '../lib/types'
import { ErrorState, EmptyState, Skeleton } from '../components/States'
import { Tag } from '../components/Badge'
import { IconCog, IconInfo } from '../components/Icons'
import { confidenceColor, fmtNum, titleCase } from '../lib/game'
import { FormulaExpr } from './mechanics/FormulaExpr'
import { XpCurveChart } from './mechanics/XpCurveChart'
import './mechanics/mechanics.css'

// ----- page-local detail shapes (from the live API; optional keys omitted) -----

interface MechConstant {
  name: string
  value: number
  kind: string
  formulaKey?: string
  description?: string
}

interface FormulaDetail {
  key: string
  name: string
  signature?: string | null
  expression?: string
  description?: string
  confidence?: string | null
  sourceFile?: string
  constants: MechConstant[]
}

/** Plain-English gloss per formula key — curated from native/FORMULAS.md so the
 * page explains WHAT each recovered expression does, not just shows the math. */
const FORMULA_GLOSS: Record<string, string> = {
  damage:
    'The core per-hit damage number. Attack vs. defence sets the ratio; level and move power scale it up; the result is then multiplied by crit, type and emotion modifiers downstream.',
  damage_pipeline:
    'The full path from a move + the two combatants to a final damage number — it chains the base damage formula through crit, elemental and emotion effectiveness, a random spread roll, and the difficulty scalar.',
  crit_chance:
    'Critical-hit probability as a function of the attacker’s crit stat-stage. Each stage adds an increasing amount to the numerator over a fixed 23 denominator.',
  stat: 'How a creature’s final stat is computed from its base value, nature, level and an IV-like term. HP uses a separate branch with an extra flat bonus.',
  stat_stage_mult:
    'The classic in-battle buff/debuff table — a +1 stat stage multiplies the stat by 1.5×, −1 by ~0.67×, and so on.',
  emotion_effectiveness:
    'The emotion-type matchup multiplier applied on top of elemental effectiveness: super-effective ×1.2, resisted ×0.8, neutral ×1.0.',
  difficulty:
    'A global damage scalar based on the chosen difficulty. Easy favours the player on both ends; Hard makes incoming damage hit harder.',
  catch_rate:
    'There is no single closed-form catch formula. The catch rate is composed at runtime from whatever modifiers are active — bilia (capture-item) effects and camp buffs each adjust a running rate.',
  xp_curve:
    'Experience required to reach a level. Some curve types are closed-form polynomials; others are designer-authored AnimationCurve keyframe data (see the chart below).',
}

/** Confidence chip: color + word (never color alone). */
function ConfidenceChip({ confidence }: { confidence?: string | null }) {
  const word = (confidence ?? 'unknown').toLowerCase()
  return (
    <span
      className="inline-flex items-center rounded-[2px] border-2 px-2 py-0.5 text-xs font-extrabold uppercase tracking-wide"
      style={{ borderColor: confidenceColor(confidence), color: confidenceColor(confidence) }}
    >
      {word}
    </span>
  )
}

function ConfidenceLegend() {
  const items: { word: string; conf: string; meaning: string }[] = [
    { word: 'verified', conf: 'verified', meaning: 'arithmetic + constants confirmed' },
    { word: 'structural', conf: 'structural', meaning: 'shape exact, one numeric point unconfirmed' },
    { word: 'partial', conf: 'partial', meaning: 'mechanism known, not fully closed-form' },
  ]
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
      {items.map((i) => (
        <span key={i.word} className="inline-flex items-center gap-2 text-sm text-ink-soft">
          <ConfidenceChip confidence={i.conf} />
          {i.meaning}
        </span>
      ))}
    </div>
  )
}

function SectionHeading({ icon, children }: { icon?: React.ReactNode; children: React.ReactNode }) {
  return (
    <h2
      className="flex items-center gap-2 text-base text-cream text-pixel-shadow"
      style={{ fontFamily: 'var(--font-display)' }}
    >
      {icon}
      {children}
    </h2>
  )
}

/** A clean, labelled constants table with explanations. */
function ConstantsTable({ rows, showFormula = false }: { rows: MechConstant[]; showFormula?: boolean }) {
  if (rows.length === 0) return null
  return (
    <div className="pixel-panel overflow-x-auto p-1">
      <table className="w-full min-w-[480px] border-collapse text-left">
        <thead>
          <tr className="text-xs uppercase tracking-wide text-ink-mute">
            <th scope="col" className="px-3 py-2">Constant</th>
            <th scope="col" className="px-3 py-2 text-right">Value</th>
            <th scope="col" className="px-3 py-2">Kind</th>
            {showFormula && <th scope="col" className="px-3 py-2">Used in</th>}
            <th scope="col" className="px-3 py-2">What it does</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((c) => (
            <tr key={c.name} className="border-t-2 border-ink/10 align-top">
              <td className="px-3 py-2.5 font-bold text-ink" style={{ fontFamily: 'var(--font-pixel)', fontSize: '1.15rem' }}>
                {c.name}
              </td>
              <td className="px-3 py-2.5 text-right">
                <span className="mx-const">{fmtNum(c.value)}</span>
              </td>
              <td className="px-3 py-2.5">
                <Tag>{titleCase(c.kind)}</Tag>
              </td>
              {showFormula && (
                <td className="px-3 py-2.5 text-sm text-ink-soft" style={{ fontFamily: 'var(--font-pixel)', fontSize: '1.05rem' }}>
                  {c.formulaKey ?? '—'}
                </td>
              )}
              <td className="px-3 py-2.5 text-sm text-ink-soft">{c.description ?? '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** A single expandable formula card; fetches its detail lazily once opened. */
function FormulaCard({ f }: { f: FormulaSummary }) {
  const [open, setOpen] = useState(false)
  const { data, loading, error, reload } = useApi<FormulaDetail>(open ? `/api/mechanics/formulas/${f.key}` : null)
  const panelId = `formula-${f.key}`
  const gloss = FORMULA_GLOSS[f.key]

  return (
    <div className="pixel-panel p-4">
      <button
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-controls={panelId}
        className="flex w-full items-start justify-between gap-3 text-left"
      >
        <span className="min-w-0">
          <span className="block text-base font-extrabold text-ink">{f.name}</span>
          {f.signature && (
            <span
              className="mt-1 block break-words text-ink-mute"
              style={{ fontFamily: 'var(--font-pixel)', fontSize: '1.05rem' }}
            >
              {f.signature}
            </span>
          )}
        </span>
        <span className="flex shrink-0 items-center gap-2">
          <ConfidenceChip confidence={f.confidence} />
          <span className="text-lg text-ink-mute" aria-hidden="true">
            {open ? '▲' : '▼'}
          </span>
        </span>
      </button>

      {!open && gloss && <p className="mx-prose mt-2">{gloss}</p>}

      {open && (
        <div id={panelId} className="mt-4 flex flex-col gap-4 border-t-2 border-ink/10 pt-4">
          {error ? (
            <ErrorState message={error} onRetry={reload} />
          ) : loading || !data ? (
            <Skeleton className="h-24 w-full" />
          ) : (
            <>
              {gloss && <p className="mx-prose">{gloss}</p>}
              <FormulaExpr expression={data.expression} />
              {data.description && <p className="mx-prose">{data.description}</p>}
              {data.constants?.length > 0 && (
                <div className="flex flex-col gap-2">
                  <p className="text-xs uppercase tracking-wide text-ink-mute">Constants in this formula</p>
                  <ConstantsTable rows={data.constants} />
                </div>
              )}
              {data.sourceFile && (
                <p className="text-xs uppercase tracking-wide text-ink-mute">
                  Source:{' '}
                  <span style={{ fontFamily: 'var(--font-pixel)', fontSize: '1rem' }}>{data.sourceFile}</span>
                </p>
              )}
            </>
          )}
        </div>
      )}
    </div>
  )
}

/** XP curve table — names + recovered expression per type (no fabricated EXP). */
function XpCurveTable({
  curves,
}: {
  curves: { curveType: number; name?: string | null; kind?: string | null }[]
}) {
  const expr: Record<number, string> = {
    0: 'AC(L)·1.2 − AC(L)·15 + 100·L − 140',
    1: 'AC(L)',
    2: 'banded: ≤Lv49, Lv50–67, Lv68–97, ≥Lv98 (scalars /50, /100, /500)',
    3: 'banded: <Lv15, Lv15–35, ≥Lv36 (scalar /50)',
    4: 'AC(L) × 0.8',
    5: 'AC(L) × 1.25',
  }
  return (
    <div className="pixel-panel overflow-x-auto p-1">
      <table className="w-full min-w-[520px] border-collapse text-left">
        <thead>
          <tr className="text-xs uppercase tracking-wide text-ink-mute">
            <th scope="col" className="px-3 py-2">Curve</th>
            <th scope="col" className="px-3 py-2">Kind</th>
            <th scope="col" className="px-3 py-2">Recovered transform</th>
          </tr>
        </thead>
        <tbody>
          {curves.map((c) => (
            <tr key={c.curveType} className="border-t-2 border-ink/10 align-top">
              <td className="px-3 py-2.5 font-extrabold text-ink">{c.name ?? `Curve ${c.curveType}`}</td>
              <td className="px-3 py-2.5">{c.kind ? <Tag>{titleCase(c.kind)}</Tag> : '—'}</td>
              <td
                className="px-3 py-2.5 text-ink-soft"
                style={{ fontFamily: 'var(--font-pixel)', fontSize: '1.1rem' }}
              >
                {expr[c.curveType] ?? '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default function Mechanics() {
  const { data, loading, error, reload } = useApi<MechanicsOverview>('/api/mechanics')
  const constants = useApi<MechConstant[]>('/api/mechanics/constants')

  return (
    <div className="flex flex-col gap-10">
      <header>
        <h1 className="flex items-center gap-2 text-2xl text-cream text-pixel-shadow">
          <IconCog className="text-2xl" />
          Mechanics
        </h1>
        <p className="mt-2 max-w-2xl text-base text-cream/70">
          The battle and progression math behind LumenTale, recovered from the game’s code. Each formula shows
          its plain-English meaning, the exact recovered expression, and the tuning constants it uses.
        </p>
      </header>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading || !data ? (
        <div className="flex flex-col gap-4">
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-64 w-full" />
        </div>
      ) : (
        <>
          {/* CONFIDENCE LEGEND */}
          <section className="pixel-panel p-4">
            <p className="mb-3 text-sm font-bold text-ink">How confident are these?</p>
            <ConfidenceLegend />
          </section>

          {/* FORMULAS */}
          <section className="flex flex-col gap-4">
            <SectionHeading icon={<IconCog />}>Battle &amp; progression formulas</SectionHeading>
            <p className="text-sm text-cream/70">Tap a formula to expand its expression and constants.</p>
            {data.formulas.length === 0 ? (
              <EmptyState title="No formulas recovered." />
            ) : (
              <div className="flex flex-col gap-4">
                {data.formulas.map((f) => (
                  <FormulaCard key={f.key} f={f} />
                ))}
              </div>
            )}
          </section>

          {/* DIFFICULTY — comparison table */}
          <section className="flex flex-col gap-4">
            <SectionHeading>Difficulty damage scaling</SectionHeading>
            <p className="mx-prose max-w-2xl">
              A global scalar applied to damage based on the chosen difficulty. <strong>Normal</strong> is unchanged
              (×1.0 both ways); <strong>Easy</strong> boosts the player and softens enemy hits;{' '}
              <strong>Hard</strong> makes incoming damage hit harder.
            </p>
            <div className="pixel-panel overflow-x-auto p-1">
              <table className="w-full min-w-[460px] border-collapse text-left">
                <thead>
                  <tr className="text-xs uppercase tracking-wide text-ink-mute">
                    <th scope="col" className="px-3 py-2">Difficulty</th>
                    <th scope="col" className="px-3 py-2 text-right">Player → Enemy</th>
                    <th scope="col" className="px-3 py-2 text-right">Enemy → Player</th>
                  </tr>
                </thead>
                <tbody>
                  {(() => {
                    const find = (diff: string, dir: string) =>
                      data.difficulty.find(
                        (d) => d.difficulty.toUpperCase() === diff && d.direction === dir,
                      )?.multiplier
                    const mult = (v?: number) => (v == null ? '×1.0' : `×${v}`)
                    const isBuff = (v?: number) => v != null && v > 1
                    const isNerf = (v?: number) => v != null && v < 1
                    const cell = (v?: number) => (
                      <span
                        className="mx-const"
                        style={{
                          color: isBuff(v)
                            ? 'var(--color-emo-furor)'
                            : isNerf(v)
                              ? 'var(--color-lumen-deep)'
                              : 'var(--color-ink-soft)',
                        }}
                      >
                        {mult(v)}
                      </span>
                    )
                    const rows: { label: string; out?: number; inc?: number }[] = [
                      { label: 'Easy', out: find('EASY', 'player_out'), inc: find('EASY', 'enemy_out') },
                      { label: 'Normal', out: undefined, inc: undefined },
                      { label: 'Hard', out: find('HARD', 'player_out'), inc: find('HARD', 'enemy_out') },
                    ]
                    return rows.map((r) => (
                      <tr key={r.label} className="border-t-2 border-ink/10">
                        <td className="px-3 py-2.5 text-base font-extrabold text-ink">{r.label}</td>
                        <td className="px-3 py-2.5 text-right">{cell(r.out)}</td>
                        <td className="px-3 py-2.5 text-right">{cell(r.inc)}</td>
                      </tr>
                    ))
                  })()}
                </tbody>
              </table>
            </div>
          </section>

          {/* XP CURVES */}
          <section className="flex flex-col gap-4">
            <SectionHeading>Experience curves</SectionHeading>
            <p className="mx-prose max-w-2xl">
              Creatures grow on one of six EXP curves. Some are closed-form polynomials; the rest are
              designer-authored AnimationCurve data sampled per level (written below as{' '}
              <span style={{ fontFamily: 'var(--font-pixel)', fontSize: '1.05rem' }}>AC(L)</span>).
            </p>
            {data.xpCurves.length === 0 ? (
              <EmptyState title="No XP curves." />
            ) : (
              <>
                <XpCurveChart />
                <XpCurveTable curves={data.xpCurves} />
              </>
            )}
            <p className="pixel-panel flex items-start gap-2 p-3 text-sm text-ink-soft">
              <IconInfo className="mt-0.5 shrink-0 text-lumen-deep" aria-hidden="true" />
              <span>
                Per-level EXP values were <strong>not extracted</strong>: the shared AnimationCurve base sampler
                couldn’t be resolved to a closed form, so the actual numbers aren’t served (rather than
                fabricated). The curve <em>transforms</em> above are recovered exactly; the chart shows their relative
                shapes only.
              </span>
            </p>
          </section>

          {/* ALL CONSTANTS */}
          <section className="flex flex-col gap-4">
            <SectionHeading>All tuning constants</SectionHeading>
            <p className="text-sm text-cream/70">
              Every numeric constant pulled from the recovered formulas, with the formula it belongs to.
            </p>
            {constants.error ? (
              <ErrorState message={constants.error} onRetry={constants.reload} />
            ) : constants.loading || !constants.data ? (
              <Skeleton className="h-48 w-full" />
            ) : constants.data.length === 0 ? (
              <EmptyState title="No constants recorded." />
            ) : (
              <ConstantsTable rows={constants.data} showFormula />
            )}
          </section>

          {/* ADDITIONAL MECHANICS — curated from FORMULAS.md / StatGradeService */}
          <section className="flex flex-col gap-4">
            <SectionHeading icon={<IconInfo />}>How the wiki derives some figures</SectionHeading>
            <div className="grid gap-4 md:grid-cols-2">
              <div className="pixel-panel p-4">
                <h3 className="text-base font-extrabold text-ink">Turn order</h3>
                <p className="mx-prose mt-2">
                  Battlers act in order of their <strong>Speed points</strong>: the engine sorts the living combatants
                  by speed each turn. When two are tied, a dedicated tie-breaker resolves the order before they take
                  their action.
                </p>
                <p className="mt-2 text-xs uppercase tracking-wide text-ink-mute">
                  Source: BattleActionOrder · Battle.SolveTie (decompiled)
                </p>
              </div>
              <div className="pixel-panel p-4">
                <h3 className="text-base font-extrabold text-ink">Stat grades (S–F)</h3>
                <p className="mx-prose mt-2">
                  The letter grades on creature pages are <strong>population percentiles</strong>, not the game’s
                  own grades: each base stat is ranked against every creature’s same stat. Roughly{' '}
                  <strong>S</strong> = top 10%, <strong>A</strong> = ~top 28%, <strong>B</strong> = top half,{' '}
                  <strong>C</strong>/<strong>D</strong>/<strong>F</strong> below.
                </p>
                <p className="mt-2 text-xs uppercase tracking-wide text-ink-mute">Wiki-derived (StatGradeService)</p>
              </div>
              <div className="pixel-panel p-4">
                <h3 className="text-base font-extrabold text-ink">Catching</h3>
                <p className="mx-prose mt-2">
                  There is no single catch formula. A running catch rate is adjusted at runtime by every active
                  modifier — <strong>bilia</strong> (capture-item) effects, which can add, scale, or key off a
                  target’s attributes, plus <strong>camp buffs</strong> that boost the rate for a limited time.
                </p>
                <p className="mt-2 text-xs uppercase tracking-wide text-ink-mute">
                  Source: GameEventMaster.OnCatchrateCalculation (decompiled)
                </p>
              </div>
              <div className="pixel-panel p-4">
                <h3 className="text-base font-extrabold text-ink">Emotion vs. element</h3>
                <p className="mx-prose mt-2">
                  Two effectiveness axes stack. The <strong>elemental</strong> matchup comes from each form’s
                  per-creature weakness data; the <strong>emotion</strong> matchup is the ×1.2 / ×0.8 / ×1.0
                  multiplier above. Both are multiplied into the final damage.
                </p>
                <p className="mt-2 text-xs uppercase tracking-wide text-ink-mute">
                  Source: BattleMath.GetEmotionalTypeEffectivenessMultiplier
                </p>
              </div>
            </div>
          </section>
        </>
      )}
    </div>
  )
}
