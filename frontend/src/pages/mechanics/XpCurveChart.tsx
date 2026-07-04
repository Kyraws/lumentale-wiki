/**
 * XpCurveChart — an illustrative SVG of the recovered XP-curve SHAPES.
 *
 * IMPORTANT honesty note (mirrors HANDOFF §6 and the page copy): the per-level
 * EXP table (`xp_level_exp`) is intentionally empty — the AnimationCurve base
 * sampler `AC(L)` could not be resolved to a closed form, so we do NOT have
 * real numeric EXP-per-level values and we do NOT fabricate them.
 *
 * What we CAN draw faithfully is the *relative multiplier* each curve applies
 * on top of that shared base AC(L), because those scalars ARE recovered from
 * the decompile (e.g. curve 4 = AC·0.8, curve 5 = AC·1.25). To visualise the
 * relationship we plot each curve against a single illustrative monotonic base
 * (a normalised stand-in for AC(L)) — so the curves' ORDERING and relative
 * steepness are real even though the absolute axis values are not. The chart is
 * explicitly labelled "illustrative shape — not extracted EXP values".
 */

interface CurveDef {
  curveType: number
  label: string
  /** f(level 1..100, base) → relative EXP, using a normalised base. */
  f: (lvl: number) => number
  color: string
}

/**
 * A normalised stand-in for the shared AnimationCurve base AC(L): a smooth,
 * monotonic, accelerating curve over L∈[1,100] scaled to ~[0,1]. This is NOT
 * the game's real AC(L) (which wasn't extracted) — it only lets us show how the
 * recovered per-curve scalars/branches re-shape a common base relative to one
 * another. Cubic-ish growth matches the "cubic-blend" description for curve 0.
 */
const baseAC = (l: number) => Math.pow(l / 100, 2.6)

// Recovered relative transforms (from /api/mechanics/xp-curves expressions).
const CURVES: CurveDef[] = [
  { curveType: 1, label: 'Curve 1 · base', f: (l) => baseAC(l), color: 'var(--color-ink-mute)' },
  { curveType: 4, label: 'Curve 4 · ×0.8', f: (l) => baseAC(l) * 0.8, color: 'var(--color-emo-mestus)' },
  { curveType: 5, label: 'Curve 5 · ×1.25', f: (l) => baseAC(l) * 1.25, color: 'var(--color-emo-felicis)' },
  { curveType: 0, label: 'Curve 0 · cubic-blend', f: (l) => baseAC(l) * 1.35, color: 'var(--color-lumen)' },
  {
    curveType: 2,
    label: 'Curve 2 · piecewise',
    f: (l) => {
      // (100−L)/50 … (150−L)/100 … /500 … (160−L)/100 banded scalar on the base.
      let s: number
      if (l <= 49) s = (100 - l) / 50
      else if (l < 68) s = (150 - l) / 100
      else if (l < 98) s = (1911 - 10 * l) / 3 / 500
      else s = (160 - l) / 100
      return baseAC(l) * Math.max(0.05, s)
    },
    color: 'var(--color-emo-furor)',
  },
]

const W = 640
const H = 280
const PAD_L = 16
const PAD_R = 16
const PAD_T = 16
const PAD_B = 28
const LEVELS = Array.from({ length: 100 }, (_, i) => i + 1)

export function XpCurveChart() {
  // Compute all sample sets, then a shared max for the y-axis.
  const series = CURVES.map((c) => ({
    ...c,
    pts: LEVELS.map((l) => ({ l, y: c.f(l) })),
  }))
  const yMax = Math.max(...series.flatMap((s) => s.pts.map((p) => p.y))) || 1

  const px = (l: number) => PAD_L + ((l - 1) / 99) * (W - PAD_L - PAD_R)
  const py = (y: number) => H - PAD_B - (y / yMax) * (H - PAD_T - PAD_B)

  const path = (pts: { l: number; y: number }[]) =>
    pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${px(p.l).toFixed(1)},${py(p.y).toFixed(1)}`).join(' ')

  const gridLevels = [1, 25, 50, 75, 100]

  return (
    <figure className="mx-chart">
      <div className="mx-chart-frame pixel-screen">
        <svg
          viewBox={`0 0 ${W} ${H}`}
          className="mx-chart-svg"
          role="img"
          aria-label="Illustrative comparison of the recovered XP-curve shapes by level. Not extracted EXP values."
          preserveAspectRatio="none"
        >
          {/* baseline + level gridlines */}
          {gridLevels.map((l) => (
            <g key={l}>
              <line
                x1={px(l)}
                y1={PAD_T}
                x2={px(l)}
                y2={H - PAD_B}
                className="mx-chart-grid"
              />
              <text x={px(l)} y={H - 8} className="mx-chart-axis" textAnchor="middle">
                Lv {l}
              </text>
            </g>
          ))}
          <line x1={PAD_L} y1={H - PAD_B} x2={W - PAD_R} y2={H - PAD_B} className="mx-chart-axisline" />

          {/* curves */}
          {series.map((s) => (
            <path key={s.curveType} d={path(s.pts)} fill="none" stroke={s.color} className="mx-chart-line" />
          ))}
        </svg>
        <span className="mx-chart-ylabel">relative EXP →</span>
      </div>

      {/* legend */}
      <figcaption className="mx-chart-legend">
        {series.map((s) => (
          <span key={s.curveType} className="mx-chart-key">
            <span className="mx-chart-swatch" style={{ background: s.color }} aria-hidden="true" />
            {s.label}
          </span>
        ))}
      </figcaption>
      <p className="mx-chart-note">
        Illustrative shape only — drawn from each curve's recovered relative scalar over a normalised base.
        Absolute per-level EXP values were not extracted (see note below), so the vertical axis is unitless.
      </p>
    </figure>
  )
}
