/**
 * WebGL utility functions for common rendering operations
 */

export interface Scale {
  scaleX: number
  scaleY: number
  rotationCos?: number
  rotationSin?: number
  rotationAspect?: number
}

export interface Offset {
  offsetX: number
  offsetY: number
}

/**
 * Sets common uniforms for 2D transformations
 * @param gl - The WebGL context
 * @param program - The shader program
 * @param scale - Scale object with scaleX and scaleY properties
 * @param offset - Offset object with offsetX and offsetY properties
 * @param zoom - Zoom level
 */
export function setTransformUniforms(
  gl: WebGL2RenderingContext,
  program: WebGLProgram,
  scale: Scale,
  offset: Offset,
  zoom: number
): void {
  const scaleLocation = gl.getUniformLocation(program, 'u_scale')
  const offsetLocation = gl.getUniformLocation(program, 'u_offset')
  const zoomLocation = gl.getUniformLocation(program, 'u_zoom')
  const rotationLocation = gl.getUniformLocation(program, 'u_rotation')
  const canvasAspectLocation = gl.getUniformLocation(program, 'u_canvasAspect')

  if (!scaleLocation || !offsetLocation || !zoomLocation) return

  gl.uniform2f(scaleLocation, scale.scaleX, scale.scaleY)
  gl.uniform2f(offsetLocation, offset.offsetX, offset.offsetY)
  gl.uniform1f(zoomLocation, zoom)

  if (rotationLocation) {
    const rotationCos = (typeof scale.rotationCos === 'number' && isFinite(scale.rotationCos)) ? scale.rotationCos : 1
    const rotationSin = (typeof scale.rotationSin === 'number' && isFinite(scale.rotationSin)) ? scale.rotationSin : 0
    gl.uniform2f(rotationLocation, rotationCos, rotationSin)
  }

  if (canvasAspectLocation) {
    const fallbackAspect = (gl.canvas.width > 0 && gl.canvas.height > 0) ? (gl.canvas.width / gl.canvas.height) : 1
    const canvasAspect = (typeof scale.rotationAspect === 'number' && isFinite(scale.rotationAspect) && scale.rotationAspect > 0)
      ? scale.rotationAspect
      : fallbackAspect
    gl.uniform1f(canvasAspectLocation, canvasAspect)
  }
}

/**
 * Sets color uniform for a shader program
 * @param gl - The WebGL context
 * @param program - The shader program
 * @param color - Color array [r, g, b, a]
 * @param uniformName - Name of the color uniform (default: 'u_color')
 */
export function setColorUniform(
  gl: WebGL2RenderingContext,
  program: WebGLProgram,
  color: readonly [number, number, number, number],
  uniformName = 'u_color'
): void {
  const colorLocation = gl.getUniformLocation(program, uniformName)
  if (!colorLocation) return
  const [r, g, b, a] = color
  gl.uniform4f(colorLocation, r, g, b, a)
}

/**
 * Sets point size uniform for a shader program
 * @param gl - The WebGL context
 * @param program - The shader program
 * @param size - Point size
 * @param uniformName - Name of the point size uniform (default: 'u_pointSize')
 */
export function setPointSizeUniform(
  gl: WebGL2RenderingContext,
  program: WebGLProgram,
  size: number,
  uniformName = 'u_pointSize'
): void {
  const sizeLocation = gl.getUniformLocation(program, uniformName)
  gl.uniform1f(sizeLocation, size)
}

/**
 * Validates scale object to prevent rendering errors
 * @param scale - Scale object to validate
 * @returns True if scale is valid for rendering
 */
export function isValidScale(scale: Scale | null | undefined): scale is Scale {
  return (
    scale !== null
    && scale !== undefined
    && scale.scaleX !== 0
    && scale.scaleY !== 0
    && isFinite(scale.scaleX)
    && isFinite(scale.scaleY)
  )
}

/**
 * Enables and configures blending for transparency
 * @param gl - The WebGL context
 * @param srcFactor - Source blend factor (default: gl.SRC_ALPHA)
 * @param dstFactor - Destination blend factor (default: gl.ONE_MINUS_SRC_ALPHA)
 */
export function enableBlending(
  gl: WebGL2RenderingContext,
  srcFactor: number = gl.SRC_ALPHA,
  dstFactor: number = gl.ONE_MINUS_SRC_ALPHA
): void {
  gl.enable(gl.BLEND)
  gl.blendFunc(srcFactor, dstFactor)
}

/**
 * Disables blending
 * @param gl - The WebGL context
 */
export function disableBlending(gl: WebGL2RenderingContext): void {
  gl.disable(gl.BLEND)
}

/**
 * Sets up stencil buffer for masking operations
 * @param gl - The WebGL context
 * @param func - Stencil function (default: gl.ALWAYS)
 * @param ref - Reference value (default: 1)
 * @param mask - Stencil mask (default: 0xFF)
 * @param sfail - Action when stencil test fails (default: gl.KEEP)
 * @param dpfail - Action when stencil test passes but depth test fails (default: gl.KEEP)
 * @param dppass - Action when both tests pass (default: gl.REPLACE)
 */
export function setupStencilBuffer(
  gl: WebGL2RenderingContext,
  func: number = gl.ALWAYS,
  ref = 1,
  mask = 0xFF,
  sfail: number = gl.KEEP,
  dpfail: number = gl.KEEP,
  dppass: number = gl.REPLACE
): void {
  gl.enable(gl.STENCIL_TEST)
  gl.stencilFunc(func, ref, mask)
  gl.stencilOp(sfail, dpfail, dppass)
}

/**
 * Disables stencil buffer operations
 * @param gl - The WebGL context
 */
export function disableStencilBuffer(gl: WebGL2RenderingContext): void {
  gl.disable(gl.STENCIL_TEST)
}
