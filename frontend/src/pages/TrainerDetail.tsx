import { Link, useParams } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { TrainerDetail as TDetail, PartyMember } from '../lib/types'
import Sprite from '../components/Sprite'
import { TypeBadge, EmotionBadge, RegionBadge, Tag } from '../components/Badge'
import { ErrorState, Skeleton } from '../components/States'
import { IconBack } from '../components/Icons'
import { cleanTrainerName, titleCase } from '../lib/game'
import { cleanMapName } from '../lib/mapName'

export default function TrainerDetail() {
  const { guid = '' } = useParams()
  const { data, loading, error, reload } = useApi<TDetail>(`/api/trainers/${guid}`)

  if (error) return <ErrorState message={error} onRetry={reload} />
  if (loading || !data) return <Skeleton className="h-72 w-full" />

  const name = cleanTrainerName(data.name, data.display)

  return (
    <div className="flex flex-col gap-5">
      <Link to="/trainers" className="inline-flex w-fit items-center gap-1 text-sm font-bold text-cream/70 hover:text-cream">
        <IconBack /> Back to Trainers
      </Link>

      <section className="dialog-box flex flex-wrap items-center gap-5 p-5 md:p-7">
        <div className="pixel-screen flex h-28 w-28 shrink-0 items-center justify-center p-2">
          <Sprite src={data.idle} alt={name} size={96} />
        </div>
        <div>
          <h1 className="text-xl text-cream text-pixel-shadow md:text-2xl">{name}</h1>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            {data.lumenClass && !/^\d+$/.test(data.lumenClass) && (
              <span className="rounded-[2px] border-2 border-cream/20 px-2 py-1 text-[0.6rem] font-extrabold uppercase tracking-wide text-cream/80">
                {titleCase(data.lumenClass)}
              </span>
            )}
            {data.rank != null && data.rank > 0 && (
              <span className="text-xs font-bold text-cream/70">Rank {data.rank}</span>
            )}
            {data.levelCap != null && (
              <span className="text-xs font-bold text-cream/70">Level cap {data.levelCap}</span>
            )}
            {data.money != null && (
              <span className="text-base font-extrabold text-gold" style={{ fontFamily: 'var(--font-pixel)' }}>
                {data.money}₲
              </span>
            )}
          </div>
        </div>
      </section>

      {/* Party */}
      <Panel title={`Team (${data.party.length})`}>
        {data.party.length === 0 ? (
          <p className="text-sm text-ink-mute">This trainer has no battle team.</p>
        ) : (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {data.party.map((p) => (
              <PartyCard key={p.ord} p={p} />
            ))}
          </div>
        )}
      </Panel>

      <div className="grid gap-5 lg:grid-cols-2">
        {/* Where found */}
        {(data.foundOnMaps.length > 0 || data.foundInScenes.length > 0) && (
          <Panel title="Where to Find">
            {data.foundOnMaps.length > 0 && (
              <ul className="mb-3 flex flex-col gap-2">
                {data.foundOnMaps.map((m) => (
                  <li key={m.guid}>
                    <Link to={`/maps/${m.guid}`} className="flex items-center justify-between gap-2 rounded-[2px] bg-ink/5 px-3 py-2 text-sm font-bold text-ink hover:bg-ink/10">
                      <span className="truncate">{cleanMapName(m.name, null)}</span>
                      {m.region && (m.region === 'north' || m.region === 'south') && <RegionBadge region={m.region} />}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
            {data.foundInScenes.length > 0 && (
              <div className="flex flex-wrap gap-2">
                {data.foundInScenes.map((s) => (
                  <Link
                    key={s.sceneId}
                    to={`/story/scene?id=${encodeURIComponent(s.sceneId)}`}
                    className="inline-flex items-center gap-1 rounded-[2px] border-2 border-ink/20 bg-ink/5 px-2 py-1 text-xs font-bold text-ink-soft hover:bg-ink/10 hover:text-ink"
                  >
                    <span aria-hidden>📜</span>
                    <span className="truncate">{s.name}</span>
                  </Link>
                ))}
              </div>
            )}
          </Panel>
        )}

        {/* Squadrons */}
        {data.squadrons.length > 0 && (
          <Panel title="Squadrons">
            <div className="flex flex-wrap gap-2">
              {data.squadrons.map((s) => (
                <span key={s.guid} className="inline-flex items-center gap-2 rounded-[2px] border-2 border-el-aura/40 bg-el-aura/10 px-3 py-2 text-sm font-bold text-ink">
                  {s.name}
                  {s.role && <Tag>{s.role}</Tag>}
                </span>
              ))}
            </div>
          </Panel>
        )}
      </div>
    </div>
  )
}

function PartyCard({ p }: { p: PartyMember }) {
  return (
    <Link to={`/dex/${p.formGuid}`} className="flex gap-3 rounded-[2px] border-2 border-ink/15 bg-ink/5 p-3 transition-transform hover:-translate-y-0.5">
      <div className="pixel-screen flex h-16 w-16 shrink-0 items-center justify-center p-1">
        <Sprite src={p.menuArt} alt={p.species} size={56} />
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline justify-between gap-2">
          <span className="line-clamp-1 text-sm font-extrabold text-ink">{p.nickname || p.species}</span>
          {p.level != null && (
            <span className="shrink-0 text-xs font-extrabold text-ink-soft" style={{ fontFamily: 'var(--font-pixel)' }}>
              Lv {p.level}
            </span>
          )}
        </div>
        {p.nickname && <span className="block text-[0.65rem] text-ink-mute">{p.species}</span>}
        <div className="mt-1.5 flex flex-wrap gap-1">
          <TypeBadge type={p.ele} small />
          <EmotionBadge emo={p.emo} small />
        </div>
        {p.quirkClass && (
          <div className="mt-1.5 text-[0.65rem] font-bold text-ink-mute">Quirk: {titleCase(p.quirkClass)}</div>
        )}
      </div>
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
