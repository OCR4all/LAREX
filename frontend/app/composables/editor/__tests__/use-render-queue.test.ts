import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { RenderPriority, useRenderQueue } from '../use-render-queue'

describe('useRenderQueue', () => {
  let animationFrames: FrameRequestCallback[]
  let nextFrameId: number

  beforeEach(() => {
    animationFrames = []
    nextFrameId = 1
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      animationFrames.push(callback)
      return nextFrameId++
    })
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('continues scheduling after a burst of consecutive frames', () => {
    const render = vi.fn()
    const { scheduleRender } = useRenderQueue(render)

    for (let frame = 0; frame < 8; frame++) {
      scheduleRender(RenderPriority.NORMAL, `frame-${frame}`)
      expect(animationFrames).toHaveLength(1)
      animationFrames.shift()!(frame * 16)
      expect(render).toHaveBeenCalledTimes(frame + 1)
    }
  })

  it('batches repeated requests into one frame', () => {
    const render = vi.fn()
    const { scheduleRender } = useRenderQueue(render, { enableMonitoring: true })

    scheduleRender(RenderPriority.NORMAL, 'first')
    scheduleRender(RenderPriority.NORMAL, 'second')

    expect(animationFrames).toHaveLength(1)
    animationFrames.shift()!(0)
    expect(render).toHaveBeenCalledOnce()
  })
})
