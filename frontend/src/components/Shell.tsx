import { NavLink, Link, useLocation } from 'react-router-dom'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import {
  IconHome, IconDex, IconMap, IconScroll, IconMove, IconBag, IconTrainer, IconTypes,
  IconBoss, IconCard, IconFurniture, IconCamp, IconSquadron, IconQuest, IconTrophy,
  IconBook, IconCog, IconGraph, IconSpark, IconInfo, IconMore,
} from './Icons'

type Item = { to: string; label: string; Icon: (p: { className?: string }) => ReactNode; end?: boolean }

// Primary nav stays small (clear hierarchy); everything else lives under "More".
const PRIMARY: Item[] = [
  { to: '/', label: 'Home', Icon: IconHome, end: true },
  { to: '/dex', label: 'Dex', Icon: IconDex },
  { to: '/maps', label: 'World', Icon: IconMap },
  { to: '/story', label: 'Story', Icon: IconScroll },
  { to: '/moves', label: 'Moves', Icon: IconMove },
  { to: '/items', label: 'Items', Icon: IconBag },
  { to: '/types', label: 'Types', Icon: IconTypes },
]

// Secondary nav, grouped by theme (overflow menu — keeps the bar uncluttered).
const GROUPS: { heading: string; items: Item[] }[] = [
  {
    heading: 'Creatures & Battle',
    items: [
      { to: '/bosses', label: 'Bosses', Icon: IconBoss },
      { to: '/cards', label: 'Cards', Icon: IconCard },
      { to: '/mechanics', label: 'Mechanics', Icon: IconCog },
    ],
  },
  {
    heading: 'People & Places',
    items: [
      { to: '/trainers', label: 'Trainers', Icon: IconTrainer },
      { to: '/quests', label: 'Quests', Icon: IconQuest },
      { to: '/camps', label: 'Camps', Icon: IconCamp },
      { to: '/squadrons', label: 'Squadrons', Icon: IconSquadron },
    ],
  },
  {
    heading: 'Collection & Lore',
    items: [
      { to: '/furniture', label: 'Furniture', Icon: IconFurniture },
      { to: '/achievements', label: 'Achievements', Icon: IconTrophy },
      { to: '/tutorials', label: 'Tutorials', Icon: IconBook },
      { to: '/quirks', label: 'Quirks', Icon: IconSpark },
      { to: '/logic', label: 'Logic Graphs', Icon: IconGraph },
      { to: '/about', label: 'About', Icon: IconInfo },
    ],
  },
]

const navItemClass = (isActive: boolean) =>
  [
    'group flex shrink-0 items-center gap-2 rounded-[2px] border-2 px-3 py-2 text-[0.62rem] uppercase tracking-wide transition-colors',
    isActive
      ? 'border-gold bg-gold/20 text-gold'
      : 'border-transparent text-cream/70 hover:border-cream/20 hover:bg-white/5 hover:text-cream',
  ].join(' ')

function NavItem({ to, label, Icon, end }: Item) {
  return (
    <NavLink to={to} end={end} className={({ isActive }) => navItemClass(isActive)}
      style={{ fontFamily: 'var(--font-display)' }}>
      <Icon className="text-base" />
      <span>{label}</span>
    </NavLink>
  )
}

/** "More" dropdown holding the grouped secondary navigation. */
function MoreMenu() {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const { pathname } = useLocation()

  useEffect(() => setOpen(false), [pathname]) // close on navigation
  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div className="relative shrink-0" ref={ref}>
      <button
        type="button"
        aria-haspopup="true"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className={navItemClass(open) + ' cursor-pointer'}
        style={{ fontFamily: 'var(--font-display)' }}
      >
        <IconMore className="text-base" />
        <span>More</span>
      </button>

      {open && (
        <div role="menu" className="dialog-box anim-pop absolute right-0 z-50 mt-2 w-[min(90vw,22rem)] p-3">
          {GROUPS.map((g) => (
            <div key={g.heading} className="mb-3 last:mb-0">
              <p className="mb-1.5 px-1 text-[0.55rem] uppercase tracking-widest text-lumen"
                 style={{ fontFamily: 'var(--font-display)' }}>
                {g.heading}
              </p>
              <div className="grid grid-cols-2 gap-1">
                {g.items.map((it) => (
                  <NavLink
                    key={it.to}
                    to={it.to}
                    role="menuitem"
                    className={({ isActive }) =>
                      [
                        'flex items-center gap-2 rounded-[2px] border-2 px-2 py-2 text-[0.62rem] uppercase tracking-wide transition-colors',
                        isActive
                          ? 'border-gold bg-gold/20 text-gold'
                          : 'border-transparent text-cream/80 hover:border-cream/20 hover:bg-white/5 hover:text-cream',
                      ].join(' ')
                    }
                    style={{ fontFamily: 'var(--font-display)' }}
                  >
                    <it.Icon className="text-sm" />
                    <span className="truncate">{it.label}</span>
                  </NavLink>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default function Shell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-dvh">
      <a href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-50 focus:bg-lumen focus:px-3 focus:py-2 focus:text-ink">
        Skip to content
      </a>

      <header className="sticky top-0 z-40 border-b-4 border-[#0c0d1d] bg-night/95 backdrop-blur">
        <div className="mx-auto flex max-w-7xl flex-col gap-3 px-3 py-3 md:flex-row md:items-center">
          <Link to="/" className="flex shrink-0 items-center gap-3">
            <span className="dialog-box flex h-11 w-11 items-center justify-center text-lumen">
              <span style={{ fontFamily: 'var(--font-display)', fontSize: '1rem' }}>L</span>
            </span>
            <span className="leading-tight">
              <span className="block text-sm text-cream text-pixel-shadow" style={{ fontFamily: 'var(--font-display)' }}>
                LumenDex
              </span>
              <span className="block text-[0.65rem] font-bold uppercase tracking-widest text-lumen">
                Memories of Trey
              </span>
            </span>
          </Link>

          <nav aria-label="Primary"
            className="-mx-1 flex items-center gap-1 overflow-x-auto px-1 pb-1 md:ml-auto md:overflow-visible md:pb-0">
            {PRIMARY.map((n) => <NavItem key={n.to} {...n} />)}
            <MoreMenu />
          </nav>
        </div>
      </header>

      <main id="main" className="mx-auto max-w-7xl px-3 py-6 md:py-8">{children}</main>

      <footer className="mt-12 border-t-4 border-[#0c0d1d] bg-night-2 px-3 py-8 text-center">
        <p className="text-xs text-cream/60">
          LumenDex — a fan-made wiki for{' '}
          <span className="font-bold text-cream">LumenTale: Memories of Trey</span>.
        </p>
        <p className="mt-1 text-[0.65rem] text-cream/35">
          Sprites &amp; data © their respective owners. Not affiliated with the developers.
        </p>
      </footer>
    </div>
  )
}
