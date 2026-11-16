import type { PageRegionType } from '@/types/label-set'
import { ALL_CHART_REGION_SUBTYPES, ALL_GRAPHIC_REGION_SUBTYPES } from '@/models/editor/region'

export const PAGE_XML_EXPORT_VERSIONS = ['2010-03-19', '2013-07-15', '2016-07-15', '2017-07-15', '2018-07-15', '2019-07-15', '2024-07-15'] as const

export const REGION_TYPE_SINCE: Partial<Record<PageRegionType, string>> = {
  ChemRegion: '2013-07-15',
  MusicRegion: '2013-07-15',
  AdvertRegion: '2013-07-15',
  MapRegion: '2018-07-15',
  CustomRegion: '2018-07-15',
  FormRegion: '2024-07-15'
}

export const TEXT_TYPE_SINCE: Record<string, string> = {
  'endnote': '2013-07-15',
  'other': '2013-07-15',
  'list-label': '2017-07-15'
}

export const GRAPHIC_TYPE_SINCE: Record<string, string> = {
  decoration: '2013-07-15',
  frame: '2013-07-15',
  barcode: '2013-07-15'
}

const REGION_KINDS_WITH_TYPE = new Set<PageRegionType>(['TextRegion', 'GraphicRegion', 'ChartRegion', 'CustomRegion'])

export function regionTypeHasSubtype(regionType: PageRegionType | null | undefined): boolean {
  return Boolean(regionType && REGION_KINDS_WITH_TYPE.has(regionType))
}

export function allowedRegionSubtypes(regionType: PageRegionType | null | undefined): readonly string[] | null {
  if (regionType === 'GraphicRegion') return ALL_GRAPHIC_REGION_SUBTYPES
  if (regionType === 'ChartRegion') return ALL_CHART_REGION_SUBTYPES
  return null
}

export interface PageXmlCompatibility {
  since: string
  olderExports: string[]
}

interface MappingLike {
  regionType?: PageRegionType | null
  textType?: string | null
  customSubType?: string | null
}

function exportsBetween(from: string, until: string): string {
  const years = PAGE_XML_EXPORT_VERSIONS
    .filter(version => version >= from && version < until)
    .map(version => version.slice(0, 4))
  const listed = years.length > 1 ? `${years.slice(0, -1).join(', ')} and ${years.at(-1)}` : years[0]
  return `PAGE ${listed} exports`
}

function untilBefore(version: string): string {
  return exportsBetween(PAGE_XML_EXPORT_VERSIONS[0], version)
}

export function pageXmlCompatibility(mapping: MappingLike | null | undefined): PageXmlCompatibility | null {
  const regionType = mapping?.regionType
  if (!regionType) return null

  switch (regionType) {
    case 'FormRegion':
      return {
        since: '2024-07-15',
        olderExports: [
          `${exportsBetween('2018-07-15', '2024-07-15')} turn it into a CustomRegion of type "form".`,
          `${untilBefore('2018-07-15')} turn it into an UnknownRegion.`
        ]
      }
    case 'MapRegion':
      return {
        since: '2018-07-15',
        olderExports: [
          `${untilBefore('2018-07-15')} turn it into an UnknownRegion.`,
          `Inside another region it needs PAGE 2024: ${exportsBetween('2018-07-15', '2024-07-15')} turn a nested map region into a CustomRegion of type "map".`
        ]
      }
    case 'CustomRegion':
      return {
        since: '2018-07-15',
        olderExports: [`${untilBefore('2018-07-15')} turn it into an UnknownRegion and drop its type.`]
      }
    case 'ChemRegion':
    case 'MusicRegion':
    case 'AdvertRegion':
      return {
        since: '2013-07-15',
        olderExports: [`${untilBefore('2013-07-15')} turn it into an UnknownRegion.`]
      }
    case 'TextRegion': {
      const textType = mapping.textType === 'custom' ? 'other' : mapping.textType
      if (textType === 'list-label') {
        return {
          since: '2017-07-15',
          olderExports: [
            `${exportsBetween('2013-07-15', '2017-07-15')} write the subtype as "other".`,
            `${untilBefore('2013-07-15')} write the region without a subtype.`
          ]
        }
      }
      if (textType && TEXT_TYPE_SINCE[textType]) {
        return {
          since: TEXT_TYPE_SINCE[textType],
          olderExports: [`${untilBefore(TEXT_TYPE_SINCE[textType])} write the region without a subtype.`]
        }
      }
      return null
    }
    case 'GraphicRegion': {
      const subtype = mapping.customSubType?.trim()
      if (!subtype || !GRAPHIC_TYPE_SINCE[subtype]) return null
      return {
        since: GRAPHIC_TYPE_SINCE[subtype],
        olderExports: [subtype === 'frame'
          ? `${untilBefore(GRAPHIC_TYPE_SINCE[subtype])} turn it into a FrameRegion.`
          : `${untilBefore(GRAPHIC_TYPE_SINCE[subtype])} write the region without a subtype.`]
      }
    }
    default:
      return null
  }
}
