import { useHolo, holoIntensity } from './useHolo'
import './cards-holo.css'

interface HoloCardProps {
  art?: string | null
  alt: string
  rarity?: string | null
  /** hero = the big detail card (more tilt, glow); otherwise a grid cell. */
  hero?: boolean
  className?: string
  /** The game's per-card holo pipeline (GameCardData → CardShader): the foil
   *  texture, the artwork mask restricting where it shows, and the foil tiling.
   *  When present we render the decompiled in-game layering; otherwise the
   *  synthetic CSS foil approximates it. */
  holo?: string | null
  mask?: string | null
  holoTilingX?: number | null
  holoTilingY?: number | null
}

/**
 * A single holographic card surface, mirroring the in-game CardShader
 * (Animon/Cards/CardShader, material CardMaterial — decompiled mapping in
 * data/curated/card_holo_material.json):
 *   art (_MainTex)
 *   + per-card foil texture (HoloTexture, tiled per GameCardData, parallax
 *     against the view angle) × grain (_HoloMask "Grainy 8") masked to the
 *     artwork (_AnimonHoloMask = mask.png), intensity ~1.4
 *   + border holo: the two extracted border colors (#d76dff ↔ #9bff7c)
 *     sweeping with the view angle, masked by the generic border mask
 *   + specular glare (screen-space sheen)
 */
export default function HoloCard({
  art, alt, rarity, hero = false, className = '',
  holo, mask, holoTilingX, holoTilingY,
}: HoloCardProps) {
  const { ref, onPointerMove, onPointerEnter, onPointerLeave } = useHolo(hero ? 12 : 7)
  const intensity = holoIntensity(rarity)
  const holoOn = intensity > 0
  // Power/Event ship their own foil+mask; Kickstarter holo uses the game's
  // DEFAULT Holography texture across the whole card (no per-card assets).
  const gameHolo = holoOn

  const vars: Record<string, string> = { '--holo': String(intensity) }
  if (gameHolo) {
    vars['--holo-url'] = holo ? `url("${holo}")` : 'url("/data/cards/_shader/default_holo.png")'
    vars['--mask-url'] = mask ? `url("${mask}")` : 'url("/data/cards/_shader/round_mask.png")'
    vars['--holo-tx'] = String(holoTilingX || (holo ? 3 : 1.5))
    vars['--holo-ty'] = String(holoTilingY || (holo ? 3 : 1.5))
  }

  return (
    <div
      ref={ref}
      className={`holo-card ${hero ? 'holo-hero' : ''} ${className}`}
      data-holo-on={holoOn ? '1' : '0'}
      data-rarity={rarity ?? undefined}
      style={vars as React.CSSProperties}
      onPointerMove={onPointerMove}
      onPointerEnter={onPointerEnter}
      onPointerLeave={onPointerLeave}
    >
      {art ? (
        <img className="holo-art" src={art} alt={alt} loading="lazy" />
      ) : (
        <div className="holo-art flex items-center justify-center bg-cream-2 text-ink-mute/60">
          <span style={{ fontFamily: 'var(--font-display)', fontSize: hero ? 96 : 40 }}>?</span>
        </div>
      )}
      {gameHolo && (
        <>
          <div className="holo-foil-game" aria-hidden />
          <div className="holo-border-game" aria-hidden />
        </>
      )}
      <div className="holo-glare" aria-hidden />
    </div>
  )
}
