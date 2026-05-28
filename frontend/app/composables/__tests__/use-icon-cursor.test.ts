import { beforeAll, describe, expect, it, vi } from 'vitest'

vi.mock('nuxt/app', () => ({
  useAppConfig: () => ({})
}))

let buildIconCursorSvg: typeof import('../use-icon-cursor').buildIconCursorSvg
let buildIconCursorValue: typeof import('../use-icon-cursor').buildIconCursorValue
let getIconCursorPreset: typeof import('../use-icon-cursor').getIconCursorPreset
let parseIconifyIconName: typeof import('../use-icon-cursor').parseIconifyIconName

beforeAll(async () => {
  const module = await import('../use-icon-cursor')
  buildIconCursorSvg = module.buildIconCursorSvg
  buildIconCursorValue = module.buildIconCursorValue
  getIconCursorPreset = module.getIconCursorPreset
  parseIconifyIconName = module.parseIconifyIconName
})

describe('use-icon-cursor helpers', () => {
  it('parses supported Iconify icon name formats', () => {
    expect(parseIconifyIconName('i-lucide-wand-sparkles')).toEqual({
      collection: 'lucide',
      icon: 'wand-sparkles'
    })

    expect(parseIconifyIconName('lucide:wand-sparkles')).toEqual({
      collection: 'lucide',
      icon: 'wand-sparkles'
    })

    expect(parseIconifyIconName('wand-sparkles')).toBeNull()
  })

  it('builds a cursor CSS value from an icon svg', () => {
    const svg = buildIconCursorSvg({
      body: '<path stroke="currentColor" d="M0 0h24v24" />',
      width: 24,
      height: 24
    }, '#ffffff', 24)

    const cursorValue = buildIconCursorValue(svg, {
      fallback: 'crosshair',
      hotspotX: 4,
      hotspotY: 4
    })

    expect(svg).toContain('viewBox="0 0 24 24"')
    expect(svg).toContain('color:#ffffff')
    expect(svg).toContain('stroke="#ffffff"')
    expect(svg).toContain('filter id="cursor-outline"')
    expect(svg).toContain('flood-color="#000000"')
    expect(cursorValue).toContain('data:image/svg+xml,')
    expect(cursorValue).toContain('4 4, crosshair')
  })

  it('exposes named cursor presets for editor tools', () => {
    expect(getIconCursorPreset('actionWand')).toEqual({
      iconName: 'i-lucide-wand-sparkles',
      fallback: 'crosshair',
      color: '#ffffff',
      hotspotX: 4,
      hotspotY: 4,
      size: 24
    })
  })
})
