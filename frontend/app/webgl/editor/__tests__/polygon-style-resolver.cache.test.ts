import { describe, expect, it, vi } from 'vitest'
import { PolygonType } from '@/models/editor'
import type { RenderablePolygon } from '@/types/editor/rendering'
import type { RGBA } from '@/utils/editor/editor-constants'
import * as labelUtils from '@/utils/editor/label-utils'
import { resolvePolygonRenderStyle } from '../polygon-style-resolver'

const region = (id: string, regionSubtype?: string): RenderablePolygon => ({ id, type: PolygonType.REGION, regionKind: 'TextRegion', regionSubtype, points: [] })

describe('region color cache', () => {
  it('looks up each distinct region label once per cache', () => {
    const lookup = vi.spyOn(labelUtils, 'getStrokeColorForLabel')
    const regionColorCache = new Map<string, RGBA>()

    const first = resolvePolygonRenderStyle(region('a', 'paragraph'), { regionColorCache })
    const second = resolvePolygonRenderStyle(region('b', 'paragraph'), { regionColorCache })
    const heading = resolvePolygonRenderStyle(region('c', 'heading'), { regionColorCache })

    expect(lookup).toHaveBeenCalledTimes(2)
    expect(second.strokeColor).toEqual(first.strokeColor)
    expect(heading.strokeColor).toEqual(resolvePolygonRenderStyle(region('d', 'heading')).strokeColor)
    lookup.mockRestore()
  })
})
