import { useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useApi } from '../lib/api'
import Sprite from '../components/Sprite'
import { ErrorState, Skeleton } from '../components/States'
import { IconBack, IconSquadron, IconTrainer, IconStar } from '../components/Icons'
import { cleanTrainerName, fmtNum } from '../lib/game'
import './camps-squadrons.css'

interface SquadronMember {
  trainerGuid: string
  name: string
  isCodename?: boolean
  role: string
  ord: number
}

interface CampBoss {
  guid: string
  name: string
}

interface SquadronDetailData {
  guid: string
  name?: string | null
  rank?: number
  rankLabel?: string | null
  memberCount?: number
  logo?: string | null
  campBoss?: CampBoss | null
  members: SquadronMember[]
}

export default function SquadronDetail() {
  const { guid = '' } = useParams()
  const { data, loading, error, reload } = useApi<SquadronDetailData>(`/api/squadrons/${guid}`)

  const split = useMemo(() => {
    const named: SquadronMember[] = []
    const roster: SquadronMember[] = []
    for (const m of data?.members ?? []) {
      ;(m.isCodename ? roster : named).push(m)
    }
    const byOrd = (a: SquadronMember, b: SquadronMember) => a.ord - b.ord
    return { named: named.sort(byOrd), roster: roster.sort(byOrd) }
  }, [data])

  if (error) return <ErrorState message={error} onRetry={reload} />
  if (loading || !data) return <Skeleton className="h-72 w-full" />

  return (
    <div className="flex flex-col gap-5">
      <Link to="/squadrons" className="inline-flex w-fit items-center gap-1 text-sm font-bold text-cream/70 hover:text-cream">
        <IconBack /> Back to Squadrons
      </Link>

      {/* Hero */}
      <section className="dialog-box flex flex-wrap items-center gap-5 p-5 md:p-7">
        <div className="pixel-screen flex h-24 w-24 shrink-0 items-center justify-center p-2 text-4xl text-cream/70">
          {data.logo ? <Sprite src={data.logo} alt={data.name || 'Squadron'} size={80} /> : <IconSquadron />}
        </div>
        <div className="min-w-0 flex-1">
          <h1 className="text-xl text-cream text-pixel-shadow md:text-2xl">{data.name || 'Squadron'}</h1>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            {data.rankLabel && (
              <span className="inline-flex items-center rounded-[2px] border-2 border-cream/30 px-2 py-0.5 text-[0.62rem] font-bold uppercase tracking-wide text-cream/80">
                {data.rankLabel}
              </span>
            )}
            <span className="inline-flex items-center gap-1 text-xs font-bold text-cream/70">
              <IconTrainer className="text-[0.9em]" /> {fmtNum(data.memberCount)} members
            </span>
          </div>
        </div>
      </section>

      {/* Camp boss */}
      {data.campBoss && (
        <section className="pixel-panel p-4 md:p-5">
          <h2 className="mb-3 inline-block rounded-[2px] bg-ink px-2 py-1 text-[0.7rem] text-cream" style={{ fontFamily: 'var(--font-display)' }}>
            Camp Boss
          </h2>
          <Link
            to={`/trainers/${data.campBoss.guid}`}
            className="flex items-center gap-3 rounded-[2px] border-2 border-gold/40 bg-gold/10 p-3 transition-colors hover:bg-gold/20"
          >
            <span className="pixel-screen flex h-12 w-12 shrink-0 items-center justify-center text-2xl text-gold-deep">
              <IconStar />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-sm font-extrabold text-ink">{cleanTrainerName(data.campBoss.name)}</span>
              <span className="block text-[0.65rem] font-bold uppercase tracking-wide text-ink-mute">
                Leader · defeat to claim the camp
              </span>
            </span>
          </Link>
        </section>
      )}

      {/* Named members */}
      {split.named.length > 0 && (
        <section className="pixel-panel p-4 md:p-5">
          <h2 className="mb-1 inline-block rounded-[2px] bg-ink px-2 py-1 text-[0.7rem] text-cream" style={{ fontFamily: 'var(--font-display)' }}>
            Members ({split.named.length})
          </h2>
          <p className="mb-4 text-xs text-ink-mute">Named Lumen of this squadron.</p>
          <ul className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {split.named.map((m) => (
              <li key={m.trainerGuid}>
                <Link
                  to={`/trainers/${m.trainerGuid}`}
                  className="member-chip flex items-center justify-between gap-2 rounded-[2px] bg-ink/5 px-3 py-2 text-sm font-bold text-ink hover:bg-ink/10"
                >
                  <span className="flex min-w-0 items-center gap-2">
                    <IconTrainer className="shrink-0 text-ink-soft" />
                    <span className="truncate">{cleanTrainerName(m.name)}</span>
                  </span>
                  {m.role === 'rank' && (
                    <span className="shrink-0 text-[0.6rem] font-bold uppercase text-ink-mute">Rank</span>
                  )}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* Anonymous ranked fighters */}
      {split.roster.length > 0 && (
        <section className="pixel-panel p-4 md:p-5">
          <h2 className="mb-1 inline-block rounded-[2px] bg-ink px-2 py-1 text-[0.7rem] text-cream" style={{ fontFamily: 'var(--font-display)' }}>
            Roster Fighters ({split.roster.length})
          </h2>
          <p className="mb-4 text-xs text-ink-mute">
            Unnamed ranked Lumen you battle to climb the squadron and take its camp.
          </p>
          <ul className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {split.roster.map((m) => (
              <li key={m.trainerGuid}>
                <Link
                  to={`/trainers/${m.trainerGuid}`}
                  data-anon="true"
                  className="member-chip flex items-center gap-2 rounded-[2px] border-2 border-ink/10 bg-ink/[0.03] px-3 py-2 text-[0.8rem] font-bold text-ink-soft hover:bg-ink/10"
                >
                  <IconTrainer className="shrink-0 text-ink-mute" />
                  <span className="truncate">{m.name.includes('_') ? cleanTrainerName(m.name) : m.name}</span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      {split.named.length === 0 && split.roster.length === 0 && (
        <section className="pixel-panel p-4 md:p-5">
          <p className="text-sm text-ink-mute">No members listed for this squadron.</p>
        </section>
      )}
    </div>
  )
}
