import { Link, useParams } from 'react-router-dom'
import { useApi } from '../lib/api'
import Sprite from '../components/Sprite'
import { ErrorState, Skeleton } from '../components/States'
import { IconBack, IconQuest, IconCamp, IconStar } from '../components/Icons'
import { fmtNum } from '../lib/game'
import './camps-squadrons.css'

interface CampTarget {
  formGuid: string
  species: string
  variant: string
  menuArt?: string | null
}

interface CampTask {
  questGuid: string
  questName: string
}

interface CampDetailData {
  guid: string
  name?: string | null
  displayName?: string | null
  region?: string | null
  area?: string | null
  effectClass?: string | null
  effectLabel?: string | null
  effectDescription?: string | null
  effectText?: string | null
  effectDuration?: number
  effectIncrement?: number
  influence?: number
  lumenAmount?: number
  targets: CampTarget[]
  tasks: CampTask[]
}

function fmtDuration(s?: number): string | null {
  if (s == null) return null
  if (s < 60) return `${s}s`
  const m = Math.floor(s / 60)
  const rem = s % 60
  return rem ? `${m}m ${rem}s` : `${m} min`
}

function pct(inc?: number): string | null {
  if (inc == null) return null
  const v = inc * 100
  return Number.isInteger(v) ? `${v}%` : `${v.toFixed(1)}%`
}

export default function CampDetail() {
  const { guid = '' } = useParams()
  const { data, loading, error, reload } = useApi<CampDetailData>(`/api/camps/${guid}`)

  if (error) return <ErrorState message={error} onRetry={reload} />
  if (loading || !data) return <Skeleton className="h-72 w-full" />

  const duration = fmtDuration(data.effectDuration)
  const increment = pct(data.effectIncrement)

  return (
    <div className="flex flex-col gap-5">
      <Link to="/camps" className="inline-flex w-fit items-center gap-1 text-sm font-bold text-cream/70 hover:text-cream">
        <IconBack /> Back to Camps
      </Link>

      {/* Hero */}
      <section
        className="camp-accent dialog-box flex flex-wrap items-center gap-5 p-5 md:p-7"
        data-effect={data.effectClass ?? ''}
      >
        <div
          className="pixel-screen flex h-24 w-24 shrink-0 items-center justify-center p-2 text-5xl"
          style={{ color: 'var(--camp-tint)' }}
        >
          <IconCamp />
        </div>
        <div className="min-w-0 flex-1">
          <h1 className="text-xl text-cream text-pixel-shadow md:text-2xl">
            {data.displayName || data.name || 'Lumen Camp'}
          </h1>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            {data.region && (
              <span className="region-pill" data-region={data.region}>
                {data.region === 'Center' ? '✦ ' : ''}
                {data.region} {data.region !== 'Talea' ? 'Route' : ''}
              </span>
            )}
            {data.area && <span className="text-xs font-bold text-cream/70">{data.area}</span>}
            {data.effectLabel && (
              <span className="camp-effect-chip">
                {data.effectLabel}
                {increment && <span className="text-cream/90">+{increment.replace('%', '')}%</span>}
              </span>
            )}
          </div>
          {data.name && data.name !== data.displayName && (
            <p className="mt-2 text-[0.65rem] font-bold uppercase tracking-wide text-cream/40">
              Internal: {data.name}
            </p>
          )}
        </div>
      </section>

      {/* Effect explainer */}
      <section className="camp-accent pixel-panel flex flex-col gap-4 p-4 md:p-5" data-effect={data.effectClass ?? ''}>
        <h2 className="inline-block w-fit rounded-[2px] bg-ink px-2 py-1 text-[0.7rem] text-cream" style={{ fontFamily: 'var(--font-display)' }}>
          Conquest Bonus
        </h2>
        {data.effectText && (
          <p className="text-sm font-bold leading-relaxed text-ink">{data.effectText}</p>
        )}
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {increment && (
            <div className="camp-stat">
              <span className="v">+{increment.replace('%', '')}%</span>
              <span className="k">Bonus</span>
            </div>
          )}
          {duration && (
            <div className="camp-stat">
              <span className="v">{duration}</span>
              <span className="k">Duration</span>
            </div>
          )}
          {data.lumenAmount != null && (
            <div className="camp-stat">
              <span className="v">{fmtNum(data.lumenAmount)}</span>
              <span className="k">Lumen to take</span>
            </div>
          )}
          {data.influence != null && data.influence > 0 && (
            <div className="camp-stat">
              <span className="v">{fmtNum(data.influence)}</span>
              <span className="k">Influence</span>
            </div>
          )}
        </div>
        <p className="text-xs leading-relaxed text-ink-mute">
          The bonus is active while you are near the camp. Leave Lumen behind to defend it and hold the camp
          longer while you roam — the stronger they are, the longer it stays yours before a rival squadron
          retakes it.
        </p>
      </section>

      {/* Targets */}
      <section className="pixel-panel p-4 md:p-5">
        <h2 className="mb-1 inline-block rounded-[2px] bg-ink px-2 py-1 text-[0.7rem] text-cream" style={{ fontFamily: 'var(--font-display)' }}>
          Bounty Targets ({data.targets.length})
        </h2>
        <p className="mb-4 text-xs text-ink-mute">Animon the camp's bounty and info tasks ask you to track down.</p>
        {data.targets.length === 0 ? (
          <p className="text-sm text-ink-mute">No target creatures for this camp.</p>
        ) : (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {data.targets.map((t) => (
              <Link
                key={t.formGuid}
                to={`/dex/${t.formGuid}`}
                className="flex items-center gap-3 rounded-[2px] border-2 border-ink/15 bg-ink/5 p-3 transition-transform hover:-translate-y-0.5 hover:bg-ink/10"
              >
                <div className="pixel-screen flex h-16 w-16 shrink-0 items-center justify-center p-1">
                  <Sprite src={t.menuArt} alt={t.species} size={56} />
                </div>
                <div className="min-w-0 flex-1">
                  <span className="line-clamp-1 text-sm font-extrabold text-ink">{t.species}</span>
                  {t.variant && t.variant !== 'Base Form' && (
                    <span className="block text-[0.65rem] font-bold text-ink-mute">{t.variant}</span>
                  )}
                </div>
              </Link>
            ))}
          </div>
        )}
      </section>

      {/* Tasks */}
      {data.tasks.length > 0 && (
        <section className="pixel-panel p-4 md:p-5">
          <h2 className="mb-1 inline-block rounded-[2px] bg-ink px-2 py-1 text-[0.7rem] text-cream" style={{ fontFamily: 'var(--font-display)' }}>
            Tasks ({data.tasks.length})
          </h2>
          <p className="mb-4 text-xs text-ink-mute">
            Townsfolk near a conquered camp offer these. Each rewards Lumen and a fixed payout.
          </p>
          <ul className="flex flex-col gap-2">
            {data.tasks.map((task) => (
              <li key={task.questGuid}>
                <Link
                  to={`/quests/${task.questGuid}`}
                  className="flex items-center gap-2 rounded-[2px] bg-ink/5 px-3 py-2 text-sm font-bold text-ink transition-colors hover:bg-ink/10"
                >
                  <IconQuest className="shrink-0 text-ink-soft" />
                  {/* "{target}" is the game's runtime placeholder for the rolled bounty Animon */}
                  <span className="truncate">{task.questName?.replace('{target}', 'a rolled bounty target')}</span>
                  <IconStar className="ml-auto text-[0.8em] text-gold-deep" />
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}
