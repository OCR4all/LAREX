import { describe, expect, it, vi } from 'vitest'
import { defaultLabelSetTransferName, requestLabelSetTransfer } from '../label-set-transfer'

describe('label set transfer naming', () => {
  it('uses the original name for moves and the backend copy name for copies', () => {
    expect(defaultLabelSetTransferName('Labels', 'MOVE')).toBe('Labels')
    expect(defaultLabelSetTransferName('Labels', 'COPY')).toBe('Labels (Copy)')
  })

  it('submits an available name without opening the rename slideover', async () => {
    const request = vi.fn().mockResolvedValue({ status: 'COMPLETED' })
    const chooseName = vi.fn()

    await expect(requestLabelSetTransfer({ name: 'Labels', nameStatus: 'available', request, chooseName }))
      .resolves.toEqual({ status: 'COMPLETED' })
    expect(request).toHaveBeenCalledWith(undefined)
    expect(chooseName).not.toHaveBeenCalled()
  })

  it('opens the rename slideover when the precheck finds the name taken', async () => {
    const request = vi.fn().mockResolvedValue({ status: 'PENDING' })
    const chooseName = vi.fn().mockResolvedValue('Other labels')

    await requestLabelSetTransfer({ name: 'Labels (Copy)', nameStatus: 'taken', request, chooseName })

    expect(chooseName).toHaveBeenCalledWith('Labels (Copy)')
    expect(request).toHaveBeenCalledWith('Other labels')
  })

  it('lets the server decide when the precheck has not finished', async () => {
    const request = vi.fn().mockResolvedValue({ status: 'PENDING' })
    const chooseName = vi.fn()

    await requestLabelSetTransfer({ name: 'Labels', nameStatus: 'checking', request, chooseName })

    expect(request).toHaveBeenCalledWith(undefined)
    expect(chooseName).not.toHaveBeenCalled()
  })

  it('stops without a transfer when the rename is cancelled', async () => {
    const request = vi.fn()
    const chooseName = vi.fn().mockResolvedValue(null)

    await expect(requestLabelSetTransfer({ name: 'Labels', nameStatus: 'taken', request, chooseName }))
      .resolves.toBeNull()
    expect(request).not.toHaveBeenCalled()
  })

  it('opens the rename slideover if the server finds a conflict after an available precheck', async () => {
    const request = vi.fn()
      .mockRejectedValueOnce({ statusCode: 409, data: { code: 'LABEL_SET_NAME_CONFLICT' } })
      .mockResolvedValueOnce({ status: 'COMPLETED' })
    const chooseName = vi.fn().mockResolvedValue('Other labels')

    await requestLabelSetTransfer({ name: 'Labels', nameStatus: 'available', request, chooseName })

    expect(chooseName).toHaveBeenCalledWith('Labels')
    expect(request.mock.calls).toEqual([[undefined], ['Other labels']])
  })
})
