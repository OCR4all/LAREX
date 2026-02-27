import { WEBGL_RESOURCE_POOL } from '@/webgl/editor/webgl-constants'

export class ResourcePool {
  private gl: WebGL2RenderingContext
  private bufferPools = new Map<string, WebGLBuffer[]>()
  private vaoPools = new Map<string, WebGLVertexArrayObject[]>()

  private float32Cache = new Map<string, Float32Array>()
  private uint16Cache = new Map<string, Uint16Array>()

  constructor(gl: WebGL2RenderingContext) {
    this.gl = gl
  }

  acquireBuffer(key: string): WebGLBuffer {
    const pool = this.bufferPools.get(key)
    if (pool && pool.length > 0) {
      return pool.pop()!
    }
    const buffer = this.gl.createBuffer()
    if (!buffer) throw new Error('Failed to create buffer')
    return buffer
  }

  releaseBuffer(key: string, buffer: WebGLBuffer): void {
    const pool = this.bufferPools.get(key) || []
    pool.push(buffer)
    this.bufferPools.set(key, pool)
  }

  getFloat32Array(key: string, requiredSize: number): Float32Array {
    let array = this.float32Cache.get(key)
    if (!array || array.length < requiredSize) {
      const newSize = Math.max(requiredSize, (array?.length || 0) * WEBGL_RESOURCE_POOL.TYPED_ARRAY_GROWTH_FACTOR)
      array = new Float32Array(Math.ceil(newSize))
      this.float32Cache.set(key, array)
    }
    return array
  }

  getUint16Array(key: string, requiredSize: number): Uint16Array {
    let array = this.uint16Cache.get(key)
    if (!array || array.length < requiredSize) {
      const newSize = Math.max(requiredSize, (array?.length || 0) * WEBGL_RESOURCE_POOL.TYPED_ARRAY_GROWTH_FACTOR)
      array = new Uint16Array(Math.ceil(newSize))
      this.uint16Cache.set(key, array)
    }
    return array
  }

  cleanup(): void {
    for (const [_, buffers] of this.bufferPools) {
      buffers.forEach(b => this.gl.deleteBuffer(b))
    }
    this.bufferPools.clear()

    for (const [_, vaos] of this.vaoPools) {
      vaos.forEach(v => this.gl.deleteVertexArray(v))
    }
    this.vaoPools.clear()

    this.float32Cache.clear()
    this.uint16Cache.clear()
  }
}
