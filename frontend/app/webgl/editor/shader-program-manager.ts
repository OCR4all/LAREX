import { WEBGL_PROGRAM } from '@/webgl/editor/webgl-constants'
import { createScopedLogger } from '@/services/editor/logger-service'

const log = createScopedLogger('ShaderManager')

export interface ProgramState {
  program: WebGLProgram
  uniforms: Map<string, WebGLUniformLocation | null>
  attributes: Map<string, number>
  lastUsed: number
}

export class ShaderProgramManager {
  private gl: WebGL2RenderingContext
  private programs: Map<string, ProgramState> = new Map()
  private currentProgram: string | null = null

  private uniformCache: Map<string, any> = new Map()

  constructor(gl: WebGL2RenderingContext) {
    this.gl = gl
  }

  /**
     * Register a shader program
     */
  registerProgram(
    name: string,
    vertexShader: string,
    fragmentShader: string
  ): WebGLProgram {
    const program = this.createProgram(vertexShader, fragmentShader)

    this.programs.set(name, {
      program,
      uniforms: new Map(),
      attributes: new Map(),
      lastUsed: Date.now()
    })

    return program
  }

  /**
     * Use a program (only switches if different from current)
     */
  useProgram(name: string): boolean {
    if (this.currentProgram === name) {
      return true // Already active, no state change needed
    }

    const state = this.programs.get(name)
    if (!state) {
      log.error(`Program '${name}' not found`)
      return false
    }

    this.gl.useProgram(state.program)
    this.currentProgram = name
    state.lastUsed = Date.now()

    this.uniformCache.clear()

    return true
  }

  /**
     * Get uniform location (cached)
     */
  getUniformLocation(programName: string, uniformName: string): WebGLUniformLocation | null {
    const state = this.programs.get(programName)
    if (!state) return null

    if (state.uniforms.has(uniformName)) {
      return state.uniforms.get(uniformName)!
    }

    const location = this.gl.getUniformLocation(state.program, uniformName)
    state.uniforms.set(uniformName, location)
    return location
  }

  /**
     * Get attribute location (cached)
     */
  getAttributeLocation(programName: string, attributeName: string): number {
    const state = this.programs.get(programName)
    if (!state) return WEBGL_PROGRAM.ATTRIBUTE_NOT_FOUND

    if (state.attributes.has(attributeName)) {
      return state.attributes.get(attributeName)!
    }

    const location = this.gl.getAttribLocation(state.program, attributeName)
    state.attributes.set(attributeName, location)
    return location
  }

  /**
     * Set uniform with caching to avoid redundant GPU calls
     */
  setUniform1f(programName: string, uniformName: string, value: number): void {
    const cacheKey = `${programName}.${uniformName}`

    if (this.uniformCache.get(cacheKey) === value) {
      return // Skip redundant update
    }

    const location = this.getUniformLocation(programName, uniformName)
    if (location !== null) {
      this.gl.uniform1f(location, value)
      this.uniformCache.set(cacheKey, value)
    }
  }

  setUniform2f(programName: string, uniformName: string, x: number, y: number): void {
    const cacheKey = `${programName}.${uniformName}`
    const value = `${x},${y}`

    if (this.uniformCache.get(cacheKey) === value) {
      return
    }

    const location = this.getUniformLocation(programName, uniformName)
    if (location !== null) {
      this.gl.uniform2f(location, x, y)
      this.uniformCache.set(cacheKey, value)
    }
  }

  setUniform4f(
    programName: string,
    uniformName: string,
    x: number,
    y: number,
    z: number,
    w: number
  ): void {
    const cacheKey = `${programName}.${uniformName}`
    const value = `${x},${y},${z},${w}`

    if (this.uniformCache.get(cacheKey) === value) {
      return
    }

    const location = this.getUniformLocation(programName, uniformName)
    if (location !== null) {
      this.gl.uniform4f(location, x, y, z, w)
      this.uniformCache.set(cacheKey, value)
    }
  }

  setUniform1i(programName: string, uniformName: string, value: number): void {
    const cacheKey = `${programName}.${uniformName}`

    if (this.uniformCache.get(cacheKey) === value) {
      return
    }

    const location = this.getUniformLocation(programName, uniformName)
    if (location !== null) {
      this.gl.uniform1i(location, value)
      this.uniformCache.set(cacheKey, value)
    }
  }

  /**
     * Set uniforms in batch (more efficient)
     */
  setUniforms(programName: string, uniforms: Record<string, any>): void {
    for (const [name, value] of Object.entries(uniforms)) {
      if (typeof value === 'number') {
        this.setUniform1f(programName, name, value)
      } else if (Array.isArray(value)) {
        if (value.length === 2) {
          this.setUniform2f(programName, name, value[0], value[1])
        } else if (value.length === 4) {
          this.setUniform4f(programName, name, value[0], value[1], value[2], value[3])
        }
      }
    }
  }

  /**
     * Get current program name
     */
  getCurrentProgram(): string | null {
    return this.currentProgram
  }

  /**
     * Get program by name
     */
  getProgram(name: string): WebGLProgram | null {
    return this.programs.get(name)?.program ?? null
  }

  /**
     * Create and compile shader program
     */
  private createProgram(vertexSource: string, fragmentSource: string): WebGLProgram {
    const vertexShader = this.createShader(this.gl.VERTEX_SHADER, vertexSource)
    const fragmentShader = this.createShader(this.gl.FRAGMENT_SHADER, fragmentSource)

    const program = this.gl.createProgram()!
    this.gl.attachShader(program, vertexShader)
    this.gl.attachShader(program, fragmentShader)
    this.gl.linkProgram(program)

    if (!this.gl.getProgramParameter(program, this.gl.LINK_STATUS)) {
      const error = this.gl.getProgramInfoLog(program)
      this.gl.deleteProgram(program)
      throw new Error(`Failed to link program: ${error}`)
    }

    this.gl.deleteShader(vertexShader)
    this.gl.deleteShader(fragmentShader)

    return program
  }

  private createShader(type: number, source: string): WebGLShader {
    const shader = this.gl.createShader(type)!
    this.gl.shaderSource(shader, source)
    this.gl.compileShader(shader)

    if (!this.gl.getShaderParameter(shader, this.gl.COMPILE_STATUS)) {
      const error = this.gl.getShaderInfoLog(shader)
      this.gl.deleteShader(shader)
      throw new Error(`Failed to compile shader: ${error}`)
    }

    return shader
  }

  /**
     * Clean up all programs
     */
  cleanup(): void {
    for (const [_, state] of this.programs) {
      this.gl.deleteProgram(state.program)
    }
    this.programs.clear()
    this.uniformCache.clear()
    this.currentProgram = null
  }

  /**
     * Get usage statistics
     */
  getStats(): {
    totalPrograms: number
    currentProgram: string | null
    programUsage: Array<{ name: string, lastUsed: number }>
  } {
    const programUsage = Array.from(this.programs.entries()).map(([name, state]) => ({
      name,
      lastUsed: state.lastUsed
    }))

    return {
      totalPrograms: this.programs.size,
      currentProgram: this.currentProgram,
      programUsage
    }
  }
}
