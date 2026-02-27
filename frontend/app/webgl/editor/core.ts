/**
 * Core WebGL utilities for shader creation and program management
 */

import { WEBGL_CORE } from '@/webgl/editor/webgl-constants'

/**
 * Creates and compiles a WebGL shader
 * @param gl - The WebGL context
 * @param type - The shader type (gl.VERTEX_SHADER or gl.FRAGMENT_SHADER)
 * @param source - The shader source code
 * @returns The compiled shader
 * @throws {Error} If shader compilation fails
 */
export function createShader(gl: WebGL2RenderingContext, type: number, source: string): WebGLShader {
  const shader = gl.createShader(type)
  if (!shader) {
    throw new Error('Failed to create shader')
  }

  gl.shaderSource(shader, source)
  gl.compileShader(shader)
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    const error = new Error(`An error occurred compiling the shaders: ${gl.getShaderInfoLog(shader)}`)
    gl.deleteShader(shader)
    throw error
  }
  return shader
}

/**
 * Creates and links a WebGL program from vertex and fragment shaders
 * @param gl - The WebGL context
 * @param vsSource - The vertex shader source code
 * @param fsSource - The fragment shader source code
 * @returns The linked shader program
 * @throws {Error} If program linking fails
 */
export function createProgram(gl: WebGL2RenderingContext, vsSource: string, fsSource: string): WebGLProgram {
  const vs = createShader(gl, gl.VERTEX_SHADER, vsSource)
  const fs = createShader(gl, gl.FRAGMENT_SHADER, fsSource)
  const program = gl.createProgram()
  if (!program) {
    throw new Error('Failed to create shader program')
  }

  gl.attachShader(program, vs)
  gl.attachShader(program, fs)
  gl.linkProgram(program)
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    const error = new Error(`Unable to initialize the shader program: ${gl.getProgramInfoLog(program)}`)
    gl.deleteProgram(program)
    throw error
  }
  gl.deleteShader(vs)
  gl.deleteShader(fs)
  return program
}

/**
 * Initializes a WebGL 2 context with stencil buffer support
 * @param canvas - The canvas element
 * @returns The WebGL context
 * @throws {Error} If WebGL 2 is not supported
 */
export function initWebGLContext(canvas: HTMLCanvasElement): WebGL2RenderingContext {
  const gl = canvas.getContext('webgl2', { stencil: true, alpha: true })
  if (!gl) {
    throw new Error('WebGL 2 not supported')
  }
  return gl
}

/**
 * Sets up WebGL viewport and clears buffers
 * @param gl - The WebGL context
 * @param width - Viewport width
 * @param height - Viewport height
 * @param clearColor - Clear color [r, g, b, a]
 */
export function setupViewport(
  gl: WebGL2RenderingContext,
  width: number,
  height: number,
  clearColor: [number, number, number, number] = WEBGL_CORE.DEFAULT_CLEAR_COLOR as unknown as [number, number, number, number]
): void {
  gl.viewport(WEBGL_CORE.VIEWPORT_ORIGIN_X, WEBGL_CORE.VIEWPORT_ORIGIN_Y, width, height)
  gl.clearColor(...clearColor)
  gl.clear(gl.COLOR_BUFFER_BIT)
}

/**
 * Attribute configuration for VAO creation
 */
export interface AttributeConfig {
  name: string
  size: number
  data?: Float32Array
  usage?: number
  stride?: number
  offset?: number
  buffer?: WebGLBuffer
}

/**
 * Creates and configures a vertex array object with attribute pointers
 * @param gl - The WebGL context
 * @param program - The shader program
 * @param attributes - Array of attribute configurations
 * @returns The configured VAO
 */
export function createVAO(
  gl: WebGL2RenderingContext,
  program: WebGLProgram,
  attributes: AttributeConfig[]
): WebGLVertexArrayObject {
  const vao = gl.createVertexArray()
  if (!vao) {
    throw new Error('Failed to create VAO')
  }

  gl.bindVertexArray(vao)

  attributes.forEach((attr) => {
    const buffer = gl.createBuffer()
    if (!buffer) {
      throw new Error(`Failed to create buffer for attribute ${attr.name}`)
    }

    gl.bindBuffer(gl.ARRAY_BUFFER, buffer)

    if (attr.data) {
      gl.bufferData(gl.ARRAY_BUFFER, attr.data, attr.usage || gl.STATIC_DRAW)
    }

    const location = gl.getAttribLocation(program, attr.name)
    gl.enableVertexAttribArray(location)
    gl.vertexAttribPointer(location, attr.size, gl.FLOAT, false, attr.stride || 0, attr.offset || 0)

    if (attr.buffer !== undefined) {
      attr.buffer = buffer
    }
  })

  return vao
}
