import { describe, expect, it, vi } from 'vitest'
import {
  getResponseFileName,
  isCompleteZipBlob,
  prepareDownloadTarget,
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

  it('extracts quoted response filenames and sanitizes path separators', () => {
    const response = new Response('', {
      headers: {
        'content-disposition': 'attachment; filename="folder\\\\result.txt"'
      }
    })

    expect(getResponseFileName(response, 'fallback.txt')).toBe('folder result.txt')
  })

  it('returns null when the user cancels the Save As picker', async () => {
    const originalWindow = (globalThis as typeof globalThis & { window?: unknown }).window
    const showSaveFilePicker = vi.fn().mockRejectedValue({ name: 'AbortError' })
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      value: { isSecureContext: true, showSaveFilePicker }
    })

    try {
      await expect(prepareDownloadTarget('project.zip')).resolves.toBeNull()
      expect(showSaveFilePicker).toHaveBeenCalledWith({ suggestedName: 'project.zip' })
    } finally {
      if (originalWindow === undefined) {
        Reflect.deleteProperty(globalThis, 'window')
      } else {
        Object.defineProperty(globalThis, 'window', { configurable: true, value: originalWindow })
      }
    }
  })

  it('creates an automatic-download target when the picker is unavailable', async () => {
    const originalWindow = (globalThis as typeof globalThis & { window?: unknown }).window
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      value: { isSecureContext: true }
    })

    try {
      const target = await prepareDownloadTarget('project.zip')
      expect(target).not.toBeNull()
    } finally {
      if (originalWindow === undefined) {
        Reflect.deleteProperty(globalThis, 'window')
      } else {
        Object.defineProperty(globalThis, 'window', { configurable: true, value: originalWindow })
      }
    }
  })

  it('streams a response into a selected file and closes the writable', async () => {
    const originalWindow = (globalThis as typeof globalThis & { window?: unknown }).window
    const writes: Array<Blob | ArrayBuffer | Uint8Array> = []
    const close = vi.fn(async () => undefined)
    const abort = vi.fn(async () => undefined)
    const handle = {
      createWritable: vi.fn(async () => ({
        write: vi.fn(async (data: Blob | ArrayBuffer | Uint8Array) => { writes.push(data) }),
        close,
        abort
      }))
    }
    const showSaveFilePicker = vi.fn().mockResolvedValue(handle)
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      value: { isSecureContext: true, showSaveFilePicker }
    })

    try {
      const target = await prepareDownloadTarget('project.zip')
      expect(target).not.toBeNull()
      await target!.saveResponse(new Response(new Uint8Array([1, 2, 3]), {
        headers: {
          'content-length': '3',
          'content-disposition': 'attachment; filename="server-name.zip"'
        }
      }), 'project.zip')

      expect(handle.createWritable).toHaveBeenCalledOnce()
      expect(writes).toHaveLength(1)
      expect(Array.from(writes[0] as Uint8Array)).toEqual([1, 2, 3])
      expect(close).toHaveBeenCalledOnce()
      expect(abort).not.toHaveBeenCalled()
    } finally {
      if (originalWindow === undefined) {
        Reflect.deleteProperty(globalThis, 'window')
      } else {
        Object.defineProperty(globalThis, 'window', { configurable: true, value: originalWindow })
      }
    }
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

  it('distinguishes finalized ZIP archives from truncated responses', async () => {
    const finalizedZip = new Blob([new Uint8Array([
      0x50, 0x4B, 0x05, 0x06,
      0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
      0, 0
    ])])
    const truncatedZip = new Blob([new Uint8Array([
      0x50, 0x4B, 0x03, 0x04,
      1, 2, 3, 4, 5, 6
    ])])

    await expect(isCompleteZipBlob(finalizedZip)).resolves.toBe(true)
    await expect(isCompleteZipBlob(truncatedZip)).resolves.toBe(false)
  })
})
