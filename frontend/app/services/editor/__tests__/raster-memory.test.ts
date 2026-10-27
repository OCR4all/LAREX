import { expect, it } from 'vitest'
import { releaseRasterMemory, reserveRasterMemory } from '../raster-memory'

it('bounds aggregate texture memory and restores reservations after a failed upload', () => {
  const a = {}, b = {}, c = {}
  try {
    reserveRasterMemory(a, 4096, 4096)
    reserveRasterMemory(b, 4096, 4096)
    expect(() => reserveRasterMemory(c, 1, 1)).toThrow('display memory limit')
    releaseRasterMemory(b)
    const rollback = reserveRasterMemory(a, 5000, 5000)
    rollback()
    reserveRasterMemory(b, 4096, 4096)
  } finally {
    releaseRasterMemory(a)
    releaseRasterMemory(b)
    releaseRasterMemory(c)
  }
})

it('allows explicit full-resolution textures beyond the automatic preview budget', () => {
  const original = {}, preview = {}
  try {
    expect(() => reserveRasterMemory(original, 19218, 12623, false)).not.toThrow()
    expect(() => reserveRasterMemory(preview, 3200, 2102)).not.toThrow()
  } finally {
    releaseRasterMemory(original)
    releaseRasterMemory(preview)
  }
})
