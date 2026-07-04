import { Link } from 'react-router-dom'

/** Skeleton block for loading states (shimmer; respects reduced-motion). */
export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`anim-shimmer rounded-[2px] bg-ink/10 ${className}`} />
}

/** Grid of skeleton cards while a list loads. */
export function GridSkeleton({ count = 12 }: { count?: number }) {
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="pixel-panel h-40 p-3">
          <Skeleton className="mx-auto h-20 w-20" />
          <Skeleton className="mx-auto mt-3 h-3 w-16" />
        </div>
      ))}
    </div>
  )
}

/** Full-screen-ish error with a recovery path (retry / home). */
export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="mx-auto max-w-md py-16 text-center" role="alert">
      <div className="dialog-box mx-auto mb-6 inline-block px-4 py-3 text-2xl">×_×</div>
      <h2 className="mb-2 text-sm text-cream text-pixel-shadow">A wild error appeared!</h2>
      <p className="mb-6 text-sm text-cream/70">{message}</p>
      <div className="flex justify-center gap-3">
        {onRetry && (
          <button className="pixel-btn pixel-btn--primary" onClick={onRetry}>
            Retry
          </button>
        )}
        <Link to="/" className="pixel-btn">
          Home
        </Link>
      </div>
    </div>
  )
}

/** Friendly empty state. */
export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="pixel-screen mx-auto max-w-md p-8 text-center text-ink-soft">
      <p className="text-sm font-extrabold text-ink">{title}</p>
      {hint && <p className="mt-1 text-sm text-ink-mute">{hint}</p>}
    </div>
  )
}
