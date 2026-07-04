/**
 * Resolve a map's friendly display name. Prefers the localized LOCATION friendly
 * name ({@code displayName} from the API, present for 85/300 maps), then the raw
 * {@code mapName}, then a cleaned-up form of the internal codename (e.g.
 * "1_House_Costa_Linda_Map" → "House Costa Linda").
 */
export function cleanMapName(internal: string, mapName?: string | null, displayName?: string | null): string {
  if (displayName && displayName.trim()) return displayName.trim()
  if (mapName && mapName.trim()) return mapName.trim()
  return (
    internal
      .replace(/^[0-9]+[_-]?/, '') // leading index
      .replace(/[_-]+/g, ' ')
      .replace(/\bMap\b/gi, '')
      .replace(/\s+/g, ' ')
      .trim() || internal
  )
}
