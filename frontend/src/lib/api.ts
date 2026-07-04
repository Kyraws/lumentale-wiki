import { useEffect, useState } from 'react'

// The dataset never changes at runtime and every response is cacheable, so a
// tiny in-memory cache keyed by path keeps navigation instant. See §2.
const cache = new Map<string, unknown>()
const inflight = new Map<string, Promise<unknown>>()

export interface ApiError {
  status: number
  error: string
  message: string
  path: string
}

export async function api<T>(path: string): Promise<T> {
  if (cache.has(path)) return cache.get(path) as T
  if (inflight.has(path)) return inflight.get(path) as Promise<T>

  const p = (async () => {
    const res = await fetch(path)
    if (!res.ok) {
      let body: Partial<ApiError> = {}
      try {
        body = await res.json()
      } catch {
        /* non-JSON error */
      }
      throw new Error(body.message || `${res.status} ${res.statusText}`)
    }
    const data = (await res.json()) as T
    cache.set(path, data)
    inflight.delete(path)
    return data
  })()

  inflight.set(path, p as Promise<unknown>)
  return p
}

export interface UseApi<T> {
  data: T | null
  loading: boolean
  error: string | null
  reload: () => void
}

/** Fetch an `/api/...` path with loading + error state. */
export function useApi<T>(path: string | null): UseApi<T> {
  const [data, setData] = useState<T | null>(path && cache.has(path) ? (cache.get(path) as T) : null)
  const [loading, setLoading] = useState<boolean>(!!path && !cache.has(path))
  const [error, setError] = useState<string | null>(null)
  const [nonce, setNonce] = useState(0)

  useEffect(() => {
    if (!path) return
    let alive = true
    setError(null)
    if (!cache.has(path)) setLoading(true)
    api<T>(path)
      .then((d) => {
        if (alive) {
          setData(d)
          setLoading(false)
        }
      })
      .catch((e: Error) => {
        if (alive) {
          setError(e.message)
          setLoading(false)
        }
      })
    return () => {
      alive = false
    }
  }, [path, nonce])

  return { data, loading, error, reload: () => setNonce((n) => n + 1) }
}
