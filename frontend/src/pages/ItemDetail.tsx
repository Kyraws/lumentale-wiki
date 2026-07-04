import { Link, useParams } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { ItemDetail as IDetail } from '../lib/types'
import Sprite from '../components/Sprite'
import { Tag } from '../components/Badge'
import { ErrorState, Skeleton } from '../components/States'
import { IconBack } from '../components/Icons'
import { cleanMapName } from '../lib/mapName'
import { itemTypeLabel, cleanSceneName } from '../lib/game'

export default function ItemDetail() {
  const { guid = '' } = useParams()
  const { data, loading, error, reload } = useApi<IDetail>(`/api/items/${guid}`)

  if (error) return <ErrorState message={error} onRetry={reload} />
  if (loading || !data) return <Skeleton className="h-72 w-full" />

  return (
    <div className="flex flex-col gap-5">
      <Link to="/items" className="inline-flex w-fit items-center gap-1 text-sm font-bold text-cream/70 hover:text-cream">
        <IconBack /> Back to Items
      </Link>

      <section className="dialog-box flex flex-wrap items-center gap-5 p-5 md:p-7">
        <div className="pixel-screen flex h-24 w-24 shrink-0 items-center justify-center p-2">
          {data.icon ? (
            <img src={data.icon} alt={data.name} className="sprite h-full w-full object-contain" />
          ) : (
            <span className="text-3xl text-ink-mute/50" style={{ fontFamily: 'var(--font-display)' }}>?</span>
          )}
        </div>
        <div>
          <h1 className="text-xl text-cream text-pixel-shadow md:text-2xl">{data.name}</h1>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            {data.type && <Tag>{itemTypeLabel(data.type)}</Tag>}
            {data.material && <Tag>{data.material}</Tag>}
            {data.price != null && (
              <span className="text-base font-extrabold text-gold" style={{ fontFamily: 'var(--font-pixel)' }}>
                {data.price}₲
              </span>
            )}
            {data.maxStack != null && (
              <span className="text-xs font-bold text-cream/60">stacks to {data.maxStack}</span>
            )}
          </div>
          {data.description && (
            <p className="mt-4 max-w-prose border-l-4 border-lumen/40 pl-3 text-sm italic leading-relaxed text-cream/80">
              {data.description}
            </p>
          )}
        </div>
      </section>

      {/* What it does */}
      <Effects effects={data.effects} />

      {/* Recipe */}
      {data.recipe && data.recipe.ingredients.length > 0 && (
        <Panel title="Recipe">
          <div className="mb-3 flex flex-wrap items-center gap-3 text-sm text-ink-soft">
            {data.recipe.preferredActor && (
              <span>
                Best cooked by <span className="font-extrabold text-ink">{data.recipe.preferredActor}</span>
              </span>
            )}
            {data.recipe.successRate != null && (
              <span className="font-bold text-lumen-deep">{data.recipe.successRate}% success</span>
            )}
          </div>
          <div className="flex flex-wrap gap-2">
            {data.recipe.ingredients.map((ing, i) => {
              const inner = (
                <span className="inline-flex items-center gap-2 rounded-[2px] border-2 border-ink/20 bg-parch px-3 py-1.5 text-sm font-bold text-ink">
                  {ing.name || 'Unknown'}
                  {ing.amount != null && <span className="text-lumen-deep">×{ing.amount}</span>}
                </span>
              )
              return ing.guid ? (
                <Link key={i} to={`/items/${ing.guid}`} className="hover:opacity-80">
                  {inner}
                </Link>
              ) : (
                <span key={i}>{inner}</span>
              )
            })}
          </div>
        </Panel>
      )}

      <div className="grid gap-5 lg:grid-cols-2">
        {/* Dropped by */}
        {data.droppedBy.length > 0 && (
          <Panel title="Dropped By">
            <div className="grid grid-cols-3 gap-3 sm:grid-cols-4">
              {data.droppedBy.map((d) => (
                <Link key={d.guid} to={`/dex/${d.guid}`} className="pixel-screen flex flex-col items-center p-2 text-center transition-transform hover:-translate-y-0.5">
                  <Sprite src={d.menuArt} alt={d.species} size={44} />
                  <span className="mt-1 line-clamp-1 text-xs font-extrabold text-ink">{d.species}</span>
                </Link>
              ))}
            </div>
          </Panel>
        )}

        {/* Sold at */}
        {data.soldAt.length > 0 && (
          <Panel title="Sold At">
            <ul className="flex flex-col gap-2">
              {data.soldAt.map((s, i) => (
                <li key={i}>
                  <Link to={`/maps/${s.mapGuid}`} className="flex items-center justify-between gap-2 rounded-[2px] bg-ink/5 px-3 py-2 text-sm font-bold text-ink hover:bg-ink/10">
                    <span className="truncate">{cleanMapName(s.mapName, null)}</span>
                    {s.price != null && <span className="shrink-0 text-lumen-deep">{s.price}₲</span>}
                  </Link>
                </li>
              ))}
            </ul>
          </Panel>
        )}
      </div>

      {/* Found on maps */}
      {data.foundOn.length > 0 && (
        <Panel title="Found On">
          <div className="flex flex-wrap gap-2">
            {data.foundOn.map((f, i) => (
              <Link key={i} to={`/maps/${f.mapGuid}`} className="inline-flex items-center gap-2 rounded-[2px] border-2 border-gold/40 bg-gold/10 px-3 py-2 text-sm font-bold text-ink hover:bg-gold/20">
                {cleanMapName(f.mapName, null)}
                <span className="text-xs text-ink-mute">{f.spots} spot{f.spots === 1 ? '' : 's'}</span>
              </Link>
            ))}
          </div>
        </Panel>
      )}

      {/* Ingredient of */}
      {data.usedIn.length > 0 && (
        <Panel title="Used In Recipes">
          <div className="flex flex-wrap gap-2">
            {data.usedIn.map((u, i) => {
              const inner = (
                <span className="inline-flex items-center gap-2 rounded-[2px] border-2 border-ink/20 bg-parch px-3 py-1.5 text-sm font-bold text-ink">
                  {u.resultName || 'Unknown recipe'}
                  {u.amount != null && <span className="text-ink-mute">needs ×{u.amount}</span>}
                </span>
              )
              return u.resultGuid ? (
                <Link key={i} to={`/items/${u.resultGuid}`} className="hover:opacity-80">
                  {inner}
                </Link>
              ) : (
                <span key={i}>{inner}</span>
              )
            })}
          </div>
        </Panel>
      )}

      {/* Given by story events */}
      {data.givenIn.length > 0 && (
        <Panel title="Given In Story">
          <div className="flex flex-wrap gap-2">
            {data.givenIn.map((g) => (
              <Link
                key={g.sceneId}
                to={`/story?scene=${encodeURIComponent(g.sceneId)}`}
                className="inline-flex items-center gap-2 rounded-[2px] border-2 border-lumen/40 bg-lumen/10 px-3 py-1.5 text-sm font-bold text-ink hover:bg-lumen/20"
              >
                <span aria-hidden>📜</span> {cleanSceneName(g.sceneName)}
              </Link>
            ))}
          </div>
        </Panel>
      )}

      {data.droppedBy.length === 0 && data.soldAt.length === 0 && data.foundOn.length === 0 && !data.recipe &&
        data.givenIn.length === 0 && (
        <Panel title="Sources">
          <p className="text-sm text-ink-mute">No known sources recorded — likely a story or event item.</p>
        </Panel>
      )}
    </div>
  )
}

