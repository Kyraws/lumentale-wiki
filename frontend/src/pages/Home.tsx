import { Link } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { MetaCounts, CreatureSummary } from '../lib/types'
import Sprite from '../components/Sprite'
import { fmtNum } from '../lib/game'
import {
  IconDex,
  IconMap,
  IconScroll,
  IconMove,
  IconBag,
  IconBoss,
  IconTypes,
  IconCog,
} from '../components/Icons'

const TILES = [
  { to: '/dex', label: 'Dex', key: 'species', Icon: IconDex, blurb: 'Every creature, stat & evolution' },
  { to: '/maps', label: 'World', key: 'game_map', Icon: IconMap, blurb: 'Routes, spawns & shops' },
  { to: '/story', label: 'Story', key: 'quest', Icon: IconScroll, blurb: 'Scenes, quests & forks' },
  { to: '/moves', label: 'Moves', key: 'move', Icon: IconMove, blurb: 'Power, type & learners' },
  { to: '/items', label: 'Items', key: 'item', Icon: IconBag, blurb: 'Gear, recipes & drops' },
  { to: '/bosses', label: 'Bosses', key: 'boss', Icon: IconBoss, blurb: 'Big fights & rewards' },
  { to: '/types', label: 'Types', key: undefined, Icon: IconTypes, blurb: 'Match-ups & coverage' },
  { to: '/mechanics', label: 'Mechanics', key: 'formula', Icon: IconCog, blurb: 'Formulas & XP curves' },
] as { to: string; label: string; key?: keyof MetaCounts; Icon: typeof IconDex; blurb: string }[]

export default function Home() {
  const { data: meta } = useApi<MetaCounts>('/api/meta')
  const { data: creatures } = useApi<CreatureSummary[]>('/api/creatures')

  // A lively strip of overworld sprites for the hero.
  const strip = (creatures ?? []).filter((c) => c.menuArt).slice(0, 14)

  return (
    <div className="flex flex-col gap-8">
      {/* Hero */}
      <section className="dialog-box scanlines relative overflow-hidden p-6 md:p-10">
        <div className="relative z-10 max-w-2xl">
          <p className="mb-3 text-[0.65rem] uppercase tracking-[0.3em] text-lumen">Fan Wiki</p>
          <h1 className="mb-4 text-xl leading-relaxed text-cream text-pixel-shadow md:text-2xl">
            LumenTale: Memories of Trey
          </h1>
          <p className="mb-6 max-w-prose text-sm leading-relaxed text-cream/80 md:text-base">
            A field guide to the creatures, routes, trainers and tangled story of the LumenTale
            world. Pick a region, fill your Lumendex, and trace where every path leads.
          </p>
          <div className="flex flex-wrap gap-3">
            <Link to="/dex" className="pixel-btn pixel-btn--primary">
              Open the Dex
            </Link>
            <Link to="/maps" className="pixel-btn">
              Explore the World
            </Link>
          </div>
        </div>

        {/* Bobbing sprite parade */}
        <div className="pointer-events-none mt-8 flex flex-wrap items-end gap-1 opacity-90">
          {strip.map((c, i) => (
            <span key={c.guid} style={{ animationDelay: `${(i % 5) * 0.12}s` }} className="anim-bob">
              <Sprite src={c.menuArt} alt={c.species} size={44} />
            </span>
          ))}
        </div>
      </section>

      {/* Section tiles with live counts */}
      <section>
        <h2 className="mb-4 text-sm text-cream/80">Browse the world</h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {TILES.map(({ to, label, key, Icon, blurb }) => (
            <Link
              key={to}
              to={to}
              className="pixel-panel group flex items-center gap-4 p-4 transition-transform hover:-translate-y-0.5"
            >
              <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-[2px] bg-ink/8 text-2xl text-lumen-deep">
                <Icon />
              </span>
              <span className="min-w-0">
                <span className="flex items-baseline gap-2">
                  <span className="text-sm font-extrabold text-ink" style={{ fontFamily: 'var(--font-display)' }}>
                    {label}
                  </span>
                  {key && meta?.[key] != null && (
                    <span className="text-lg font-extrabold text-lumen-deep" style={{ fontFamily: 'var(--font-pixel)' }}>
                      {fmtNum(meta[key])}
                    </span>
                  )}
                </span>
                <span className="block text-sm text-ink-mute">{blurb}</span>
              </span>
            </Link>
          ))}
        </div>
      </section>

      {/* Tiny catalogue ribbon */}
      {meta && (
        <section className="pixel-screen flex flex-wrap items-center justify-center gap-x-8 gap-y-2 p-4 text-center">
          {[
            ['Forms', meta.form],
            ['Moves', meta.move],
            ['Items', meta.item],
            ['Furniture', meta.furniture],
            ['Maps', meta.game_map],
            ['Languages', meta.languages],
          ].map(([label, n]) => (
            <span key={label as string} className="leading-tight">
              <span className="block text-lg font-extrabold text-ink" style={{ fontFamily: 'var(--font-pixel)', fontVariantNumeric: 'tabular-nums' }}>
                {fmtNum(n as number)}
              </span>
              <span className="block text-[0.65rem] font-bold uppercase tracking-wide text-ink-mute">
                {label}
              </span>
            </span>
          ))}
        </section>
      )}
    </div>
  )
}
