// Shared furniture presentation helpers for the Furniture list + detail pages.
//
// Furniture has no clean game category/set enum in the extracted data — only a
// numeric rarity (1–3), price, footprint, and an is_carpet flag. (The
// Addressables GUID→bundle map that would expose the "anispace_<theme>" set a
// piece belongs to was never extracted; see HANDOFF §6.) So rarity is the one
// classifying axis we can show with confidence.
//
// Rarity codes seen in the DB: 1 (313 pieces), 2 (333), 3 (225). The game has no
// inline rarity_label, so we name the three tiers by the conventional ramp.
export interface RarityMeta {
  label: string
  bg: string
  fg: string
}

export const RARITY: Record<number, RarityMeta> = {
  1: { label: 'Common', bg: '#d8d2c2', fg: '#3a352b' },
  2: { label: 'Rare', bg: '#bcd6ec', fg: '#1f3a52' },
  3: { label: 'Epic', bg: '#e7c9f0', fg: '#4a2557' },
}

export function rarityMeta(rarity?: string | number | null): RarityMeta | undefined {
  if (rarity == null) return undefined
  const n = typeof rarity === 'number' ? rarity : parseInt(rarity, 10)
  return Number.isFinite(n) ? RARITY[n] : undefined
}
