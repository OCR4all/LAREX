import { effectScope, nextTick, ref, type EffectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useServerTablePagination, useTablePagination } from '../use-table-pagination'

const screen = vi.hoisted(() => ({ small: null as unknown as ReturnType<typeof ref<boolean>> }))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => screen.small }))

let scope: EffectScope
beforeEach(() => {
  screen.small = ref(true)
  scope = effectScope()
})
afterEach(() => scope.stop())

describe('responsive table pagination', () => {
  it('appends mobile rows, resets after filtering, and keeps desktop pages separate', async () => {
    const data = ref([1, 2, 3, 4, 5])
    const page = ref(1)
    const table = scope.run(() => useTablePagination(data, page, 2))!
    expect(table.rows.value).toEqual([1, 2])
    table.infiniteScroll.loadMore()
    expect(table.rows.value).toEqual([1, 2, 3, 4])
    table.infiniteScroll.loadMore()
    expect(table.rows.value).toEqual([1, 2, 3, 4, 5])
    expect(table.infiniteScroll.hasMore).toBe(false)
    table.infiniteScroll.loadMore()
    expect(page.value).toBe(3)

    data.value = [5, 3, 1]
    await nextTick()
    expect(table.rows.value).toEqual([5, 3])
    screen.small.value = false
    expect(page.value).toBe(1)
    page.value = 2
    expect(table.rows.value).toEqual([1])
    table.infiniteScroll.loadMore()
    expect(page.value).toBe(2)
    screen.small.value = true
    expect(table.rows.value).toEqual([5, 3])
  })
})

describe('server table pagination', () => {
  function setup(fetchPage: (page: number) => Promise<number[]>) {
    const firstPage = ref([1, 2])
    const total = ref(6)
    const page = ref(1)
    const pending = ref(false)
    const query = ref('all')
    const table = scope.run(() => useServerTablePagination(firstPage, total, page, pending, query, fetchPage))!
    return { ...table, firstPage, total, page, pending, query }
  }

  it('loads one page at a time, retains previous rows, and stops at the total', async () => {
    let resolvePage!: (rows: number[]) => void
    const fetchPage = vi.fn(() => new Promise<number[]>((resolve) => {
      resolvePage = resolve
    }))
    const table = setup(fetchPage)
    const request = table.infiniteScroll.loadMore()
    await table.infiniteScroll.loadMore()
    expect(fetchPage).toHaveBeenCalledExactlyOnceWith(2)
    expect(table.rows.value).toEqual([1, 2])
    resolvePage([3, 4])
    await request
    expect(table.rows.value).toEqual([1, 2, 3, 4])
    const lastRequest = table.infiniteScroll.loadMore()
    expect(fetchPage).toHaveBeenLastCalledWith(3)
    resolvePage([5, 6])
    await lastRequest
    await table.infiniteScroll.loadMore()
    expect(fetchPage).toHaveBeenCalledTimes(2)
    expect(table.infiniteScroll.hasMore).toBe(false)
    expect(table.page.value).toBe(1)
  })

  it('retries the same page after failure and preserves loaded rows', async () => {
    const fetchPage = vi.fn<(page: number) => Promise<number[]>>()
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce([3, 4])
    const table = setup(fetchPage)
    await table.infiniteScroll.loadMore()
    expect(table.infiniteScroll.error).toContain('Try again')
    expect(table.rows.value).toEqual([1, 2])
    await table.infiniteScroll.loadMore()
    expect(fetchPage.mock.calls).toEqual([[2], [2]])
    expect(table.rows.value).toEqual([1, 2, 3, 4])
    expect(table.infiniteScroll.error).toBeNull()
  })

  it('stops automatic loading on an empty page without skipping it on retry', async () => {
    const fetchPage = vi.fn<(page: number) => Promise<number[]>>()
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([3, 4])
    const table = setup(fetchPage)
    await table.infiniteScroll.loadMore()
    expect(table.infiniteScroll.error).toContain('No more rows')
    expect(table.rows.value).toEqual([1, 2])
    await table.infiniteScroll.loadMore()
    expect(fetchPage.mock.calls).toEqual([[2], [2]])
    expect(table.rows.value).toEqual([1, 2, 3, 4])
  })

  it('discards responses from previous filters and resets appended rows on refresh', async () => {
    let resolvePage!: (rows: number[]) => void
    const fetchPage = vi.fn(() => new Promise<number[]>((resolve) => {
      resolvePage = resolve
    }))
    const table = setup(fetchPage)
    const oldRequest = table.infiniteScroll.loadMore()
    table.query.value = 'filtered'
    table.firstPage.value = [10, 20]
    resolvePage([3, 4])
    await oldRequest
    expect(table.rows.value).toEqual([10, 20])
    const newRequest = table.infiniteScroll.loadMore()
    expect(fetchPage).toHaveBeenLastCalledWith(2)
    resolvePage([30, 40])
    await newRequest
    expect(table.rows.value).toEqual([10, 20, 30, 40])
    table.firstPage.value = [100, 200]
    expect(table.rows.value).toEqual([100, 200])
  })

  it('waits for the first page and cancels stale mobile requests when switching to desktop', async () => {
    let resolvePage!: (rows: number[]) => void
    const fetchPage = vi.fn(() => new Promise<number[]>((resolve) => {
      resolvePage = resolve
    }))
    const table = setup(fetchPage)
    table.pending.value = true
    await table.infiniteScroll.loadMore()
    expect(fetchPage).not.toHaveBeenCalled()
    table.pending.value = false
    const request = table.infiniteScroll.loadMore()
    screen.small.value = false
    table.page.value = 2
    table.firstPage.value = [3, 4]
    resolvePage([3, 4])
    await request
    expect(table.rows.value).toEqual([3, 4])
    await table.infiniteScroll.loadMore()
    expect(fetchPage).toHaveBeenCalledTimes(1)
    screen.small.value = true
    expect(table.page.value).toBe(1)
  })
})