/* ---------- effects ---------- */

const STAT_NAMES = ['HP', 'ATK', 'DEF', 'SpA', 'SpD', 'Spe']
const n = (v: unknown): number | undefined => (typeof v === 'number' ? v : undefined)

/** One effect entry -> a short human line, or null to hide meta entries. */
function describeEffect(cls: string | undefined, d: Record<string, unknown>): string | null {
  const amt = n(d.Amount)
  const dur = n(d.Duration)
  switch (cls) {
    case 'RecipeDescriptionOverrider':
      return null // meta: just points at the recipe shown above
    case 'ChangeHP':
      return d.ReviveAnimon
        ? `Revives with ${amt}${d.UsesPercentage ? '%' : ''} HP`
        : `Restores ${amt}${d.UsesPercentage ? '%' : ''} HP`
    case 'ReviveAnimon':
      return `Revives with ${amt}${d.UsesPercentage ? '%' : ''} HP`
    case 'ChangeSP':
      return `Restores ${amt} SP`
    case 'ChangeStat':
      return `+${amt} ${STAT_NAMES[n(d.Stat) ?? -1] ?? 'stat'} in battle`
    case 'IncreaseAnimonStatRoll':
      return `Permanently raises the ${STAT_NAMES[n(d.StatToChange) ?? -1] ?? 'stat'} roll by ${amt}`
    case 'BattleShieldItemEffect':
      return `Shields ${n(d.ShieldStrenght)}${d.Percentage ? '%' : ''} damage for ${dur} turns`
    case 'GiveExpAmountItemEffect':
    case 'ExpGiverItemEffect':
      return `Grants ${n(d.ExpAmount) ?? amt ?? ''} EXP`
    case 'ChangeAnimonAffectionItemEffect':
      return `+${amt} affection`
    case 'OverworldSpeedBuffItemEffect':
      return `×${(n(d.SpeedMultiplier) ?? 1).toFixed(1)} overworld speed for ${dur}s`
    case 'MoneyGainBuffItemEffect':
      return `Boosts money gained${dur ? ` for ${dur}s` : ''}`
    case 'RepelItemEffect':
      return `Repels wild Animon for ${dur}s`
    case 'DispelItemEffect':
      return 'Dispels active effects'
    case 'OpenCardPack':
      return 'Opens a card pack'
    case 'OpenCardBox':
      return 'Opens a card box'
    case 'ChangeHiddenTypeEffect':
      return "Changes the Animon's hidden type"
    case 'ChangeQuirkEffect':
      return "Changes the Animon's quirk"
    case 'BiliaAttributeCatchRateModifier':
    case 'BiliaCustomCatchRateModifier':
    case 'BiliaScalarCatchRateModifier':
      return 'Modifies catch rate'
    case 'BiliaHolokenRepsModifier':
    case 'BiliaHolokenLengthModifier':
      return 'Modifies the catch minigame'
    default:
      // de-camel the class name as a generic fallback
      return (cls ?? 'Effect').replace(/ItemEffect$|Effect$/, '').replace(/([a-z])([A-Z])/g, '$1 $2')
  }
}

function Effects({ effects }: { effects: IDetail['effects'] }) {
  if (!Array.isArray(effects)) return null
  const lines = effects
    .map((e) => describeEffect(e.class, (e.data as Record<string, unknown>) ?? {}))
    .filter(Boolean) as string[]
  if (lines.length === 0) return null
  return (
    <Panel title="Effects">
      <ul className="flex flex-col gap-1.5">
        {lines.map((l, i) => (
          <li key={i} className="rounded-[2px] bg-ink/5 px-3 py-2 text-sm font-bold text-ink">
            {l}
          </li>
        ))}
      </ul>
    </Panel>
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
