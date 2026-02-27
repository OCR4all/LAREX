import { describe, expect, it } from 'vitest'
import { glslFloatLiteral } from './glsl-utils'

describe('glslFloatLiteral', () => {
  it('formats integers as float literals', () => {
    expect(glslFloatLiteral(2)).toBe('2.0')
    expect(glslFloatLiteral(0)).toBe('0.0')
    expect(glslFloatLiteral(-3)).toBe('-3.0')
  })

  it('keeps decimal values as-is', () => {
    expect(glslFloatLiteral(0.5)).toBe('0.5')
    expect(glslFloatLiteral(2.0)).toBe('2.0')
    expect(glslFloatLiteral(-1.25)).toBe('-1.25')
  })

  it('keeps scientific notation as-is', () => {
    expect(glslFloatLiteral(1e-4)).toBe('0.0001')
  })

  it('guards against non-finite values', () => {
    expect(glslFloatLiteral(Number.NaN)).toBe('0.0')
    expect(glslFloatLiteral(Number.POSITIVE_INFINITY)).toBe('0.0')
    expect(glslFloatLiteral(Number.NEGATIVE_INFINITY)).toBe('0.0')
  })
})
