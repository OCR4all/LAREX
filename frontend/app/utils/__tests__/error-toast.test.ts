import { beforeEach, describe, expect, it, vi } from 'vitest'

const copyTextToClipboard = vi.fn()
const add = vi.fn()

vi.mock('../clipboard', () => ({
  copyTextToClipboard
}))

describe('error-toast utils', () => {
  beforeEach(() => {
    add.mockReset()
    copyTextToClipboard.mockReset()
    copyTextToClipboard.mockResolvedValue(true)
    vi.stubGlobal('useToast', () => ({ add }))
    vi.stubGlobal('window', {
      location: { href: 'https://example.test/project/alpha' }
    })
  })

  it('adds a copy action when the API payload includes an error id', async () => {
    const { showApiErrorToast } = await import('../error-toast')

    showApiErrorToast({
      title: 'Import Failed',
      error: {
        data: {
          message: 'Import job failed',
          code: 'IMPORT_FAILED',
          errorId: 'err-456',
          path: '/api/admin/import/jobs'
        }
      },
      fallback: 'Fallback'
    })

    expect(add).toHaveBeenCalledTimes(1)
    const payload = add.mock.calls[0]?.[0]
    expect(payload.title).toBe('Import Failed')
    expect(payload.description).toBe('Import job failed')
    expect(payload.actions).toHaveLength(1)
    expect(payload.actions[0].label).toBe('Copy error')

    await payload.actions[0].onClick({} as MouseEvent)

    expect(copyTextToClipboard).toHaveBeenCalledWith(
      expect.stringContaining('Error ID: err-456'),
      expect.objectContaining({ successTitle: 'Error details copied' })
    )
  })
})
