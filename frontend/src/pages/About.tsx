import { useApi } from '../lib/api'
import type { MetaCounts } from '../lib/types'
import { GridSkeleton, ErrorState } from '../components/States'
import { IconInfo } from '../components/Icons'
import { titleCase, fmtNum } from '../lib/game'

// Friendlier labels for a few raw table names; everything else falls back to
// titleCase (e.g. "behavior_tree" -> "Behavior Tree").
const LABELS: Record<string, string> = {
  species: 'Species',
  form: 'Forms',
  move: 'Moves',
  item: 'Items',
  crafting_recipe: 'Recipes',
  card: 'Cards',
  card_pool: 'Card Pools',
  game_map: 'Maps',
  mini_map: 'Mini-maps',
  quest: 'Quests',
  quest_node: 'Quest Nodes',
  trainer: 'Trainers',
  boss: 'Bosses',
  camp: 'Camps',
  squadron: 'Squadrons',
  achievement: 'Achievements',
  furniture: 'Furniture',
  tutorial: 'Tutorials',
  formula: 'Formulas',
  game_constant: 'Constants',
  xp_curve: 'XP Curves',
  behavior_tree: 'Behavior Trees',
  timeline_director: 'Timelines',
  minigame_instance: 'Minigames',
  localized_strings: 'Localized Strings',
  languages: 'Languages',
  variable: 'Variables',
  asset: 'Assets',
}

export default function About() {
  const { data, loading, error, reload } = useApi<MetaCounts>('/api/meta')

  const tiles = data
    ? Object.entries(data)
        .filter(([, n]) => typeof n === 'number')
        .sort((a, b) => b[1] - a[1])
    : []

  return (
    <div className="flex flex-col gap-8">
      <header className="dialog-box scanlines flex items-center gap-4 p-6 md:p-8">
        <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-[2px] bg-lumen/15 text-3xl text-lumen">
          <IconInfo />
        </span>
        <div>
          <h1 className="text-lg text-cream text-pixel-shadow md:text-xl">Dex Stats</h1>
          <p className="mt-2 text-sm text-cream/70">
            Everything the LumenDex has catalogued, counted straight from the dataset.
          </p>
        </div>
      </header>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading || !data ? (
        <GridSkeleton count={12} />
      ) : (
        <section
          className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4"
          aria-label="Dataset counts"
        >
          {tiles.map(([table, n], i) => (
            <div
              key={table}
              className="pixel-panel anim-pop flex flex-col items-center justify-center gap-1 p-4 text-center"
              style={{ animationDelay: `${Math.min(i, 20) * 14}ms` }}
            >
              <span
                className="text-xl font-extrabold text-lumen-deep md:text-2xl"
                style={{ fontFamily: 'var(--font-pixel)', fontVariantNumeric: 'tabular-nums' }}
              >
                {fmtNum(n)}
              </span>
              <span className="text-[0.7rem] font-bold uppercase tracking-wide text-ink-mute">
                {LABELS[table] ?? titleCase(table)}
              </span>
            </div>
          ))}
        </section>
      )}

      {/* About blurb + fan-made disclaimer (mirrors the footer tone). */}
      <section className="pixel-panel flex flex-col gap-3 p-5 md:p-6">
        <h2 className="inline-block w-fit rounded-[2px] bg-ink px-2 py-1 text-[0.7rem] text-cream" style={{ fontFamily: 'var(--font-display)' }}>
          About this wiki
        </h2>
        <p className="max-w-prose text-sm leading-relaxed text-ink-soft">
          The LumenDex is a fan-made field guide to <span className="font-extrabold text-ink">LumenTale: Memories of Trey</span>
          {' '}— a searchable record of every creature, move, item, location, trainer and tangled
          story thread in the game. Pick a region, fill your Dex, and trace where every path leads.
        </p>
        <p className="max-w-prose text-xs leading-relaxed text-ink-mute">
          This is an unofficial, non-commercial fan project and is not affiliated with or endorsed
          by the creators of LumenTale. All game names, assets and trademarks belong to their
          respective owners. Data is presented for reference only.
        </p>
      </section>
    </div>
  )
}
