import type { Glyph } from '@/types/virtual-keyboard'

import { ofetch } from 'ofetch'

type BackendCharacterSearchResponse = {
  query: string
  offset: number
  limit: number
  total: number
  items: Array<{
    source: 'unicode' | 'mufi'
    codePoint: number
    codePointHex: string
    utf8: string
    description: string
    isPua: boolean
  }>
}

const globalFetch = (globalThis as typeof globalThis & { $fetch?: typeof ofetch }).$fetch
const fetcher: typeof ofetch = globalFetch ?? ofetch

export const GlyphService = {
  async search(
    query: string,
    sources = { mufi: true, unicode: false },
    page = 0,
    pageSize = 50,
    options?: { isPua?: boolean }
  ) {
    const offset = Math.max(0, page) * pageSize
    const source: Array<'mufi' | 'unicode'> = []
    if (sources.mufi) source.push('mufi')
    if (sources.unicode) source.push('unicode')

    if (source.length === 0) {
      return { data: [], total: 0, hasMore: false }
    }

    const res = (await fetcher('/api/characters/search', {
      query: {
        q: query,
        offset,
        limit: pageSize,
        source,
        isPua: options?.isPua
      }
    })) as BackendCharacterSearchResponse

    const data: Glyph[] = res.items.map(item => ({
      codepoint: item.codePointHex,
      utf8: item.utf8,
      description: item.description,
      source: item.source
    }))

    return {
      data,
      total: res.total,
      hasMore: res.offset + res.limit < res.total
    }
  }
}
