import { Link } from 'react-router-dom'
import { useApi } from '../lib/api'

/**
 * "Starts quest X" / "Completes quest X" badges for a story scene, fetched from
 * /api/story/scene/quests (quests linked to the scene by a shared flag). New
 * component (not an edit to the shared SceneReader): the Story browser and the
 * standalone scene page render it above the scene flow. Renders nothing when the
 * scene touches no quest flag.
 */
interface SceneQuestLink {
  guid: string
  internalName: string
  name?: string
  relation: string // 'starts' | 'completes' | 'related'
  flag?: string
  mode?: string
}

const REL: Record<string, { label: string; tint: string }> = {
  starts: { label: 'Starts', tint: 'var(--color-gold)' },
  completes: { label: 'Completes', tint: 'var(--color-sky)' },
  related: { label: 'Relates to', tint: 'var(--color-lumen)' },
}

// strongest relation wins when a scene touches several of a quest's flags
const RANK: Record<string, number> = { starts: 0, completes: 1, related: 2 }

export default function SceneQuestBadges({ sceneId }: { sceneId: string }) {
  const { data } = useApi<SceneQuestLink[]>(
    sceneId ? `/api/story/scene/quests?id=${encodeURIComponent(sceneId)}&lang=en` : null,
  )
  if (!data || data.length === 0) return null

  // Collapse to one badge per quest, keeping its strongest relation (starts > completes > related).
  const byQuest = new Map<string, SceneQuestLink>()
  for (const q of data) {
    const prev = byQuest.get(q.guid)
    if (!prev || (RANK[q.relation] ?? 9) < (RANK[prev.relation] ?? 9)) byQuest.set(q.guid, q)
  }
  const links = [...byQuest.values()].sort((a, b) => (RANK[a.relation] ?? 9) - (RANK[b.relation] ?? 9))

  return (
    <div className="flex flex-wrap items-center gap-2">
      {links.map((q, i) => {
        const rel = REL[q.relation] ?? REL.related
        return (
          <Link
            key={i}
            to={`/quests/${q.guid}`}
            className="inline-flex items-center gap-2 rounded-[2px] border-2 px-3 py-1.5 text-sm font-bold text-ink hover:opacity-80"
            style={{ borderColor: rel.tint, backgroundColor: 'color-mix(in srgb, ' + rel.tint + ' 14%, transparent)' }}
            title={q.flag}
          >
            <span
              className="rounded-[2px] px-1.5 py-0.5 text-[0.5rem] font-extrabold uppercase tracking-wide text-night"
              style={{ backgroundColor: rel.tint }}
            >
              {rel.label} quest
            </span>
            {q.name || q.internalName}
          </Link>
        )
      })}
    </div>
  )
}
