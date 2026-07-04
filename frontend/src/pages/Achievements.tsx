import { useMemo, useState } from 'react'
import { useApi } from '../lib/api'
import type { AchievementSummary } from '../lib/types'
import Sprite from '../components/Sprite'
import { Tag } from '../components/Badge'
import { GridSkeleton, ErrorState, EmptyState, Skeleton } from '../components/States'
import { IconTrophy } from '../components/Icons'
import { rawStr, rawNum, titleCase, fmtNum } from '../lib/game'

// Rarity tints A→D (Champion is the rarest). Color + label — never color alone.
const RARITY_TINT: Record<string, string> = {
  Common: 'var(--color-ink-mute)',
  Rare: 'var(--color-sky)',
  Elite: 'var(--color-el-aura)',
  Champion: 'var(--color-gold)',
}

// Stable rarity ordering for the filter row.
const RARITY_ORDER = ['Common', 'Rare', 'Elite', 'Champion']

export default function Achievements() {
  const { data, loading, error, reload } = useApi<AchievementSummary[]>('/api/achievements')
  const [rarity, setRarity] = useState<string | null>(null)

  const rarities = useMemo(
    () =>
      data
        ? [...new Set(data.map((a) => a.rarity).filter(Boolean) as string[])].sort(
            (x, y) => RARITY_ORDER.indexOf(x) - RARITY_ORDER.indexOf(y),
          )
        : [],
    [data],
  )

  const rows = useMemo(
    () => (data ? data.filter((a) => !rarity || a.rarity === rarity) : []),
    [data, rarity],
  )

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="text-lg text-cream text-pixel-shadow">Achievements</h1>
        <p className="mt-1 text-sm text-cream/60">
          {data ? `${rows.length} of ${data.length} achievements` : 'Polishing the trophy case…'}
        </p>
      </header>

      {rarities.length > 0 && (
        <div className="dialog-box flex flex-wrap items-center gap-2 p-4">
          <span className="mr-1 text-[0.6rem] uppercase tracking-wide text-cream/50">Rarity</span>
          {rarities.map((r) => (
            <button
              key={r}
              onClick={() => setRarity(rarity === r ? null : r)}
              aria-pressed={rarity === r}
              className="type-chip transition-transform hover:-translate-y-0.5"
              style={{
                backgroundColor: RARITY_TINT[r] ?? 'var(--color-ink-mute)',
                opacity: !rarity || rarity === r ? 1 : 0.4,
                boxShadow: rarity === r ? '0 0 0 2px var(--color-gold), 2px 2px 0 0 rgba(0,0,0,0.3)' : undefined,
              }}
            >
              {r}
            </button>
          ))}
        </div>
      )}

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading ? (
        <GridSkeleton count={12} />
      ) : rows.length === 0 ? (
        <EmptyState title="No achievements match." hint="Try clearing the rarity filter." />
      ) : (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {rows.map((a, i) => (
            <AchievementCard key={a.guid} a={a} index={i} />
          ))}
        </div>
      )}
    </div>
  )
}

function AchievementCard({ a, index }: { a: AchievementSummary; index: number }) {
  const [open, setOpen] = useState(false)
  // Lazily fetch the raw detail record only once the row is expanded.
  const { data, loading } = useApi<Record<string, unknown>>(open ? `/api/achievements/${a.guid}` : null)
  const panelId = `ach-${a.guid}`

  // Raw record keeps the game's original PascalCase keys; read defensively.
  const desc = data ? rawStr(data, 'Description') : undefined
  const internalId = data ? rawStr(data, 'Internal_ID') : undefined
  const storeId = data ? rawStr(data, 'StoreID') : undefined
  const visibility = data ? rawNum(data, 'VisibilityType') : undefined

  return (
    <div className="pixel-panel anim-pop overflow-hidden" style={{ animationDelay: `${Math.min(index, 16) * 16}ms` }}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-controls={panelId}
        className="flex w-full items-center gap-3 p-3 text-left transition-colors hover:bg-ink/5"
      >
        <div className="pixel-screen flex h-14 w-14 shrink-0 items-center justify-center p-1 text-lumen-deep">
          {a.icon ? (
            <Sprite src={a.icon} alt="" size={48} />
          ) : (
            <IconTrophy className="text-2xl" />
          )}
        </div>
        <div className="min-w-0 flex-1">
          <span className="line-clamp-2 text-sm font-extrabold leading-snug text-ink">
            {a.name || 'Untitled achievement'}
          </span>
          <span className="mt-1 flex flex-wrap items-center gap-1.5">
            {a.rarity && (
              <span
                className="type-chip"
                style={{ backgroundColor: RARITY_TINT[a.rarity] ?? 'var(--color-ink-mute)', fontSize: '0.42rem' }}
              >
                {a.rarity}
              </span>
            )}
            {a.steps != null && a.steps > 1 && <Tag>{a.steps} steps</Tag>}
          </span>
        </div>
        <span className="shrink-0 text-lg font-black text-ink-mute" aria-hidden="true">
          {open ? '−' : '+'}
        </span>
      </button>

      {open && (
        <div id={panelId} className="border-t-2 border-ink/10 px-3 pb-3 pt-3">
          {loading || !data ? (
            <Skeleton className="h-16 w-full" />
          ) : (
            <div className="flex flex-col gap-2 text-sm text-ink-soft">
              {desc ? (
                <p className="leading-relaxed">{desc}</p>
              ) : (
                <p className="italic text-ink-mute">No description recorded.</p>
              )}
              <dl className="mt-1 flex flex-wrap gap-x-6 gap-y-1 text-xs">
                {a.steps != null && (
                  <Field label="Steps" value={fmtNum(a.steps)} />
                )}
                {internalId && <Field label="ID" value={internalId} />}
                {storeId && <Field label="Store ID" value={storeId} />}
                {visibility != null && (
                  <Field label="Visibility" value={visibility === 0 ? 'Visible' : 'Hidden'} />
                )}
              </dl>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="font-bold uppercase tracking-wide text-ink-mute">{titleCase(label)}</dt>
      <dd className="font-extrabold text-ink">{value}</dd>
    </div>
  )
}
