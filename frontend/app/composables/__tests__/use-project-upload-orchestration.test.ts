import { describe, expect, it } from 'vitest'
import {
  getUploadSessionRealtimeProgressPercent,
  shouldApplyUploadRealtimeSequence
} from '../use-project-upload-orchestration'

describe('upload session realtime progress', () => {
  it('does not replace local chunk progress with inactive conversion progress while uploading', () => {
    expect(getUploadSessionRealtimeProgressPercent({
      sessionId: 'session-1',
      status: 'UPLOADING',
      processedFiles: 0,
      failedFiles: 0,
      totalFiles: 100,
      processingProgressPercent: 0
    })).toBeUndefined()
  })

  it('uses conversion progress after processing starts', () => {
    expect(getUploadSessionRealtimeProgressPercent({
      sessionId: 'session-1',
      status: 'PROCESSING',
      processedFiles: 40,
      failedFiles: 0,
      totalFiles: 100,
      processingProgressPercent: 35
    })).toBe(35)
  })
})

describe('upload realtime ordering', () => {
  it('ignores duplicate and out-of-order events from the current backend stream', () => {
    expect(shouldApplyUploadRealtimeSequence('stream-1', 10, 'stream-1', 10)).toBe(false)
    expect(shouldApplyUploadRealtimeSequence('stream-1', 10, 'stream-1', 9)).toBe(false)
    expect(shouldApplyUploadRealtimeSequence('stream-1', 10, 'stream-1', 11)).toBe(true)
  })

  it('accepts a reset sequence after the backend restarts', () => {
    expect(shouldApplyUploadRealtimeSequence('stream-1', 100, 'stream-2', 1)).toBe(true)
  })
})
