import { describe, expect, it, vi } from 'vitest'
import { UniformStateCache } from './uniform-state-cache'

function createGlMock() {
  const locations = new Map<string, WebGLUniformLocation | null>()
  const gl = {
    getUniformLocation: vi.fn((_program: WebGLProgram, name: string) => {
      if (!locations.has(name)) {
        locations.set(name, {} as WebGLUniformLocation)
      }
      return locations.get(name) ?? null
    }),
    uniform1f: vi.fn(),
    uniform1i: vi.fn(),
    uniform2f: vi.fn(),
    uniform4f: vi.fn()
  } as unknown as WebGL2RenderingContext

  return { gl, locations }
}

describe('UniformStateCache', () => {
  it('caches uniform locations, including missing uniforms', () => {
    const { gl, locations } = createGlMock()
    locations.set('missing', null)
    const cache = new UniformStateCache(gl)
    const program = {} as WebGLProgram

    expect(cache.getLocation(program, 'color')).toBe(cache.getLocation(program, 'color'))
    expect(cache.getLocation(program, 'missing')).toBeNull()
    expect(cache.getLocation(program, 'missing')).toBeNull()
    expect(gl.getUniformLocation).toHaveBeenCalledTimes(2)
  })

  it('skips identical writes and forwards changed values', () => {
    const { gl } = createGlMock()
    const cache = new UniformStateCache(gl)
    const location = {} as WebGLUniformLocation
    const integerLocation = {} as WebGLUniformLocation

    cache.uniform1f(location, 1)
    cache.uniform1f(location, 1)
    cache.uniform1f(location, 2)
    cache.uniform1i(integerLocation, 1)
    cache.uniform1i(integerLocation, 1)
    cache.uniform2f(location, 2, 3)
    cache.uniform2f(location, 2, 3)
    cache.uniform4f(location, 1, 2, 3, 4)
    cache.uniform4f(location, 1, 2, 3, 4)

    expect(gl.uniform1f).toHaveBeenCalledTimes(2)
    expect(gl.uniform1i).toHaveBeenCalledTimes(1)
    expect(gl.uniform2f).toHaveBeenCalledTimes(1)
    expect(gl.uniform4f).toHaveBeenCalledTimes(1)
  })

  it('forgets cached locations and values when cleared', () => {
    const { gl } = createGlMock()
    const cache = new UniformStateCache(gl)
    const program = {} as WebGLProgram
    const location = cache.getLocation(program, 'zoom')

    cache.uniform1f(location, 2)
    cache.clear()
    cache.getLocation(program, 'zoom')
    cache.uniform1f(location, 2)

    expect(gl.getUniformLocation).toHaveBeenCalledTimes(2)
    expect(gl.uniform1f).toHaveBeenCalledTimes(2)
  })
})
