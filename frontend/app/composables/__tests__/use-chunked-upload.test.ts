import { describe, expect, it } from 'vitest'
import { runSettledWorkerPool } from '../use-chunked-upload'

describe('chunked upload worker pool', () => {
  it('keeps concurrency bounded for very large file sets', async () => {
    const files = Array.from({ length: 1500 }, (_, index) => index)
    let active = 0
    let maximumActive = 0
    let completed = 0

    await runSettledWorkerPool(files, 3, async () => {
      active += 1
      maximumActive = Math.max(maximumActive, active)
      await Promise.resolve()
      completed += 1
      active -= 1
    })

    expect(completed).toBe(1500)
    expect(maximumActive).toBe(3)
  })

  it('continues processing after an individual file fails', async () => {
    const completed: number[] = []
    await runSettledWorkerPool([1, 2, 3], 2, async (file) => {
      if (file === 2) throw new Error('failed')
      completed.push(file)
    })

    expect(completed).toEqual([1, 3])
  })
})
