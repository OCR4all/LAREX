import { afterEach, describe, expect, it, vi } from 'vitest'
import { markRaw, ref } from 'vue'
import { runSettledWorkerPool, useChunkedUpload } from '../use-chunked-upload'

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

describe('chunked upload slicing', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('slices files with the chunk size the server reports', async () => {
    const chunkSizes: number[] = []
    vi.stubGlobal('markRaw', markRaw)
    vi.stubGlobal('$fetch', vi.fn(async (url: string, options?: { body?: unknown }) => {
      if (url.endsWith('/upload-sessions')) {
        return {
          id: 'session',
          projectId: 'project',
          workspaceId: 'workspace',
          status: 'PENDING',
          chunkSizeBytes: 4,
          files: [{ id: 'file', originalFileName: 'page.png', chunkCount: 3 }]
        }
      }
      if (url.endsWith('/chunks')) {
        chunkSizes.push(((options?.body as FormData).get('file') as Blob).size)
      }
      return {}
    }))

    const upload = useChunkedUpload({
      workspaceId: ref('workspace'),
      projectId: ref('project'),
      onBeforeFinalize: async () => false
    })
    upload.addFiles([new File(['0123456789'], 'page.png', { type: 'image/png' })])
    await upload.startUpload()

    expect(chunkSizes).toEqual([4, 4, 2])
    expect(upload.files.value[0]?.status).toBe('uploaded')
  })
})
