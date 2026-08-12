import { describe, expect, it } from 'vitest'
import { fitCutoutDimensions } from '../cutout-sizing'

describe('fitCutoutDimensions', () => {
  it('uses the preferred height when the cutout fits in the available width', () => {
    expect(fitCutoutDimensions({
      sourceWidth: 400,
      sourceHeight: 100,
      targetHeight: 50,
      availableWidth: 300
    })).toMatchObject({ width: 200, height: 50, scale: 0.5 })
  })

  it('scales long cutouts down so their right edge remains visible', () => {
    expect(fitCutoutDimensions({
      sourceWidth: 1200,
      sourceHeight: 100,
      targetHeight: 72,
      availableWidth: 360
    })).toMatchObject({ width: 360, height: 30, scale: 0.3 })
  })

  it('honors the maximum height without stretching a narrow cutout past it', () => {
    expect(fitCutoutDimensions({
      sourceWidth: 100,
      sourceHeight: 100,
      targetHeight: 220,
      maxHeight: 160,
      availableWidth: 500
    })).toMatchObject({ width: 160, height: 160, scale: 1.6 })
  })
})
