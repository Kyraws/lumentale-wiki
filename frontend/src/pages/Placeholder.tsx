import { Link } from 'react-router-dom'

/** Coming-soon / 404 panel for routes not built in the flagship pass. */
export default function Placeholder({ title, notFound = false }: { title: string; notFound?: boolean }) {
  return (
    <div className="mx-auto max-w-lg py-16 text-center">
      <div className="dialog-box mx-auto mb-6 inline-block scanlines px-6 py-5">
        <p className="text-xs text-lumen" style={{ fontFamily: 'var(--font-display)' }}>
          {notFound ? '404' : 'WIP'}
        </p>
      </div>
      <h1 className="mb-3 text-base text-cream text-pixel-shadow">{title}</h1>
      <p className="mb-6 text-sm text-cream/70">
        {notFound
          ? 'That page wandered off the route map.'
          : 'This section is on the way. The flagship build covers the Dex, World Map and Story first.'}
      </p>
      <Link to="/" className="pixel-btn pixel-btn--primary">
        Back to Home
      </Link>
    </div>
  )
}
