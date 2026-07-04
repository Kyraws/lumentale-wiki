import { Link, useParams } from 'react-router-dom'
import { useApi } from '../lib/api'
import Sprite from '../components/Sprite'
import { Tag } from '../components/Badge'
import { ErrorState, Skeleton } from '../components/States'
import { IconBack } from '../components/Icons'
import { rawStr, rawNum, titleCase } from '../lib/game'
import HoloCard from './HoloCard'

// Numeric `Rarity` ladder → label, recovered from the live data
// (Common 0 < Uncommon 1 < Rare 2 < Power 3 < Event 4 < Kickstarter 5).
const RARITY_LABELS = ['Common', 'Uncommon', 'Rare', 'Power', 'Event', 'Kickstarter']
function rarityLabel(n?: number | null): string | null {
  if (n == null) return null
  return RARITY_LABELS[n] ?? null
}

// Live shape of GET /api/cards/{guid} (inspected). `card` is a raw pruned
// record with original PascalCase keys — read defensively. `art`/`form`/`pools`
// are resolved by the serializer. No top-level mask/holo (the raw references
// are empty objects), so we render the resolved `art` only.
interface CardForm {
  guid: string
  species?: string
  variant?: string
  menuArt?: string | null
}
interface CardPoolRef {
  name?: string
  kickstarter?: boolean
  weight?: number
  level?: number
  ord?: number
}
interface CardDetailData {
  card: Record<string, unknown>
  art?: string | null
  /** Per-card holo pipeline assets (filesystem-resolved on the 34 holo cards). */
  holo?: string | null
  mask?: string | null
  form?: CardForm | null
  pools?: CardPoolRef[]
}

export default function CardDetail() {
  const { guid = '' } = useParams()
  const { data, loading, error, reload } = useApi<CardDetailData>(`/api/cards/${guid}`)

  if (error) return <ErrorState message={error} onRetry={reload} />
  if (loading || !data) return <Skeleton className="h-72 w-full" />

  const { card, form, pools = [] } = data
  const artist = rawStr(card, 'ArtistName')
  const rarityNum = rawNum(card, 'Rarity')
  const rarity = rarityLabel(rarityNum)
  const name = form?.species || 'Card'

  return (
    <div className="flex flex-col gap-5">
      <Link
        to="/cards"
        className="inline-flex w-fit items-center gap-1 text-sm font-bold text-cream/70 hover:text-cream"
      >
        <IconBack /> Back to Cards
      </Link>

      <section className="dialog-box flex flex-col items-center gap-7 p-5 md:flex-row md:items-start md:p-7">
        {/* Hero card — big, holo, pointer-tracked. Art fills the rectangle. */}
        <div className="w-full max-w-[26rem] shrink-0 sm:max-w-[30rem] md:w-[32rem] md:max-w-none lg:w-[36rem]">
          <HoloCard
            art={data.art} alt={name} rarity={rarity} hero
            holo={data.holo} mask={data.mask}
            holoTilingX={(data.card as any)?.HoloTextureTiling?.x}
            holoTilingY={(data.card as any)?.HoloTextureTiling?.y}
          />
          {rarity && (
            <p className="mt-3 text-center text-[0.7rem] font-bold uppercase tracking-wide text-cream/60">
              {rarity === 'Common'
                ? 'Matte finish'
                : `${rarity} · holographic finish — move the cursor over the card`}
            </p>
          )}
        </div>
        <div className="min-w-0 flex-1 self-stretch">
          <h1 className="text-2xl text-cream text-pixel-shadow md:text-3xl">{name}</h1>
          <div className="mt-4 flex flex-wrap items-center gap-2">
            {rarity && <Tag>{rarity}</Tag>}
            {form?.variant && form.variant !== 'Base Form' && <Tag>{form.variant}</Tag>}
          </div>
          {artist && (
            <p className="mt-4 text-sm text-cream/70">
              Art by <span className="font-extrabold text-cream">{artist}</span>
            </p>
          )}
        </div>
      </section>

      {/* Depicted form */}
      {form?.guid && (
        <Panel title="Depicts">
          <Link
            to={`/dex/${form.guid}`}
            className="pixel-screen flex w-fit items-center gap-3 p-3 transition-transform hover:-translate-y-0.5"
          >
            <Sprite src={form.menuArt} alt={form.species ?? 'Form'} size={56} />
            <span className="flex flex-col">
              <span className="text-sm font-extrabold text-ink">{form.species ?? 'Unknown form'}</span>
              {form.variant && <span className="text-xs font-bold text-ink-mute">{form.variant}</span>}
            </span>
          </Link>
        </Panel>
      )}

      {/* Pools */}
      {pools.length > 0 && (
        <Panel title="Found In Pools">
          <ul className="flex flex-col gap-2">
            {pools.map((p, i) => (
              <li
                key={i}
                className="flex flex-wrap items-center justify-between gap-2 rounded-[2px] bg-ink/5 px-3 py-2 text-sm font-bold text-ink"
              >
                <span className="flex items-center gap-2">
                  {titleCase(p.name?.replace(/PullData$/, '') || 'Pool')}
                  {p.kickstarter && <Tag>Kickstarter</Tag>}
                </span>
                {p.weight != null && <span className="text-lumen-deep">weight {p.weight}</span>}
              </li>
            ))}
          </ul>
        </Panel>
      )}
    </div>
  )
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
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
