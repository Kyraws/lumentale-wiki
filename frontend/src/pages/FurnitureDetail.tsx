import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useApi } from '../lib/api'
import Sprite from '../components/Sprite'
import { Tag } from '../components/Badge'
import { ErrorState, Skeleton } from '../components/States'
import { IconBack } from '../components/Icons'
import { cleanMapName } from '../lib/mapName'
import { fmtNum } from '../lib/game'
import { RARITY } from './furnitureMeta'

// GET /api/furniture/{guid} now returns a curated FurnitureDetail with
// provenance: the shops that stock it (map_shop_entry.furniture_guid) and the
// quests that reward it (quest_item_reward.furniture_guid). Those are the only
// two channels furniture can be obtained through — there are no crafting
// recipes producing furniture, and furniture never appears as a map pickup or a
// story "Give Item" node (verified against the DB).
interface FurnitureDetailDTO {
  guid: string
  name?: string | null
  nameKey?: string | null
  rarity?: number | null
  rarityLabel?: string | null
  price?: number | null
  size?: number | null
  sizeX?: number | null
  sizeY?: number | null
  carpet?: boolean | null
  icon?: string | null
  obtainable: boolean
  soldAt: { mapGuid: string; mapName?: string | null; shop?: string | null; npc?: string | null; price?: number | null; limitAmount?: number | null }[]
  questRewards: { questGuid: string; questName?: string | null; internalName?: string | null; amount?: number | null }[]
}

export default function FurnitureDetail() {
  const { guid = '' } = useParams()
  const { data, loading, error, reload } = useApi<FurnitureDetailDTO>(`/api/furniture/${guid}`)

  if (error) return <ErrorState message={error} onRetry={reload} />
  if (loading || !data) return <Skeleton className="h-72 w-full" />

  const name = data.name || 'Furniture'
  const rar = data.rarity != null ? RARITY[data.rarity] : undefined

  return (
    <div className="flex flex-col gap-5">
      <Link
        to="/furniture"
        className="inline-flex w-fit items-center gap-1 text-sm font-bold text-cream/70 hover:text-cream"
      >
        <IconBack /> Back to Furniture
      </Link>

      {/* Hero */}
      <section className="dialog-box flex flex-wrap items-center gap-5 p-5 md:p-7">
        <div className="pixel-screen flex h-28 w-28 shrink-0 items-center justify-center p-2">
          <Sprite src={data.icon || `/data/furniture/${guid}/icon.png`} alt={name} size={96} />
        </div>
        <div className="min-w-0 flex-1">
          <h1 className="text-xl text-cream text-pixel-shadow md:text-2xl">{name}</h1>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            {rar && (
              <span
                className="rounded-[2px] px-2 py-0.5 text-xs font-extrabold"
                style={{ fontFamily: 'var(--font-pixel)', background: rar.bg, color: rar.fg }}
              >
                {rar.label}
              </span>
            )}
            {data.price != null && (
              <span className="text-base font-extrabold text-gold" style={{ fontFamily: 'var(--font-pixel)' }}>
                {fmtNum(data.price)}₲
              </span>
            )}
            {data.sizeX != null && data.sizeY != null && (
              <span className="text-sm font-bold text-cream/70">
                {data.sizeX}×{data.sizeY} tiles
              </span>
            )}
            {data.carpet && <Tag>Carpet</Tag>}
          </div>
        </div>
      </section>

      {/* Provenance */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {data.soldAt.length > 0 && (
          <Panel title={`Sold At${data.soldAt.length > 1 ? ` · ${data.soldAt.length}` : ''}`}>
            <ul className="flex flex-col gap-2">
              {data.soldAt.map((s, i) => (
                <li key={i}>
                  <Link
                    to={`/maps/${s.mapGuid}`}
                    className="flex items-center justify-between gap-2 rounded-[2px] bg-ink/5 px-3 py-2 text-sm font-bold text-ink hover:bg-ink/10"
                  >
                    <span className="min-w-0">
                      <span className="block truncate">{cleanMapName(s.shop || '', s.mapName)}</span>
                      {s.npc && (
                        <span className="block truncate text-xs font-bold text-ink-mute">{cleanNpc(s.npc)}</span>
                      )}
                    </span>
                    <span className="shrink-0 text-right">
                      {s.price != null && <span className="block text-lumen-deep">{fmtNum(s.price)}₲</span>}
                      {s.limitAmount != null && (
                        <span className="block text-xs text-ink-mute">limit {s.limitAmount}</span>
                      )}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          </Panel>
        )}

        {data.questRewards.length > 0 && (
          <Panel title={`Quest Reward${data.questRewards.length > 1 ? `s · ${data.questRewards.length}` : ''}`}>
            <ul className="flex flex-col gap-2">
              {data.questRewards.map((q, i) => (
                <li
                  key={i}
                  className="flex items-center justify-between gap-2 rounded-[2px] border-2 border-lumen/40 bg-lumen/10 px-3 py-2 text-sm font-bold text-ink"
                >
                  <span className="min-w-0">
                    <span className="block truncate">{q.questName || cleanQuest(q.internalName)}</span>
                    {q.internalName && q.questName && (
                      <span className="block truncate text-xs font-bold text-ink-mute">{cleanQuest(q.internalName)}</span>
                    )}
                  </span>
                  {q.amount != null && q.amount > 1 && (
                    <span className="shrink-0 text-ink-mute">×{q.amount}</span>
                  )}
                </li>
              ))}
            </ul>
          </Panel>
        )}
      </div>

      {!data.obtainable && (
        <Panel title="How to Obtain">
          <p className="text-sm text-ink-mute">
            No shop or quest source is recorded for this piece. It is likely a starter, DLC, or
            event/reward item placed directly into the home.
          </p>
        </Panel>
      )}
    </div>
  )
}

/** "NPC1 - FurnitureShop" / "NPC - SHOP_BAFFOBLU" → trimmed shopkeeper label. */
function cleanNpc(npc: string): string {
  const tail = npc.split('-').pop()?.trim() || npc
  return tail.replace(/_/g, ' ').replace(/\s+/g, ' ').trim()
}

/** "PD_Q3_SingingInTheRainBow" → "Singing In The Rain Bow" (quest internal id). */
function cleanQuest(internal?: string | null): string {
  if (!internal) return 'Quest'
  return (
    internal
      .replace(/^[A-Z]{2,4}_/, '') // city prefix
      .replace(/^Q(uest)?\d+_?/i, '') // quest number
      .replace(/_/g, ' ')
      .replace(/([a-z])([A-Z])/g, '$1 $2')
      .trim() || internal
  )
}

function Panel({ title, children }: { title: string; children: ReactNode }) {
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
