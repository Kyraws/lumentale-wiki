import { Link, useParams } from 'react-router-dom'
import { useApi } from '../lib/api'
import type { TutorialSummary } from '../lib/types'
import Sprite from '../components/Sprite'
import { ErrorState, Skeleton, EmptyState } from '../components/States'
import { IconBack } from '../components/Icons'

// Page-local detail shape (camelCase typed endpoint). `asset` is currently always
// absent in v3 but kept optional so the layout is ready if art lands later.
interface TutorialPage {
  ord: number
  textKey?: string | null
  asset?: string | null
}
interface TutorialDetailData extends TutorialSummary {
  pages: TutorialPage[]
}

// Reuse the list-card name logic so the detail header matches the index.
function prettyName(internalName?: string | null, titleKey?: string | null): string {
  if (titleKey && titleKey.trim()) return titleKey.trim()
  const n = internalName ?? ''
  return (
    n
      .replace(/([a-z])([A-Z])/g, '$1 $2')
      .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2')
      .replace(/_/g, ' ')
      .replace(/\s+/g, ' ')
      .trim() || 'Tutorial'
  )
}

export default function TutorialDetail() {
  const { guid = '' } = useParams()
  const { data, loading, error, reload } = useApi<TutorialDetailData>(`/api/tutorials/${guid}`)

  if (error) return <ErrorState message={error} onRetry={reload} />
  if (loading || !data) return <Skeleton className="h-72 w-full" />

  const name = prettyName(data.internalName, data.titleKey)
  const pages = [...(data.pages ?? [])].sort((a, b) => a.ord - b.ord)

  return (
    <div className="flex flex-col gap-5">
      <Link to="/tutorials" className="inline-flex w-fit items-center gap-1 text-sm font-bold text-cream/70 hover:text-cream">
        <IconBack /> Back to Tutorials
      </Link>

      <header className="dialog-box p-5 md:p-7">
        <h1 className="text-lg text-cream text-pixel-shadow md:text-xl">{name}</h1>
        <p className="mt-2 text-sm text-cream/60">
          {pages.length} {pages.length === 1 ? 'page' : 'pages'}
          {data.internalName && data.titleKey && (
            <span className="ml-2 text-cream/40">· {data.internalName}</span>
          )}
        </p>
      </header>

      {pages.length === 0 ? (
        <EmptyState title="This tutorial has no pages." />
      ) : (
        <div className="flex flex-col gap-4">
          {pages.map((p) => (
            <section key={p.ord} className="pixel-panel flex flex-wrap items-start gap-4 p-4 md:p-5">
              <span
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[2px] bg-ink text-cream"
                style={{ fontFamily: 'var(--font-display)', fontSize: '0.7rem' }}
                aria-label={`Page ${p.ord + 1}`}
              >
                {p.ord + 1}
              </span>
              {/* Asset is usually absent — Sprite renders its "?" fallback. */}
              {p.asset && (
                <div className="pixel-screen flex h-24 w-24 shrink-0 items-center justify-center p-2">
                  <Sprite src={p.asset} alt={`Page ${p.ord + 1} art`} size={80} />
                </div>
              )}
              <div className="min-w-0 flex-1">
                {p.textKey ? (
                  <p
                    className="break-words text-sm font-bold leading-relaxed text-ink-soft"
                    style={{ fontFamily: 'var(--font-pixel)' }}
                  >
                    {p.textKey}
                  </p>
                ) : (
                  <p className="text-sm italic text-ink-mute">No text on this page.</p>
                )}
              </div>
            </section>
          ))}
        </div>
      )}
    </div>
  )
}
