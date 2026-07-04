import { useEffect } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { Quirk } from '../lib/types'
import { Tag } from '../components/Badge'
import Sprite from '../components/Sprite'
import { GridSkeleton, ErrorState, EmptyState } from '../components/States'
import { titleCase } from '../lib/game'
import './quirks.css'

type Owner = Quirk['owners'][number]

export default function Quirks() {
  const { data, loading, error, reload } = useApi<Quirk[]>('/api/quirks?lang=en')
  const { hash } = useLocation()

  // Deep-link target (e.g. /quirks#Anode from a creature page): once the list
  // is rendered, scroll the matching row into view and flash a highlight.
  // PRESERVED behavior — creature pages link to /quirks#<class> and rely on
  // both the id={class} anchor and the .flash-target pulse below.
  useEffect(() => {
    if (!data || !hash) return
    const el = document.getElementById(decodeURIComponent(hash.slice(1)))
    if (!el) return
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    el.classList.remove('flash-target')
    void el.offsetWidth // force reflow so the animation re-fires on repeat visits
    el.classList.add('flash-target')
    const t = setTimeout(() => el.classList.remove('flash-target'), 1800)
    return () => clearTimeout(t)
  }, [data, hash])

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="text-lg text-cream text-pixel-shadow">Quirks</h1>
        <p className="mt-1 max-w-prose text-sm text-cream/60">
          Innate traits a creature can be born with. Each lists the species that can roll it —
          dimmed sprites are hidden quirks.
        </p>
      </header>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading || !data ? (
        <GridSkeleton count={8} />
      ) : data.length === 0 ? (
        <EmptyState title="No quirks recorded." hint="The dataset has no quirk entries." />
      ) : (
        <div className="flex flex-col gap-3">
          {data.map((q) => (
            <QuirkRow key={q.class} q={q} />
          ))}
        </div>
      )}
    </div>
  )
}

function QuirkRow({ q }: { q: Quirk }) {
  const title = q.name || titleCase(q.class)
  return (
    <section
      id={q.class}
      className="pixel-panel flex scroll-mt-24 flex-col gap-3 p-4 transition-shadow sm:flex-row sm:items-start sm:gap-5"
    >
      {/* Left: name + description + count. Fixed-ish width on wide screens so the
          icon rail lines up across quirks. */}
      <div className="flex flex-col gap-2 sm:w-72 sm:shrink-0">
        <div className="flex items-baseline justify-between gap-2">
          <h2 className="text-[0.7rem] leading-tight text-ink" style={{ fontFamily: 'var(--font-display)' }}>
            {title}
          </h2>
          <span
            className="shrink-0 rounded-[2px] bg-ink/8 px-1.5 text-[0.7rem] font-extrabold leading-none text-ink-soft"
            style={{ fontFamily: 'var(--font-pixel)' }}
            title={`${q.owners.length} bearer${q.owners.length === 1 ? '' : 's'}`}
          >
            {q.owners.length}
          </span>
        </div>
        {q.description && (
          <p className="border-l-4 border-lumen/40 pl-3 text-sm leading-relaxed text-ink-soft">
            {q.description}
          </p>
        )}
      </div>

      {/* Right: a rail of menu-sprite icons, one per bearer form, each linking to
          its creature page (same /dex/<guid> target as the old text chips). */}
      {q.owners.length === 0 ? (
        <p className="self-center text-sm text-ink-mute">No known owners.</p>
      ) : (
        <ul className="flex flex-1 flex-wrap content-start gap-2">
          {q.owners.map((o) => (
            <OwnerIcon key={o.guid} o={o} />
          ))}
        </ul>
      )}
    </section>
  )
}

function OwnerIcon({ o }: { o: Owner }) {
  const variant = o.variant && o.variant !== 'Base Form' ? o.variant : null
  const label = variant ? `${o.species} (${variant})` : o.species
  return (
    <li>
      <Link
        to={`/dex/${o.guid}`}
        title={o.hidden ? `${label} — hidden quirk` : label}
        className={`quirk-owner pixel-screen group relative flex w-[5.5rem] flex-col items-center gap-1 p-1.5 text-center transition-transform hover:-translate-y-0.5${
          o.hidden ? ' is-hidden' : ''
        }`}
      >
        <div className="pointer-events-none flex aspect-square w-full items-center justify-center">
          <Sprite src={o.menuArt} alt={o.species} size={56} className="group-hover:scale-110" />
        </div>
        <span className="line-clamp-1 w-full text-[0.62rem] font-bold leading-tight text-ink-soft">
          {o.species}
        </span>
        {variant && (
          <span className="line-clamp-1 w-full text-[0.58rem] leading-tight text-ink-mute">{variant}</span>
        )}
        {o.hidden && (
          <span className="absolute right-1 top-1">
            <Tag>Hidden</Tag>
          </span>
        )}
      </Link>
    </li>
  )
}
