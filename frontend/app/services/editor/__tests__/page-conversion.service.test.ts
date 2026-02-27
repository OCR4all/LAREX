import { describe, expect, it } from 'vitest'
import {
  convertPageDtoToPcGts,
  convertPcGtsToPageDto,
  type PageDto
} from '../page-conversion.service'

function createBaseDto(): PageDto {
  return {
    imageFilename: 'image.png',
    imageWidth: 1000,
    imageHeight: 1500,
    regions: [
      {
        id: 'r1',
        kind: 'TextRegion',
        coords: {
          points: [[0, 0], [1, 0], [1, 1], [0, 1]]
        },
        continuation: false,
        confidence: 0.73,
        type: 'heading',
        textContentVariants: [
          { unicode: 'Region text', index: undefined },
          { unicode: 'GT text', index: 0 }
        ],
        textLines: [
          {
            id: 'tl1',
            coords: { points: [[0, 0], [1, 0], [1, 0.2], [0, 0.2]] },
            baseline: {
              points: [[0, 0.1], [1, 0.1]],
              confidence: 0.61
            },
            textContentVariants: [
              { unicode: 'Line', confidence: 0.91 }
            ],
            words: [
              {
                id: 'w1',
                coords: { points: [[0, 0], [0.5, 0], [0.5, 0.2], [0, 0.2]] },
                textContentVariants: [{ unicode: 'Line', confidence: 0.9 }],
                glyphs: [
                  {
                    id: 'g1',
                    coords: { points: [[0, 0], [0.1, 0], [0.1, 0.2], [0, 0.2]] },
                    textContentVariants: [
                      { unicode: 'L', confidence: 0.95, index: 0 },
                      { unicode: 'ℒ', confidence: 0.5, index: 1 }
                    ]
                  }
                ]
              }
            ]
          }
        ]
      }
    ]
  }
}

describe('page-conversion.service sparse mapping', () => {
  it('does not inject default metadata values when DTO metadata is missing', () => {
    const dto = createBaseDto()
    dto.metadata = undefined

    const pcGts = convertPageDtoToPcGts(dto)

    expect(pcGts.metadata.creator).toBeUndefined()
    expect(pcGts.metadata.created).toBeUndefined()
    expect(pcGts.metadata.lastChange).toBeUndefined()
  })

  it('preserves text region type and text/baseline confidence on round-trip', () => {
    const dto = createBaseDto()
    const pcGts = convertPageDtoToPcGts(dto)
    const roundTrip = convertPcGtsToPageDto(pcGts)

    expect(roundTrip.regions?.[0]?.type).toBe('heading')
    expect(roundTrip.regions?.[0]?.continuation).toBe(false)
    expect(roundTrip.regions?.[0]?.confidence).toBe(0.73)
    expect(roundTrip.regions?.[0]?.textLines?.[0]?.textContentVariants?.[0]?.confidence).toBe(0.91)
    expect(roundTrip.regions?.[0]?.textLines?.[0]?.baseline?.confidence).toBe(0.61)
    expect(roundTrip.regions?.[0]?.textContentVariants?.[0]?.index).toBeUndefined()
    expect(roundTrip.regions?.[0]?.textContentVariants?.[1]?.index).toBe(0)
  })

  it('keeps glyph text variants instead of collapsing to one value', () => {
    const dto = createBaseDto()
    const pcGts = convertPageDtoToPcGts(dto)
    const roundTrip = convertPcGtsToPageDto(pcGts)

    const glyphVariants = roundTrip
      .regions?.[0]?.textLines?.[0]?.words?.[0]?.glyphs?.[0]?.textContentVariants

    expect(glyphVariants?.length).toBe(2)
    expect(glyphVariants?.[0]?.unicode).toBe('L')
    expect(glyphVariants?.[1]?.unicode).toBe('ℒ')
  })
})
