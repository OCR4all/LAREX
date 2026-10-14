import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { compileScript, parse } from 'vue/compiler-sfc'
import { ModuleKind, transpileModule } from 'typescript'
import * as Vue from 'vue'
import * as bufferPolygon from '@/utils/editor/buffer-polygon'
import * as geometry from '@/models/editor/geometry'
import * as hitDetection from '@/utils/editor/hit-detection'

const descriptor = parse(readFileSync(new URL('../buffer-polygon.vue', import.meta.url), 'utf8')).descriptor
const compiled = transpileModule(compileScript(descriptor, { id: 'buffer-polygon-test' }).content, {
  compilerOptions: { module: ModuleKind.CommonJS }
}).outputText
interface Setup {
  distance: Vue.Ref<number>
  previewPoints: Vue.ComputedRef<{ x: number, y: number }[] | null>
  validationError: Vue.Ref<string | null>
  canSave: Vue.ComputedRef<boolean>
  distanceRange: Vue.ComputedRef<{ min: number, max: number }>
}
const exportsObject: { default?: { setup: (props: unknown, context: unknown) => Setup } } = {}
new Function('require', 'exports', 'useId', compiled)((id: string) => {
  if (id === '@/utils/editor/buffer-polygon') return bufferPolygon
  if (id === 'vue') return Vue
  if (id === '@/models/editor/geometry') return geometry
  if (id === '@/utils/editor/hit-detection') return hitDetection
  if (id === '@/composables/editor/use-canvas-interaction-blocker') return { useBlockEditorCanvasInteractions: () => {} }
  throw new Error(`Unexpected dependency ${id}`)
}, exportsObject, () => 'buffer-test')

describe('Expand / Shrink', () => {
  it.each([
    { distance: 1, constrainToImage: true, valid: true },
    { distance: -1, constrainToImage: true, valid: true },
    { distance: 50, constrainToImage: true, valid: true },
    { distance: -50, constrainToImage: true, valid: true },
    { distance: 400, constrainToImage: true, valid: false },
    { distance: 400, constrainToImage: false, valid: true }
  ])('validates a $distance-pixel adjustment (image constraint: $constrainToImage)', async ({ distance, constrainToImage, valid }) => {
    const scope = Vue.effectScope()
    try {
      const setup = scope.run(() => exportsObject.default!.setup({
        polygon: { id: 'region', type: 'region', points: [
          { x: -0.5, y: 0.5 }, { x: 0.5, y: 0.5 },
          { x: 0.5, y: -0.5 }, { x: -0.5, y: -0.5 }
        ] },
        polygons: [], imageSize: { width: 1000, height: 2000 },
        constrainToImage, constrainToParent: false
      }, { expose: () => {} }))!
      setup.distance.value = distance
      await Vue.nextTick()
      expect(setup.validationError.value).toBe(valid ? null : 'Shape exceeds image boundaries')
      expect(setup.canSave.value).toBe(valid)
    } finally {
      scope.stop()
    }
  })

  it.each([
    { constrainToParent: false, child: false, expectedMin: -353, expectedMax: 353 },
    { constrainToParent: true, child: false, expectedMin: -353, expectedMax: 70 },
    { constrainToParent: false, child: true, expectedMin: -70, expectedMax: 353 }
  ])('adapts limits to parent: $constrainToParent and children: $child', async ({ constrainToParent, child, expectedMin, expectedMax }) => {
    const square = (half: number) => [
      { x: -half, y: half }, { x: half, y: half },
      { x: half, y: -half }, { x: -half, y: -half }
    ]
    const scope = Vue.effectScope()
    try {
      const setup = scope.run(() => exportsObject.default!.setup({
        polygon: { id: 'region', parentId: 'parent', type: 'region', points: square(0.5) },
        polygons: [
          { id: 'parent', type: 'region', points: square(0.6) },
          ...(child ? [{ id: 'child', parentId: 'region', label: 'child', points: square(0.4) }] : [])
        ],
        imageSize: { width: 1000, height: 1000 },
        constrainToImage: true, constrainToParent
      }, { expose: () => {} }))!
      expect(setup.distanceRange.value).toEqual({ min: expectedMin, max: expectedMax })
      for (const limit of [expectedMin, expectedMax]) {
        setup.distance.value = limit
        await Vue.nextTick()
        expect(setup.validationError.value).toBeNull()
        expect(setup.canSave.value).toBe(true)
      }
      for (const beyond of [expectedMin - 1, expectedMax + 1]) {
        setup.distance.value = beyond
        await Vue.nextTick()
        expect(setup.validationError.value).not.toBeNull()
        expect(setup.canSave.value).toBe(false)
      }
    } finally {
      scope.stop()
    }
  })
})
