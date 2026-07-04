import { useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { StoryCity, SceneLite } from '../lib/types'
import SceneReader from '../components/SceneReader'
import { ErrorState, Skeleton, EmptyState } from '../components/States'
import { IconSearch, IconScroll } from '../components/Icons'
import { cleanSceneName } from '../lib/game'
import SceneQuestBadges from './SceneQuestBadges'

/**
 * Story browser (ported from the v1 two-pane design): the left rail is the
 * chaptered playthrough spine — ✦ Prologue → the chosen path → ◈ Endgame, with
 * a region header per city — and the selected scene reads inline on the right.
 */
type Path = 'both' | 'north' | 'south'

const TRACK_TINT: Record<string, string> = {
  prologue: 'var(--color-gold)',
  center: 'var(--color-gold)',
  south: 'var(--color-emo-sereum)',
  north: 'var(--color-sky)',
  hub: 'var(--color-el-aura)',
  other: 'var(--color-ink-mute)',
}

const TRACK_LABEL: Record<string, string> = {
  prologue: '✦ Prologue',
  center: '✦ Magnolia Hub — every run starts here',
  south: '▼ Southern Path',
  north: '▲ Northern Path',
  hub: '◈ Endgame (shared)',
}

const REGION_LABEL: Record<string, string> = {
  costalinda: 'Costa Linda',
  arsilia: 'Arsilia',
  borgoiride: 'Borgo Iride',
  altipetra: 'Altipetra',
  magnolia: 'Magnolia',
  memorenia: 'Memorenia',
  mirasilva: 'Mirasilva',
  paradine: 'Paradine',
  speranova: 'Speranova',
  volteria: 'Volteria',
  areasandinter: 'Areas & Interiors',
  squadronsystem: 'Squadron System',
  shared: 'Shared',
}
const rlabel = (r: string) => REGION_LABEL[r] ?? r

/** One sidebar entry: a single scene, or a numbered series merged into one
 *  (e.g. "Caserma Lectern DX1/DX2/SX1/SX2" → "Caserma Lectern ×4"). */
type SceneGroup = { label: string; scenes: SceneLite[] }

const natural = (a: string, b: string) => a.localeCompare(b, undefined, { numeric: true })

/** Scene name minus trailing counter/variant tokens: "Court 3" → "Court",
 *  "Q2 a" → "Q2", "Caserma Lectern DX1" → "Caserma Lectern". */
function seriesBase(name: string): string {
  const words = cleanSceneName(name).split(' ')
  while (words.length > 1 && /^(\d+|[A-Za-z]|DX\d*|SX\d*)$/i.test(words[words.length - 1])) {
    words.pop()
  }
  return words.join(' ')
}

/**
 * Cluster a city's scenes for the spine. Main-story scenes (mainNum set) keep
 * their own entry and order. Side scenes merge ONLY when they are parts of the
 * same series — identical names once trailing counters are stripped — and read
 * in numeric order (DX1 → DX2 → DX10). Nothing semantically different merges.
 */
function groupScenes(scenes: SceneLite[]): SceneGroup[] {
  const baseSize = new Map<string, number>()
  for (const s of scenes) {
    if (s.mainNum == null) {
      const b = seriesBase(s.name)
      baseSize.set(b, (baseSize.get(b) ?? 0) + 1)
    }
  }
  const out: SceneGroup[] = []
  const emitted = new Set<string>()
  for (const s of scenes) {
    const base = seriesBase(s.name)
    if (s.mainNum != null || (baseSize.get(base) ?? 0) < 2) {
      out.push({ label: cleanSceneName(s.name), scenes: [s] })
      continue
    }
    if (emitted.has(base)) continue // series already emitted at its first member
    emitted.add(base)
    const members = scenes
      .filter((x) => x.mainNum == null && seriesBase(x.name) === base)
      .sort((a, b) => natural(cleanSceneName(a.name), cleanSceneName(b.name)))
    out.push({ label: base, scenes: members })
  }
  return out
}

export default function Story() {
  const [path, setPath] = useState<Path>('both')
  const [q, setQ] = useState('')
  const [params, setParams] = useSearchParams()
  const sel = params.get('scene')
  // merged groups put their member ids (in series order) in ?scenes=
  const selGroup = params.get('scenes')?.split(',').filter(Boolean) ?? null
  const { data, loading, error, reload } = useApi<StoryCity[]>(`/api/story/cities?path=${path}`)

  // Search runs flat across every scene of the loaded route.
  const matches = useMemo(() => {
    if (!data) return []
    const needle = q.trim().toLowerCase()
    if (!needle) return []
    return data
      .flatMap((c) => c.scenes)
      .filter(
        (s) =>
          s.name.toLowerCase().includes(needle) ||
          cleanSceneName(s.name).toLowerCase().includes(needle),
      )
      .slice(0, 100)
  }, [data, q])

  const pick = (id: string) => setParams({ scene: id })
  const pickGroup = (ids: string[]) =>
    ids.length === 1 ? pick(ids[0]) : setParams({ scenes: ids.join(',') })

  // Keep the selected entry anchored in view in the left rail — matters when
  // navigating with prev/next or landing on a deep link.
  const listRef = useRef<HTMLDivElement>(null)
  const groupKey = selGroup?.join(',')
  useEffect(() => {
    const c = listRef.current
    const el = c?.querySelector<HTMLElement>('[aria-current]')
    if (!c || !el) return
    const cr = c.getBoundingClientRect()
    const er = el.getBoundingClientRect()
    // centre the active row within the rail (scrolls only the rail, not the page)
    c.scrollTo({
      top: c.scrollTop + (er.top - cr.top) - (c.clientHeight / 2 - el.clientHeight / 2),
      behavior: 'smooth',
    })
  }, [sel, groupKey, data, q])

  return (
    // Breakout: the reader + rail want more width than the global container.
    <div className="mx-[calc(50%-50vw)] px-4 sm:px-6">
    <div className="mx-auto flex max-w-[100rem] flex-col gap-5">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-lg text-cream text-pixel-shadow">Story</h1>
          <p className="mt-1 max-w-prose text-sm text-cream/60">
            The journey forks after the prologue. Pick a scene on the left to read it.
          </p>
        </div>
        <div className="flex gap-1.5">
          {(['both', 'south', 'north'] as Path[]).map((p) => (
            <button
              key={p}
              onClick={() => setPath(p)}
              aria-pressed={path === p}
              className={`rounded-[2px] border-2 px-3 py-1.5 text-[0.6rem] font-extrabold uppercase tracking-wide transition-colors ${
                path === p ? 'border-gold bg-gold/25 text-gold' : 'border-cream/15 text-cream/60 hover:bg-white/5'
              }`}
            >
              {p === 'both' ? '◆ Both' : p === 'south' ? '▼ South' : '▲ North'}
            </button>
          ))}
        </div>
      </header>

      {error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : loading || !data ? (
        <Skeleton className="h-96 w-full" />
      ) : data.length === 0 ? (
        <EmptyState title="No story data for this route." />
      ) : (
        <div className="grid items-start gap-5 lg:grid-cols-[320px_1fr]">
          {/* --- left rail: spine browser --- */}
          <aside className="dialog-box flex flex-col gap-3 p-3 lg:sticky lg:top-4 lg:max-h-[calc(100vh-2rem)]">
            <label className="relative block">
              <span className="sr-only">Search scenes</span>
              <IconSearch className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-base text-ink-mute" />
              <input
                value={q}
                onChange={(e) => setQ(e.target.value)}
                type="search"
                placeholder="Search scenes…"
                className="w-full rounded-[2px] border-ink bg-parch py-2 pl-9 pr-3 text-sm font-bold text-ink placeholder:text-ink-mute/70 focus:outline-none"
                style={{ borderWidth: 3 }}
              />
            </label>

            <div ref={listRef} className="flex-1 overflow-y-auto pr-1">
              {q.trim() ? (
                matches.length === 0 ? (
                  <p className="px-1 py-2 text-sm text-cream/60">No scenes match.</p>
                ) : (
                  <ul className="flex flex-col gap-1">
                    {matches.map((s) => (
                      <SceneButton key={s.sceneId} s={s} on={s.sceneId === sel} pick={pick} />
                    ))}
                  </ul>
                )
              ) : (
                <Spine cities={data} sel={sel} selGroup={selGroup} pickGroup={pickGroup} />
              )}
            </div>
          </aside>

          {/* --- right pane: the scene (or merged series), readable --- */}
          <main className="min-w-0">
            {selGroup && selGroup.length > 0 ? (
              <div className="flex flex-col gap-6">
                {selGroup.map((id, i) => (
                  <div key={id}>
                    <div className="mb-2 text-[0.62rem] font-extrabold uppercase tracking-wide text-cream/50">
                      Part {i + 1} of {selGroup.length}
                    </div>
                    <div className="mb-3"><SceneQuestBadges sceneId={id} /></div>
                    <SceneReader id={id} sceneHref={(x) => `/story?scene=${encodeURIComponent(x)}`} />
                  </div>
                ))}
              </div>
            ) : sel ? (
              <div className="flex flex-col gap-3">
                <SceneQuestBadges sceneId={sel} />
                <SceneReader
                  key={sel}
                  id={sel}
                  sceneHref={(id) => `/story?scene=${encodeURIComponent(id)}`}
                />
              </div>
            ) : (
              <div className="pixel-panel flex flex-col items-center gap-3 p-10 text-center">
                <span className="text-4xl text-ink-mute"><IconScroll /></span>
                <p className="text-sm font-bold text-ink-soft">Pick a scene from the spine to read the story.</p>
                <p className="max-w-prose text-xs text-ink-mute">
                  Scenes are ordered as you'd play them: the Prologue, then your chosen route, then the shared endgame.
                </p>
              </div>
            )}
          </main>
        </div>
      )}
    </div>
    </div>
  )
}

/** The playthrough spine: cities in play order, grouped under track headers. */
function Spine({
  cities,
  sel,
  selGroup,
  pickGroup,
}: {
  cities: StoryCity[]
  sel: string | null
  selGroup: string[] | null
  pickGroup: (ids: string[]) => void
}) {
  let lastTrack: string | null = null
  const groupKey = selGroup?.join(',')
  return (
    <div className="flex flex-col gap-1">
      {cities.map((city, i) => {
        const tint = TRACK_TINT[city.track] ?? TRACK_TINT.other
        const trackHeader = city.track !== lastTrack
        lastTrack = city.track
        const groups = groupScenes(city.scenes)
        return (
          <div key={`${city.region}-${i}`}>
            {trackHeader && (
              <div
                className="mt-2 rounded-[2px] px-2 py-1 text-[0.62rem] font-extrabold uppercase tracking-wide text-night first:mt-0"
                style={{ backgroundColor: tint }}
              >
                {TRACK_LABEL[city.track] ?? city.track}
              </div>
            )}
            <div className="mb-1 mt-1.5 flex items-center gap-1.5 px-1">
              <span className="h-2 w-2 shrink-0 rounded-full" style={{ backgroundColor: tint }} />
              <span className="text-[0.62rem] font-extrabold uppercase tracking-wide text-cream/70">
                {rlabel(city.region)}
              </span>
              <span className="text-[0.6rem] text-cream/40">{city.scenes.length}</span>
            </div>
            <ul className="flex flex-col gap-1">
              {groups.map((g) => {
                const ids = g.scenes.map((s) => s.sceneId)
                const on =
                  ids.length === 1 ? ids[0] === sel : ids.join(',') === groupKey
                return (
                  <GroupButton key={ids[0]} g={g} on={on} pick={() => pickGroup(ids)} />
                )
              })}
            </ul>
          </div>
        )
      })}
    </div>
  )
}

/** Sidebar row for one spine entry — a scene, or a merged series of scenes. */
function GroupButton({ g, on, pick }: { g: SceneGroup; on: boolean; pick: () => void }) {
  const single = g.scenes.length === 1
  const s = g.scenes[0]
  const dialogue = g.scenes.reduce((n, x) => n + x.dialogue, 0)
  return (
    <li>
      <button
        onClick={pick}
        aria-current={on || undefined}
        className={`flex w-full items-center gap-2 rounded-[2px] border-2 px-2 py-1.5 text-left transition-colors ${
          on
            ? 'border-gold bg-gold/20 text-cream'
            : 'border-transparent text-cream/75 hover:bg-white/5 hover:text-cream'
        }`}
      >
        {single && s.chapter != null && (
          <span
            className="flex h-5 w-5 shrink-0 items-center justify-center rounded-[2px] bg-ink text-[0.6rem] text-cream"
            style={{ fontFamily: 'var(--font-pixel)' }}
            title="Chapter"
          >
            {s.chapter}
          </span>
        )}
        {!single && (
          <span
            className="flex h-5 w-5 shrink-0 items-center justify-center rounded-[2px] bg-ink/60 text-[0.6rem] text-cream"
            style={{ fontFamily: 'var(--font-pixel)' }}
            title={`${g.scenes.length} merged scenes`}
          >
            ×{g.scenes.length}
          </span>
        )}
        <span className="min-w-0 flex-1 truncate text-xs font-bold">{g.label}</span>
        {dialogue > 0 && <span className="shrink-0 text-[0.6rem] text-cream/40">{dialogue}💬</span>}
      </button>
    </li>
  )
}

function SceneButton({ s, on, pick }: { s: SceneLite; on: boolean; pick: (id: string) => void }) {
  return (
    <li>
      <button
        onClick={() => pick(s.sceneId)}
        aria-current={on || undefined}
        className={`flex w-full items-center gap-2 rounded-[2px] border-2 px-2 py-1.5 text-left transition-colors ${
          on
            ? 'border-gold bg-gold/20 text-cream'
            : 'border-transparent text-cream/75 hover:bg-white/5 hover:text-cream'
        }`}
      >
        {s.chapter != null && (
          <span
            className="flex h-5 w-5 shrink-0 items-center justify-center rounded-[2px] bg-ink text-[0.6rem] text-cream"
            style={{ fontFamily: 'var(--font-pixel)' }}
            title="Chapter"
          >
            {s.chapter}
          </span>
        )}
        <span className="min-w-0 flex-1 truncate text-xs font-bold">{cleanSceneName(s.name)}</span>
        {s.dialogue > 0 && <span className="shrink-0 text-[0.6rem] text-cream/40">{s.dialogue}💬</span>}
      </button>
    </li>
  )
}
