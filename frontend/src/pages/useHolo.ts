import { useCallback, useRef } from 'react'

/**
 * Pointer/tilt tracking for the holographic card effect (cards-holo.css).
 *
 * Mirrors the in-game GameCardImage holo material, which drives a sheen by the
 * card's screen-space orientation. Here we feed the pointer position into CSS
 * custom properties (--mx/--my percentage, --rx/--ry tilt deg, --pointer ramp)
 * written straight onto the element — no React re-render per move, so it stays
 * 60fps even with a full grid hovered. Writes are skipped when the element is
 * flagged data-holo-static (e.g. prefers-reduced-motion handled in CSS too).
 *
 * `maxTilt` is the peak rotation in degrees (hero card uses more than a grid cell).
 */
export function useHolo(maxTilt = 10) {
  const ref = useRef<HTMLDivElement | null>(null)
  const raf = useRef<number | null>(null)

  const apply = useCallback(
    (mx: number, my: number, pointer: number) => {
      const el = ref.current
      if (!el) return
      // mx/my are 0..1 fractions across the card.
      const ry = (mx - 0.5) * 2 * maxTilt // left/right → rotateY
      const rx = (0.5 - my) * 2 * maxTilt // up/down → rotateX
      el.style.setProperty('--mx', (mx * 100).toFixed(2))
      el.style.setProperty('--my', (my * 100).toFixed(2))
      el.style.setProperty('--rx', `${rx.toFixed(2)}deg`)
      el.style.setProperty('--ry', `${ry.toFixed(2)}deg`)
      el.style.setProperty('--pointer', pointer.toFixed(2))
    },
    [maxTilt],
  )

  const onPointerMove = useCallback(
    (e: React.PointerEvent<HTMLDivElement>) => {
      const el = ref.current
      if (!el) return
      const r = el.getBoundingClientRect()
      const mx = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width))
      const my = Math.min(1, Math.max(0, (e.clientY - r.top) / r.height))
      if (raf.current != null) cancelAnimationFrame(raf.current)
      raf.current = requestAnimationFrame(() => apply(mx, my, 1))
    },
    [apply],
  )

  const onPointerEnter = useCallback(() => {
    const el = ref.current
    if (el) el.style.setProperty('--pointer', '1')
  }, [])

  const reset = useCallback(() => {
    const el = ref.current
    if (!el) return
    if (raf.current != null) cancelAnimationFrame(raf.current)
    el.style.setProperty('--mx', '50')
    el.style.setProperty('--my', '50')
    el.style.setProperty('--rx', '0deg')
    el.style.setProperty('--ry', '0deg')
    el.style.setProperty('--pointer', '0')
  }, [])

  return { ref, onPointerMove, onPointerEnter, onPointerLeave: reset }
}

/**
 * Rarity → holo intensity ladder, recovered from the live data:
 *   Common 0 < Uncommon 1 < Rare 2 < Power 3 < Event 4 < Kickstarter 5
 * (the numeric `Rarity` field in the card record). In-game the holo material is
 * only switched on for the higher card tiers (GameCardImage$$SetUpByCard gates
 * `set_Holo` on the card/form type), so low tiers stay matte here.
 */
// Decompiled GameCardImage.SetUpByData: holo is enabled ONLY for
// Rarity 3/4/5 (Power, Event, Kickstarter) — Common/Uncommon/Rare are
// matte in-game. All holo tiers share the same material intensity.
const HOLO_BY_RARITY: Record<string, number> = {
  Common: 0,
  Uncommon: 0,
  Rare: 0,
  Power: 1,
  Event: 1,
  Kickstarter: 1,
}

export function holoIntensity(rarity?: string | null): number {
  if (!rarity) return 0
  return HOLO_BY_RARITY[rarity] ?? 0
}
