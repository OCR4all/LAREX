import { describe, expect, it, vi } from 'vitest'
import {
  getResponseFileName,
  readResponseBlob,
  type BackgroundJobControls
} from '../use-background-downloads'

describe('background downloads', () => {
  it('extracts RFC 5987 response filenames', () => {
    const response = new Response('', {
      headers: {
        'content-disposition': 'attachment; filename*=UTF-8\'\'export%20file.zip'
      }
    })

    expect(getResponseFileName(response, 'fallback.zip')).toBe('export file.zip')
  })

  it('reports byte progress while reading streamed responses', async () => {
    const chunks = [
      new Uint8Array([1, 2, 3]),
      new Uint8Array([4, 5])
    ]
    const response = new Response(new Blob(chunks), {
      headers: {
        'content-length': '5',
        'content-type': 'application/octet-stream'
      }
    })
    const update = vi.fn<BackgroundJobControls['update']>()

    const blob = await readResponseBlob(response, { update })

    expect(blob.size).toBe(5)
    expect(update).toHaveBeenCalledWith(expect.objectContaining({
      progressPercent: 100,
      detail: '5 B / 5 B'
    }))
  })
})
