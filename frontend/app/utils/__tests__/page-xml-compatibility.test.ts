import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { ALL_CHART_REGION_SUBTYPES, ALL_GRAPHIC_REGION_SUBTYPES, ALL_REGION_KINDS } from '@/models/editor/region'
import {
  GRAPHIC_TYPE_SINCE,
  PAGE_XML_EXPORT_VERSIONS,
  REGION_TYPE_SINCE,
  TEXT_TYPE_SINCE,
  pageXmlCompatibility,
  regionTypeHasSubtype
} from '@/utils/page-xml-compatibility'

const SCHEMAS = fileURLToPath(new URL('../../../../backend/src/main/resources/schema/page', import.meta.url))
const schemas = Object.fromEntries(PAGE_XML_EXPORT_VERSIONS.map(version =>
  [version, readFileSync(`${SCHEMAS}/pagecontent-${version}.xsd`, 'utf8')]))
const latest = schemas[PAGE_XML_EXPORT_VERSIONS.at(-1)!]!

function regionElements(xsd: string): Set<string> {
  return new Set([...xsd.matchAll(/<element\s+name="(\w+Region)"/g)].map(match => match[1]!))
}

function enumeration(xsd: string, simpleType: string): string[] {
  const block = xsd.match(new RegExp(`<simpleType name="${simpleType}">([\\s\\S]*?)</simpleType>`))?.[1] ?? ''
  return [...block.matchAll(/<enumeration value="([^"]+)"/g)].map(match => match[1]!)
}

function firstVersion(defines: (xsd: string) => boolean): string | undefined {
  return PAGE_XML_EXPORT_VERSIONS.find(version => defines(schemas[version]!))
}

function since(table: Record<string, string>, value: string): string {
  return table[value] ?? PAGE_XML_EXPORT_VERSIONS[0]
}

describe('PAGE XML compatibility', () => {
  it('names the first PAGE version of every region type', () => {
    for (const kind of ALL_REGION_KINDS) {
      expect(firstVersion(xsd => regionElements(xsd).has(kind)), kind).toBe(since(REGION_TYPE_SINCE, kind))
    }
  })

  it('names the first PAGE version of every text and graphic type', () => {
    for (const textType of enumeration(latest, 'TextTypeSimpleType')) {
      expect(firstVersion(xsd => enumeration(xsd, 'TextTypeSimpleType').includes(textType)), textType).toBe(since(TEXT_TYPE_SINCE, textType))
    }
    for (const graphicType of enumeration(latest, 'GraphicsTypeSimpleType')) {
      expect(firstVersion(xsd => enumeration(xsd, 'GraphicsTypeSimpleType').includes(graphicType)), graphicType).toBe(since(GRAPHIC_TYPE_SINCE, graphicType))
    }
  })

  it('offers exactly the graphic and chart types of the newest schema', () => {
    expect([...ALL_GRAPHIC_REGION_SUBTYPES].sort()).toEqual(enumeration(latest, 'GraphicsTypeSimpleType').sort())
    expect([...ALL_CHART_REGION_SUBTYPES].sort()).toEqual(enumeration(latest, 'ChartTypeSimpleType').sort())
  })

  it('allows a subtype exactly on the region types with a type attribute', () => {
    for (const kind of ALL_REGION_KINDS) {
      const type = latest.match(new RegExp(`<complexType name="${kind}Type">([\\s\\S]*?)</complexType>`))?.[1] ?? ''
      expect(regionTypeHasSubtype(kind), kind).toBe(/<attribute name="type"/.test(type))
    }
  })

  it('describes every region type that older exports change', () => {
    for (const kind of ALL_REGION_KINDS) {
      const compatibility = pageXmlCompatibility({ regionType: kind })
      expect(compatibility?.since, kind).toBe(REGION_TYPE_SINCE[kind])
    }
  })

  it('names the export versions each note applies to', () => {
    expect(pageXmlCompatibility({ regionType: 'FormRegion' })?.olderExports).toEqual([
      'PAGE 2018 and 2019 exports turn it into a CustomRegion of type "form".',
      'PAGE 2010, 2013, 2016 and 2017 exports turn it into an UnknownRegion.'
    ])
    expect(pageXmlCompatibility({ regionType: 'TextRegion', textType: 'list-label' })?.olderExports).toEqual([
      'PAGE 2013 and 2016 exports write the subtype as "other".',
      'PAGE 2010 exports write the region without a subtype.'
    ])
    expect(pageXmlCompatibility({ regionType: 'GraphicRegion', customSubType: 'frame' })?.olderExports).toEqual([
      'PAGE 2010 exports turn it into a FrameRegion.'
    ])
    expect(pageXmlCompatibility({ regionType: 'TextRegion', textType: 'paragraph' })).toBeNull()
  })
})
