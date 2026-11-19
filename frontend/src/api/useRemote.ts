import { useEffect, useState } from 'react'
import { normalizeApiError } from './errors'
import type { NormalizedApiError } from './errors'

export function useRemote<T>(load: () => Promise<T>) {
  const [state, setState] = useState<{ data?: T; error?: NormalizedApiError; loading: boolean }>({ loading: true })
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    let current = true
    setState({ loading: true })
    load().then(data => { if (current) setState({ data, loading: false }) }, error => {
      if (current) setState({ error: normalizeApiError(error), loading: false })
    })
    return () => { current = false }
  }, [load, revision])
  return { ...state, reload: () => setRevision(value => value + 1) }
}
