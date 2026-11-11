import { readFileSync } from 'node:fs'
import { ModuleKind, transpileModule } from 'typescript'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as Vue from 'vue'
import { compileScript, parse } from 'vue/compiler-sfc'
import type { ActionDefinition } from '~/types/action'
import type { ActionWorkflowStep } from '~/types/action-workflow'

interface StepSettings {
  multipleImages: Vue.ComputedRef<boolean>
  selectedVariants: Vue.ComputedRef<string[]>
  variantItems: Vue.ComputedRef<Array<{ label: string, value: string }>>
  updateImages: (value: string | string[] | null | undefined) => void
  createVariant: (variant: string) => void
}

function setupStepSettings() {
  const file = './step-settings.vue'
  const descriptor = parse(readFileSync(new URL(file, import.meta.url), 'utf8')).descriptor
  const compiled = transpileModule(compileScript(descriptor, { id: file }).content, { compilerOptions: { module: ModuleKind.CommonJS } }).outputText
  const component: { default?: { setup: (props: unknown, context: unknown) => StepSettings } } = {}
  new Function('require', 'exports', compiled)((id: string) => (id === 'vue' ? Vue : {}), component)
  return component.default!.setup
}

const setup = setupStepSettings()
const processor = (multiple: boolean) => ({
  id: 'ocr',
  name: 'OCR',
  parameters: {},
  acceptsImages: true,
  inputs: { images: { level: 'REQUIRED', requiredForTargets: [], multiple }, xml: { level: 'NONE', requiredForTargets: [] } }
}) as unknown as ActionDefinition
const step = (variants?: string[]): ActionWorkflowStep => ({
  processorDefinitionId: 'ocr',
  parameters: {},
  imageVariantSelection: variants ? { mode: 'GLOBAL', variants, fallbackImage: false } : null
}) as ActionWorkflowStep

let scope: Vue.EffectScope
beforeEach(() => {
  for (const name of ['ref', 'computed', 'watch'] as const) vi.stubGlobal(name, Vue[name])
  vi.stubGlobal('useId', () => 'step')
  vi.stubGlobal('onBeforeUnmount', () => {})
  scope = Vue.effectScope()
})
afterEach(() => {
  scope.stop()
  vi.unstubAllGlobals()
})

function settings(multiple: boolean, current?: string[], earlierVariants?: string[], projectVariants?: Array<{ label: string, value: string }>) {
  const emit = vi.fn()
  const props = Vue.reactive({ processor: processor(multiple), step: step(current), earlierVariants, projectVariants })
  const state = scope.run(() => setup(props, { emit, expose: () => {} }))!
  return { state, emit }
}

describe('workflow step image variants', () => {
  it('offers project variants, earlier step outputs, and the current selection', () => {
    const { state } = settings(true, ['custom'], ['binarized', 'grayscale'], [{ label: 'binarized · binary', value: 'binarized' }, { label: 'original · color', value: 'original' }])
    expect(state.variantItems.value).toEqual([
      { label: 'binarized · binary', value: 'binarized' },
      { label: 'original · color', value: 'original' },
      { label: 'grayscale · from earlier step', value: 'grayscale' },
      { label: 'custom', value: 'custom' }
    ])
  })

  it('offers existing project variants for annotation-only workflows', () => {
    const { state } = settings(false, undefined, [], [{ label: 'binarized · binary', value: 'binarized' }])
    expect(state.variantItems.value.map(item => item.value)).toEqual(['binarized'])
  })

  it('stores several variants for multi-image processors', () => {
    const { state, emit } = settings(true, ['binarized'])
    state.updateImages(['binarized', ' grayscale ', 'binarized'])
    expect(emit).toHaveBeenLastCalledWith('update', expect.objectContaining({
      imageVariantSelection: { mode: 'GLOBAL', variants: ['binarized', 'grayscale'], fallbackImage: false }
    }))
    state.createVariant('deskewed')
    expect(emit).toHaveBeenLastCalledWith('update', expect.objectContaining({
      imageVariantSelection: { mode: 'GLOBAL', variants: ['binarized', 'deskewed'], fallbackImage: false }
    }))
  })

  it('keeps one variant for single-image processors and clears to automatic', () => {
    const { state, emit } = settings(false, ['binarized'])
    expect(state.multipleImages.value).toBe(false)
    state.createVariant('grayscale')
    expect(emit).toHaveBeenLastCalledWith('update', expect.objectContaining({
      imageVariantSelection: { mode: 'GLOBAL', variants: ['grayscale'], fallbackImage: false }
    }))
    state.updateImages(null)
    expect(emit).toHaveBeenLastCalledWith('update', expect.objectContaining({ imageVariantSelection: null }))
  })
})
