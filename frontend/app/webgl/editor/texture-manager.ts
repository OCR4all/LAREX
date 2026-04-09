import { WEBGL_TEXTURE } from '@/webgl/editor/webgl-constants'

export interface TextureSlot {
  texture: WebGLTexture
  width: number
  height: number
  lastUsed: number
}

export class TextureManager {
  private gl: WebGL2RenderingContext
  private textureSlots: Map<number, TextureSlot> = new Map()
  private textureCache: Map<string, { slot: number, texture: WebGLTexture }> = new Map()
  private maxTextureSlots: number

  constructor(gl: WebGL2RenderingContext) {
    this.gl = gl
    this.maxTextureSlots = gl.getParameter(gl.MAX_COMBINED_TEXTURE_IMAGE_UNITS)

    this.reserveSlot(WEBGL_TEXTURE.RESERVED_MAIN_SLOT)
  }

  private reserveSlot(slot: number): void {
    const texture = this.gl.createTexture()!
    this.textureSlots.set(slot, {
      texture,
      width: 0,
      height: 0,
      lastUsed: Date.now()
    })
  }

  /**
     * Get or create a texture slot
     */
  acquireSlot(key: string): number {
    if (this.textureCache.has(key)) {
      const cached = this.textureCache.get(key)!
      const slot = this.textureSlots.get(cached.slot)!
      slot.lastUsed = Date.now()
      return cached.slot
    }

    let availableSlot: number = WEBGL_TEXTURE.SLOT_NOT_FOUND
    for (let i = WEBGL_TEXTURE.FIRST_DYNAMIC_SLOT; i < this.maxTextureSlots; i++) { // Start from 1 (0 is reserved)
      if (!this.textureSlots.has(i)) {
        availableSlot = i
        break
      }
    }

    if (availableSlot === WEBGL_TEXTURE.SLOT_NOT_FOUND) {
      availableSlot = this.evictLRU()
    }

    this.reserveSlot(availableSlot)
    this.textureCache.set(key, {
      slot: availableSlot,
      texture: this.textureSlots.get(availableSlot)!.texture
    })

    return availableSlot
  }

  /**
     * Upload image to texture slot
     */
  uploadImage(key: string, image: HTMLImageElement | ImageBitmap): number {
    const slot = this.acquireSlot(key)
    const slotData = this.textureSlots.get(slot)!

    this.gl.activeTexture(this.gl.TEXTURE0 + slot)
    this.gl.bindTexture(this.gl.TEXTURE_2D, slotData.texture)

    this.gl.pixelStorei(this.gl.UNPACK_FLIP_Y_WEBGL, true)
    this.gl.texImage2D(
      this.gl.TEXTURE_2D,
      WEBGL_TEXTURE.BASE_MIP_LEVEL,
      this.gl.RGBA,
      this.gl.RGBA,
      this.gl.UNSIGNED_BYTE,
      image
    )

    this.gl.texParameteri(this.gl.TEXTURE_2D, this.gl.TEXTURE_MIN_FILTER, this.gl.LINEAR)
    this.gl.texParameteri(this.gl.TEXTURE_2D, this.gl.TEXTURE_MAG_FILTER, this.gl.LINEAR)
    this.gl.texParameteri(this.gl.TEXTURE_2D, this.gl.TEXTURE_WRAP_S, this.gl.CLAMP_TO_EDGE)
    this.gl.texParameteri(this.gl.TEXTURE_2D, this.gl.TEXTURE_WRAP_T, this.gl.CLAMP_TO_EDGE)

    slotData.width = image.width
    slotData.height = image.height
    slotData.lastUsed = Date.now()

    return slot
  }

  /**
     * Bind texture to slot without upload
     */
  bindTexture(key: string, unit: number): boolean {
    const cached = this.textureCache.get(key)
    if (!cached) return false

    this.gl.activeTexture(this.gl.TEXTURE0 + unit)
    this.gl.bindTexture(this.gl.TEXTURE_2D, cached.texture)

    const slot = this.textureSlots.get(cached.slot)!
    slot.lastUsed = Date.now()

    return true
  }

  /**
     * Evict least recently used texture
     */
  private evictLRU(): number {
    let oldestSlot: number = WEBGL_TEXTURE.SLOT_NOT_FOUND
    let oldestTime = Date.now()

    for (const [slot, data] of this.textureSlots) {
      if (slot === WEBGL_TEXTURE.RESERVED_MAIN_SLOT) continue // Don't evict reserved slot

      if (data.lastUsed < oldestTime) {
        oldestTime = data.lastUsed
        oldestSlot = slot
      }
    }

    if (oldestSlot !== WEBGL_TEXTURE.SLOT_NOT_FOUND) {
      for (const [key, value] of this.textureCache) {
        if (value.slot === oldestSlot) {
          this.textureCache.delete(key)
          break
        }
      }

      const slotData = this.textureSlots.get(oldestSlot)!
      this.gl.deleteTexture(slotData.texture)
      this.textureSlots.delete(oldestSlot)
    }

    return oldestSlot
  }

  /**
     * Get texture dimensions
     */
  getTextureDimensions(key: string): { width: number, height: number } | null {
    const cached = this.textureCache.get(key)
    if (!cached) return null

    const slot = this.textureSlots.get(cached.slot)
    if (!slot) return null

    return { width: slot.width, height: slot.height }
  }

  /**
     * Clear specific texture
     */
  releaseTexture(key: string): void {
    const cached = this.textureCache.get(key)
    if (!cached) return

    const slotData = this.textureSlots.get(cached.slot)
    if (slotData && cached.slot !== WEBGL_TEXTURE.RESERVED_MAIN_SLOT) { // Don't release reserved slot
      this.gl.deleteTexture(slotData.texture)
      this.textureSlots.delete(cached.slot)
    }

    this.textureCache.delete(key)
  }

  /**
     * Clear all textures
     */
  cleanup(): void {
    for (const [_, slotData] of this.textureSlots) {
      this.gl.deleteTexture(slotData.texture)
    }
    this.textureSlots.clear()
    this.textureCache.clear()
  }

  /**
     * Get cache statistics
     */
  getStats(): {
    totalSlots: number
    usedSlots: number
    cachedTextures: number
    availableSlots: number
  } {
    return {
      totalSlots: this.maxTextureSlots,
      usedSlots: this.textureSlots.size,
      cachedTextures: this.textureCache.size,
      availableSlots: this.maxTextureSlots - this.textureSlots.size
    }
  }
}
