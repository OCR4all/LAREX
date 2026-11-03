// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { compileScript, parse } from 'vue/compiler-sfc'
import { ModuleKind, transpileModule } from 'typescript'
import * as Vue from 'vue'
import source from '../output-panel.vue?raw'

const descriptor = parse(source).descriptor
const compiled = transpileModule(compileScript(descriptor, { id: 'output-panel-time-test' }).content, {
  compilerOptions: { module: ModuleKind.CommonJS }
}).outputText
const exportsObject: { default?: { setup: (props: unknown, context: unknown) => { formatDate: (value: string) => string } } } = {}
new Function('require', 'exports', compiled)(() => Vue, exportsObject)

describe('output timestamps in the user timezone', () => {
  const originalTimezone = process.env.TZ
  afterEach(() => {
    if (originalTimezone === undefined) delete process.env.TZ
    else process.env.TZ = originalTimezone
    vi.unstubAllGlobals()
  })

  it.each(['2026-01-15T12:00:00', '2026-07-15T12:00:00'])('converts UTC server time %s to Berlin time', async (timestamp) => {
    process.env.TZ = 'Europe/Berlin'
    const host = document.createElement('div')
    const app = Vue.createApp({
      setup() {
        const setup = exportsObject.default!.setup({ outputs: [], pending: false, summary: '', canManage: false }, { expose: () => {}, emit: () => {} })
        return () => Vue.h('time', setup.formatDate(timestamp))
      }
    })
    app.mount(host)
    await Vue.nextTick()
    const expected = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(`${timestamp}Z`))
    try {
      expect(host.textContent).toBe(expected)
    } finally {
      app.unmount()
    }
  })
})
