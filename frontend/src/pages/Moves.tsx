import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { MoveSummary } from '../lib/types'
import { TypeBadge } from '../components/Badge'
import { Skeleton, ErrorState, EmptyState } from '../components/States'
import { IconSearch } from '../components/Icons'
import { ALL_ELEMENTS, elementColor, MOVE_CATEGORY, SPREAD } from '../lib/game'

type SortKey = 'name' | 'power' | 'accuracy' | 'cost' | 'learners'

export default function Moves() {
  const { data, loading, error, reload } = useApi<MoveSummary[]>('/api/moves')
  const [q, setQ] = useState('')
  const [cat, setCat] = useState<string | null>(null)
  const [ele, setEle] = useState<string | null>(null)
  const [aoe, setAoe] = useState<string | null>(null)
  const [sort, setSort] = useState<{ key: SortKey; dir: 1 | -1 }>({ key: 'name', dir: 1 })

  const rows = useMemo(() => {
    if (!data) return []
    const needle = q.trim().toLowerCase()
    const out = data.filter((m) => {
      if (m.system) return false // internal DoT/EoT ticks, charge stages, dev tests
      if (cat && m.category !== cat) return false
      if (ele && m.type !== ele) return false
      if (aoe && m.aoe !== aoe) return false
      if (needle && !m.name.toLowerCase().includes(needle)) return false
      return true
    })
    const { key, dir } = sort
    out.sort((a, b) => {
      if (key === 'name') return a.name.localeCompare(b.name) * dir
      return ((a[key] ?? 0) - (b[key] ?? 0)) * dir
    })
    return out
  }, [data, q, cat, ele, aoe, sort])

  const toggleSort = (key: SortKey) =>
    setSort((s) => (s.key === key ? { key, dir: (s.dir * -1) as 1 | -1 } : { key, dir: key === 'name' ? 1 : -1 }))

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="text-lg text-cream text-pixel-shadow">Moves</h1>
        <p className="mt-1 text-sm text-cream/60">
          {data ? `${rows.length} of ${data.filter((m) => !m.system).length} moves` : 'Loading the move list…'}
        </p>
      </header>

      <div className="dialog-box flex flex-col gap-4 p-4">
        <label className="relative block">
          <span className="sr-only">Search moves</span>
          <IconSearch className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-lg text-ink-mute" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            type="search"
            placeholder="Search moves…"
            className="w-full rounded-[2px] border-ink bg-parch py-2.5 pl-10 pr-3 text-sm font-bold text-ink placeholder:text-ink-mute/70 focus:outline-none"
            style={{ borderWidth: 3 }}
          />
        </label>
        <div className="flex flex-wrap items-center gap-2">
          <span className="mr-1 text-[0.6rem] uppercase tracking-wide text-cream/50">Class</span>
          {Object.keys(MOVE_CATEGORY).map((c) => (
            <button
              key={c}
              onClick={() => setCat(cat === c ? null : c)}
              aria-pressed={cat === c}
              className="type-chip transition-transform hover:-translate-y-0.5"
              style={{
                backgroundColor: MOVE_CATEGORY[c].tint,
                opacity: !cat || cat === c ? 1 : 0.4,
                boxShadow: cat === c ? '0 0 0 2px var(--color-gold), 2px 2px 0 0 rgba(0,0,0,0.3)' : undefined,
              }}
            >
              {MOVE_CATEGORY[c].label}
            </button>
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="mr-1 text-[0.6rem] uppercase tracking-wide text-cream/50">Spread</span>
          {Object.entries(SPREAD).map(([code, label]) => (
            <button
              key={code}
              onClick={() => setAoe(aoe === code ? null : code)}
              aria-pressed={aoe === code}
              className={`rounded-[2px] border-2 px-3 py-1.5 text-[0.6rem] font-extrabold uppercase tracking-wide transition-colors ${
                aoe === code
                  ? 'border-gold bg-gold/25 text-gold'
                  : 'border-cream/15 text-cream/60 hover:bg-white/5'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="mr-1 w-14 text-[0.6rem] uppercase tracking-wide text-cream/50">Type</span>
          {ALL_ELEMENTS.map((o) => (
            <button
              key={o}
              onClick={() => setEle(ele === o ? null : o)}
              aria-pressed={ele === o}
              className="type-chip transition-transform hover:-translate-y-0.5"
              style={{
                backgroundColor: elementColor(o),
                opacity: !ele || ele === o ? 1 : 0.4,
                boxShadow: ele === o ? '0 0 0 2px var(--color-gold), 2px 2px 0 0 rgba(0,0,0,0.3)' : undefined,
              }}
            >
              {o}
            </button>
          ))}
        </div>
      </div>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading ? (
        <Skeleton className="h-96 w-full" />
      ) : rows.length === 0 ? (
        <EmptyState title="No moves match." hint="Try clearing a filter." />
      ) : (
        <div className="pixel-panel overflow-x-auto p-1">
          <table className="w-full min-w-[640px] border-collapse text-left">
            <thead>
              <tr className="text-[0.6rem] uppercase tracking-wide text-ink-mute">
                <Th onClick={() => toggleSort('name')} active={sort.key === 'name'} dir={sort.dir}>
                  Move
                </Th>
                <th className="px-3 py-2">Type</th>
                <th className="px-3 py-2">Class</th>
                <Th onClick={() => toggleSort('power')} active={sort.key === 'power'} dir={sort.dir} num>
                  Pow
                </Th>
                <Th onClick={() => toggleSort('accuracy')} active={sort.key === 'accuracy'} dir={sort.dir} num>
                  Acc
                </Th>
                <Th onClick={() => toggleSort('cost')} active={sort.key === 'cost'} dir={sort.dir} num>
                  SP
                </Th>
                <Th onClick={() => toggleSort('learners')} active={sort.key === 'learners'} dir={sort.dir} num>
                  Learners
                </Th>
              </tr>
            </thead>
            <tbody>
              {rows.map((m) => (
                <tr key={m.guid} className="border-t-2 border-ink/10 transition-colors hover:bg-ink/5">
                  <td className="px-3 py-2">
                    <Link to={`/moves/${m.guid}`} className="font-extrabold text-ink hover:text-lumen-deep">
                      {m.name}
                    </Link>
                  </td>
                  <td className="px-3 py-2">
                    <TypeBadge type={m.type} small />
                  </td>
                  <td className="px-3 py-2">
                    {m.category && (
                      <span
                        className="text-[0.6rem] font-extrabold uppercase"
                        style={{ color: MOVE_CATEGORY[m.category]?.tint ?? 'var(--color-ink-mute)' }}
                      >
                        {MOVE_CATEGORY[m.category]?.label ?? m.category}
                      </span>
                    )}
                  </td>
                  <Num>{m.power ? m.power : '—'}</Num>
                  <Num>{m.accuracy != null ? `${m.accuracy}%` : '—'}</Num>
                  <Num>{m.cost ?? '—'}</Num>
                  <Num>{m.learners}</Num>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function Th({
  children,
  onClick,
  active,
  dir,
  num,
}: {
  children: React.ReactNode
  onClick: () => void
  active: boolean
  dir: 1 | -1
  num?: boolean
}) {
  return (
    <th
      className={`px-3 py-2 ${num ? 'text-right' : ''}`}
      aria-sort={active ? (dir === 1 ? 'ascending' : 'descending') : 'none'}
    >
      <button onClick={onClick} className={`inline-flex items-center gap-1 hover:text-ink ${active ? 'text-lumen-deep' : ''}`}>
        {children}
        <span className="text-[0.7rem]">{active ? (dir === 1 ? '▲' : '▼') : '↕'}</span>
      </button>
    </th>
  )
}

function Num({ children }: { children: React.ReactNode }) {
  return (
    <td className="px-3 py-2 text-right text-sm font-bold text-ink-soft" style={{ fontFamily: 'var(--font-pixel)' }}>
      {children}
    </td>
  )
}
