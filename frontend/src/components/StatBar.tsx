import type { StatGrade } from '../lib/types'
import { gradeColor, STAT_FULL } from '../lib/game'

/** A single stat row: label, grade badge, base value, segmented meter, percentile rank. */
export function StatBar({ s, value }: { s: StatGrade; value?: number }) {
  return (
    <div className="grid grid-cols-[3rem_2rem_2.75rem_1fr] items-center gap-2">
      <span
        className="text-[0.85rem] font-extrabold text-ink-soft"
        title={STAT_FULL[s.stat] ?? s.stat}
      >
        {s.stat}
      </span>
      <span
        className="flex h-6 w-6 items-center justify-center rounded-[2px] border-2 border-ink/70 text-xs font-extrabold text-white"
        style={{ backgroundColor: gradeColor(s.grade) }}
        aria-label={`grade ${s.grade}`}
      >
        {s.grade}
      </span>
      <span
        className="text-right text-lg font-extrabold text-ink"
        style={{ fontFamily: 'var(--font-pixel)' }}
        title="Base value"
      >
        {value ?? '—'}
      </span>
      <div className="flex items-center gap-2">
        <div className="stat-track h-4 flex-1">
          <div
            className="h-full"
            style={{
              width: `${Math.max(4, s.pct)}%`,
              backgroundColor: gradeColor(s.grade),
              transition: 'width 400ms ease-out',
            }}
          />
        </div>
        {s.rank != null && (
          <span className="w-12 text-right text-xs font-bold text-ink-mute">#{s.rank}</span>
        )}
      </div>
    </div>
  )
}

export function StatBlock({ grades, values }: { grades: StatGrade[]; values?: number[] }) {
  return (
    <div className="flex flex-col gap-2">
      {grades.map((g, i) => (
        <StatBar key={g.stat} s={g} value={values?.[i]} />
      ))}
    </div>
  )
}
