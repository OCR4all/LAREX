import type { DictionaryCheckTokensResponse, DictionaryTokenCheckResult } from '@/types/dictionary'

type QueueEntry = {
  workspaceId: string
  dictionaryId: string
  includeSuggestions: boolean
  limit: number
  tokens: Set<string>
  resolvers: Array<() => void>
  rejecters: Array<(error: unknown) => void>
  timer: ReturnType<typeof setTimeout> | null
}

const pendingQueues = new Map<string, QueueEntry>()

function queueKey(workspaceId: string, dictionaryId: string, includeSuggestions: boolean, limit: number) {
  return `${workspaceId}:${dictionaryId}:${includeSuggestions ? '1' : '0'}:${limit}`
}

export function useDictionaryTokenLookup() {
  const tokenResults = useState<Record<string, Record<string, DictionaryTokenCheckResult>>>('dictionary-token-results', () => ({}))
  const pendingTokens = useState<Record<string, true>>('dictionary-token-pending', () => ({}))

  function cacheKey(workspaceId: string, dictionaryId: string) {
    return `${workspaceId}:${dictionaryId}`
  }

  function pendingKey(workspaceId: string, dictionaryId: string, token: string) {
    return `${workspaceId}:${dictionaryId}:${token}`
  }

  function getTokenResult(workspaceId: string, dictionaryId: string, token: string): DictionaryTokenCheckResult | null {
    const normalizedToken = token?.trim()
    if (!normalizedToken) return null
    return tokenResults.value[cacheKey(workspaceId, dictionaryId)]?.[normalizedToken] ?? null
  }

  function isTokenPending(workspaceId: string, dictionaryId: string, token: string): boolean {
    const normalizedToken = token?.trim()
    if (!normalizedToken) return false
    return Boolean(pendingTokens.value[pendingKey(workspaceId, dictionaryId, normalizedToken)])
  }

  function storeResults(workspaceId: string, dictionaryId: string, results: DictionaryTokenCheckResult[]) {
    const key = cacheKey(workspaceId, dictionaryId)
    const next = {
      ...(tokenResults.value[key] ?? {})
    }

    for (const result of results ?? []) {
      if (!result?.token) continue
      next[result.token] = result
    }

    tokenResults.value = {
      ...tokenResults.value,
      [key]: next
    }
  }

  function markTokensPending(workspaceId: string, dictionaryId: string, tokens: string[]) {
    if (tokens.length === 0) return
    const next = { ...pendingTokens.value }
    for (const token of tokens) {
      next[pendingKey(workspaceId, dictionaryId, token)] = true
    }
    pendingTokens.value = next
  }

  function clearTokensPending(workspaceId: string, dictionaryId: string, tokens: Iterable<string>) {
    const next = { ...pendingTokens.value }
    for (const token of tokens) {
      delete next[pendingKey(workspaceId, dictionaryId, token)]
    }
    pendingTokens.value = next
  }

  async function flushQueue(key: string) {
    const entry = pendingQueues.get(key)
    if (!entry) return
    pendingQueues.delete(key)

    try {
      const response = await $fetch<DictionaryCheckTokensResponse>(
        `/api/workspaces/${entry.workspaceId}/dictionaries/${entry.dictionaryId}/check-tokens`,
        {
          method: 'POST',
          body: {
            tokens: Array.from(entry.tokens),
            includeSuggestions: entry.includeSuggestions,
            limit: entry.limit
          }
        }
      )
      storeResults(entry.workspaceId, entry.dictionaryId, response.results ?? [])
      clearTokensPending(entry.workspaceId, entry.dictionaryId, entry.tokens)
      for (const resolve of entry.resolvers) resolve()
    } catch (error) {
      clearTokensPending(entry.workspaceId, entry.dictionaryId, entry.tokens)
      for (const reject of entry.rejecters) reject(error)
    }
  }

  function ensureTokenResults(options: {
    workspaceId: string
    dictionaryId: string
    tokens: string[]
    includeSuggestions?: boolean
    limit?: number
  }) {
    const workspaceId = options.workspaceId?.trim()
    const dictionaryId = options.dictionaryId?.trim()
    if (!workspaceId || !dictionaryId) {
      return Promise.resolve()
    }

    const includeSuggestions = Boolean(options.includeSuggestions)
    const limit = Math.max(1, Math.min(Number(options.limit ?? 5) || 5, 10))
    const requestedTokens = [...new Set((options.tokens ?? []).map(token => token?.trim()).filter(Boolean) as string[])]
    if (requestedTokens.length === 0) {
      return Promise.resolve()
    }

    const missingTokens = requestedTokens.filter((token) => {
      const cached = getTokenResult(workspaceId, dictionaryId, token)
      if (!cached) return true
      return includeSuggestions && (cached.suggestions?.length ?? 0) === 0 && !cached.known
    })

    if (missingTokens.length === 0) {
      return Promise.resolve()
    }

    const key = queueKey(workspaceId, dictionaryId, includeSuggestions, limit)
    const existing = pendingQueues.get(key)
    if (existing) {
      for (const token of missingTokens) existing.tokens.add(token)
      markTokensPending(workspaceId, dictionaryId, missingTokens)
      return new Promise<void>((resolve, reject) => {
        existing.resolvers.push(resolve)
        existing.rejecters.push(reject)
      })
    }

    const queue: QueueEntry = {
      workspaceId,
      dictionaryId,
      includeSuggestions,
      limit,
      tokens: new Set(missingTokens),
      resolvers: [],
      rejecters: [],
      timer: null
    }

    markTokensPending(workspaceId, dictionaryId, missingTokens)

    const promise = new Promise<void>((resolve, reject) => {
      queue.resolvers.push(resolve)
      queue.rejecters.push(reject)
    })

    queue.timer = setTimeout(() => {
      void flushQueue(key)
    }, 40)

    pendingQueues.set(key, queue)
    return promise
  }

  function invalidateDictionary(workspaceId: string, dictionaryId: string) {
    const key = cacheKey(workspaceId, dictionaryId)
    const next = { ...tokenResults.value }
    delete next[key]
    tokenResults.value = next

    const pendingPrefix = `${workspaceId}:${dictionaryId}:`
    const pendingNext = { ...pendingTokens.value }
    for (const key of Object.keys(pendingNext)) {
      if (key.startsWith(pendingPrefix)) {
        delete pendingNext[key]
      }
    }
    pendingTokens.value = pendingNext
  }

  function invalidateToken(workspaceId: string, dictionaryId: string, token: string) {
    const key = cacheKey(workspaceId, dictionaryId)
    const existing = tokenResults.value[key]
    if (!existing) return
    const next = { ...existing }
    delete next[token]
    tokenResults.value = {
      ...tokenResults.value,
      [key]: next
    }
    const pendingNext = { ...pendingTokens.value }
    delete pendingNext[pendingKey(workspaceId, dictionaryId, token)]
    pendingTokens.value = pendingNext
  }

  return {
    ensureTokenResults,
    getTokenResult,
    isTokenPending,
    invalidateDictionary,
    invalidateToken
  }
}
