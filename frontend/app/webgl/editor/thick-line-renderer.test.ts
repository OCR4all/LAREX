import { describe, expect, it, vi } from 'vitest'
import { ThickLineRenderer, type LineData } from './thick-line-renderer'

const square = [{ x: 0, y: 0 }, { x: 1, y: 0 }, { x: 1, y: 1 }, { x: 0, y: 1 }]
const VERTEX_COMPONENTS = 9

function createGl() {
  const gl = {
    VERTEX_SHADER: 1, FRAGMENT_SHADER: 2, COMPILE_STATUS: 3, LINK_STATUS: 4,
    ARRAY_BUFFER: 5, ELEMENT_ARRAY_BUFFER: 6, FLOAT: 7, DYNAMIC_DRAW: 8, TRIANGLES: 9, UNSIGNED_INT: 10,
    canvas: { width: 100, height: 100 },
    createShader: vi.fn(() => ({})), shaderSource: vi.fn(), compileShader: vi.fn(),
    getShaderParameter: vi.fn(() => true), deleteShader: vi.fn(),
    createProgram: vi.fn(() => ({})), attachShader: vi.fn(), linkProgram: vi.fn(),
    getProgramParameter: vi.fn(() => true), useProgram: vi.fn(),
    createVertexArray: vi.fn(() => ({})), bindVertexArray: vi.fn(),
    createBuffer: vi.fn(() => ({})), bindBuffer: vi.fn(), bufferData: vi.fn(),
    getAttribLocation: vi.fn(() => 0), enableVertexAttribArray: vi.fn(), vertexAttribPointer: vi.fn(),
    getUniformLocation: vi.fn(() => ({})),
    uniform1f: vi.fn(), uniform2f: vi.fn(), uniform4f: vi.fn(),
    drawElements: vi.fn(), drawArrays: vi.fn()
  }
  return gl
}

function createRenderer() {
  const gl = createGl()
  const renderer = new ThickLineRenderer(gl as unknown as WebGL2RenderingContext)
  renderer.init()
  return { gl, renderer }
}

const scale = { scaleX: 1, scaleY: 1 }
const view = { zoom: 1, offsetX: 0, offsetY: 0 }
const line = (overrides: Partial<LineData> = {}): LineData => ({
  points: square, color: [1, 0, 0, 1], thickness: 2, isClosed: true, ...overrides
})

describe('batched thick lines', () => {
  it('keeps each line\'s strip triangles and carries color and thickness per vertex', () => {
    const { renderer } = createRenderer()
    const open = line({ isClosed: false, color: [0, 1, 0, 0.5], thickness: 3 })
    const { vertices, indices } = renderer.buildLineBatchBuffers([line(), open])

    // Four vertices per segment: a closed square has four segments, an open one three.
    expect(vertices.length).toBe((16 + 12) * VERTEX_COMPONENTS)
    expect(indices.length).toBe((14 + 10) * 3)
    expect(Array.from(indices.slice(0, 6))).toEqual([0, 1, 2, 1, 2, 3])
    // The second line's triangles never reference the first line's vertices.
    expect(Math.min(...indices.slice(14 * 3))).toBe(16)

    const strip = renderer.generateThickLineVertices(square, true)
    expect(Array.from(vertices.slice(0, 4))).toEqual([
      strip.positions[0], strip.positions[1], strip.normals[0], strip.normals[1]
    ])
    expect(Array.from(vertices.slice(4, VERTEX_COMPONENTS))).toEqual([1, 0, 0, 1, 2])
    expect(Array.from(vertices.slice(16 * VERTEX_COMPONENTS + 4, 17 * VERTEX_COMPONENTS))).toEqual([0, 1, 0, 0.5, 3])
  })

  it('uploads buffers only when the lines change', () => {
    const { gl, renderer } = createRenderer()
    const uploads = () => gl.bufferData.mock.calls.length

    renderer.drawMultipleThickLines([line()], scale, view)
    const initialUploads = uploads()
    expect(gl.drawElements).toHaveBeenLastCalledWith(gl.TRIANGLES, 14 * 3, gl.UNSIGNED_INT, 0)

    renderer.drawMultipleThickLines([line({ color: [1, 0, 0, 1] })], scale, { zoom: 2, offsetX: 1, offsetY: 1 })
    expect(uploads()).toBe(initialUploads)
    expect(gl.drawElements).toHaveBeenCalledTimes(2)

    const changes: LineData[][] = [
      [line({ color: [0, 0, 1, 1] })],
      [line({ thickness: 4 })],
      [line({ points: [...square] })],
      [line({ isClosed: false })],
      [line(), line()]
    ]
    let expectedUploads = initialUploads
    for (const lines of changes) {
      renderer.drawMultipleThickLines(lines, scale, view)
      expectedUploads += 2
      expect(uploads()).toBe(expectedUploads)
    }

    renderer.drawMultipleThickLines(changes.at(-1)!, scale, view)
    expect(uploads()).toBe(expectedUploads)
    renderer.invalidateGeometry('any')
    renderer.drawMultipleThickLines(changes.at(-1)!, scale, view)
    expect(uploads()).toBe(expectedUploads + 2)
  })

  it('draws nothing for an empty batch', () => {
    const { gl, renderer } = createRenderer()
    renderer.drawMultipleThickLines([], scale, view)
    expect(gl.drawElements).not.toHaveBeenCalled()
  })
})
