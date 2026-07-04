import { useState } from 'react'

interface SpriteProps {
  src?: string | null
  alt: string
  /** pixel box size in px (square) */
  size?: number
  bob?: boolean
  className?: string
}

/**
 * Crisp pixel sprite with a graceful fallback to a "?" silhouette when the
 * asset is missing (some forms/items have no art). image-rendering:pixelated
 * comes from the global `.sprite` rule.
 */
export default function Sprite({ src, alt, size = 64, bob = false, className = '' }: SpriteProps) {
  const [failed, setFailed] = useState(false)
  const box = { width: size, height: size }

  if (!src || failed) {
    return (
      <div
        style={box}
        className={`flex items-center justify-center text-ink-mute/60 ${className}`}
        aria-label={`${alt} (no sprite)`}
        role="img"
      >
        <span style={{ fontFamily: 'var(--font-display)', fontSize: size * 0.32 }}>?</span>
      </div>
    )
  }

  return (
    <img
      src={src}
      alt={alt}
      width={size}
      height={size}
      loading="lazy"
      onError={() => setFailed(true)}
      style={box}
      className={`sprite object-contain ${bob ? 'anim-bob' : ''} ${className}`}
    />
  )
}
