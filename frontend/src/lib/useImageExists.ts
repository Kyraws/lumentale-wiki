import { useEffect, useState } from 'react'

/** Resolve true once an image URL loads — used to gate optional sprites. */
export function useImageExists(url?: string | null): boolean {
  const [ok, setOk] = useState(false)
  useEffect(() => {
    setOk(false)
    if (!url) return
    const img = new Image()
    img.onload = () => setOk(true)
    img.src = url
    return () => {
      img.onload = null
    }
  }, [url])
  return ok
}
