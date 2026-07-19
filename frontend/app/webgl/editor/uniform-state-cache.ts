type UniformValues = readonly number[]

/**
 * Caches WebGL uniform locations and the last value written to each location.
 *
 * Uniform values belong to a program, so they remain valid while switching
 * between programs. A cache shared by all renderers using the same WebGL
 * context can therefore safely skip identical writes across draw calls.
 */
export class UniformStateCache {
  private locations = new WeakMap<WebGLProgram, Map<string, WebGLUniformLocation | null>>()
  private values = new WeakMap<WebGLUniformLocation, UniformValues>()

  constructor(private readonly gl: WebGL2RenderingContext) {}

  getLocation(program: WebGLProgram, name: string): WebGLUniformLocation | null {
    let programLocations = this.locations.get(program)
    if (!programLocations) {
      programLocations = new Map()
      this.locations.set(program, programLocations)
    }

    if (programLocations.has(name)) {
      return programLocations.get(name) ?? null
    }

    const location = this.gl.getUniformLocation(program, name)
    programLocations.set(name, location)
    return location
  }

  uniform1f(location: WebGLUniformLocation | null, value: number): void {
    if (!location || this.has1Value(location, value)) return
    this.gl.uniform1f(location, value)
  }

  uniform1i(location: WebGLUniformLocation | null, value: number): void {
    if (!location || this.has1Value(location, value)) return
    this.gl.uniform1i(location, value)
  }

  uniform2f(location: WebGLUniformLocation | null, x: number, y: number): void {
    if (!location || this.has2Values(location, x, y)) return
    this.gl.uniform2f(location, x, y)
  }

  uniform4f(location: WebGLUniformLocation | null, x: number, y: number, z: number, w: number): void {
    if (!location || this.has4Values(location, x, y, z, w)) return
    this.gl.uniform4f(location, x, y, z, w)
  }

  clear(): void {
    this.locations = new WeakMap()
    this.values = new WeakMap()
  }

  private has1Value(location: WebGLUniformLocation, value: number): boolean {
    const previousValue = this.values.get(location)
    if (previousValue?.length === 1 && Object.is(previousValue[0], value)) return true
    this.values.set(location, [value])
    return false
  }

  private has2Values(location: WebGLUniformLocation, x: number, y: number): boolean {
    const previousValue = this.values.get(location)
    if (
      previousValue?.length === 2
      && Object.is(previousValue[0], x)
      && Object.is(previousValue[1], y)
    ) return true
    this.values.set(location, [x, y])
    return false
  }

  private has4Values(location: WebGLUniformLocation, x: number, y: number, z: number, w: number): boolean {
    const previousValue = this.values.get(location)
    if (
      previousValue?.length === 4
      && Object.is(previousValue[0], x)
      && Object.is(previousValue[1], y)
      && Object.is(previousValue[2], z)
      && Object.is(previousValue[3], w)
    ) return true
    this.values.set(location, [x, y, z, w])
    return false
  }
}
