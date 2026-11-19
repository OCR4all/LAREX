import type { ComputedRef } from 'vue'
import { baselineIdForTextLineId, findGlyphRecursive, findWordRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { getEditorSession } from '@/session/editor/editor-session'
import { Polygon } from '@/models/editor'
import type { AlternativeImage, Labels, MetadataItem, TextStyleAttributes, UserDefined } from '@/models/editor'
import type { PcGts } from '@/models/editor/document'
import type { ReadingOrder } from '@/models/editor/reading-order'
import type { Page } from '@/models/editor/page'
import type { Region, TextRegion } from '@/models/editor/region'
import type { TextLine, Baseline } from '@/models/editor/text'
import type { MetadataApplyPayload } from '@/types/editor/metadata'
import { cloneReadingOrder } from '@/commands/editor/reading-order-utils'
import { findParentTable, syncTableDimensions } from '@/utils/editor/table/table-model'

type EditorMetadataApplyOptions = {
  activeCanvasId: ComputedRef<string | null>
  activeDocument: ComputedRef<PcGts | null>
  activePage: ComputedRef<Page | null>
  onReadingOrderUpdated: () => void
}

export function useEditorMetadataApply(options: EditorMetadataApplyOptions) {
  function findRegionById(regions: Region[] | undefined, id: string): Region | null {
    if (!regions) return null
    for (const region of regions) {
      if (region.id === id) return region
      const nested = findRegionById(region.regions, id)
      if (nested) return nested
    }
    return null
  }

  function findTextLineById(regions: Region[] | undefined, id: string): TextLine | null {
    if (!regions) return null
    for (const region of regions) {
      if (region.kind === 'TextRegion' && (region as TextRegion).textLines) {
        const textLine = (region as TextRegion).textLines?.find(item => item.id === id)
        if (textLine) return textLine
      }
      const nested = findTextLineById(region.regions, id)
      if (nested) return nested
    }
    return null
  }

  function findBaselineById(regions: Region[] | undefined, id: string): Baseline | null {
    if (!regions) return null
    for (const region of regions) {
      if (region.kind === 'TextRegion' && (region as TextRegion).textLines) {
        for (const textLine of (region as TextRegion).textLines ?? []) {
          if (!textLine.baseline) continue
          if (baselineIdForTextLineId(textLine.id) === id || `${textLine.id}_baseline` === id) {
            return textLine.baseline
          }
        }
      }
      const nested = findBaselineById(region.regions, id)
      if (nested) return nested
    }
    return null
  }

  function touchActiveDocumentMetadata() {
    options.activeDocument.value?.metadata?.touch?.()
  }

  function triggerActiveDocumentRef() {
    const canvasId = options.activeCanvasId.value
    if (!canvasId) return
    const session = getEditorSession(canvasId)
    if (!session?.document.value) return
    triggerRef(session.document)
  }

  function normalizeOptionalMetadataString(value: unknown): string | undefined {
    if (typeof value === 'string') {
      const trimmed = value.trim()
      return trimmed.length > 0 ? trimmed : undefined
    }
    if (value && typeof value === 'object' && 'value' in value) {
      const nested = (value as { value?: unknown }).value
      if (typeof nested === 'string') {
        const trimmed = nested.trim()
        return trimmed.length > 0 ? trimmed : undefined
      }
    }
    return undefined
  }

  function rawString(value: unknown): string | undefined {
    if (typeof value === 'string') return value
    if (value && typeof value === 'object' && 'value' in value) {
      const nested = (value as { value?: unknown }).value
      if (typeof nested === 'string') return nested
    }
    return undefined
  }

  function normalizeAlternativeImagesFromForm(images?: Array<{ filename?: string, comments?: string, confidence?: number }>): AlternativeImage[] | undefined {
    if (!images?.length) return undefined
    const normalized = images.reduce<AlternativeImage[]>((acc, image) => {
      const filename = normalizeOptionalMetadataString(image.filename)
      if (!filename) return acc

      acc.push({
        filename,
        comments: rawString(image.comments),
        confidence: image.confidence
      })
      return acc
    }, [])
    return normalized.length > 0 ? normalized : undefined
  }

  function normalizeLabelsFromForm(groups?: Array<{
    externalModel?: string
    externalId?: string
    prefix?: string
    comments?: string
    labels?: Array<{ value?: string, type?: string, comments?: string }>
  }>): Labels[] | undefined {
    if (!groups?.length) return undefined
    const normalized = groups
      .map((group) => {
        const labels = (group.labels ?? [])
          .map(label => ({
            value: rawString(label.value),
            type: rawString(label.type),
            comments: rawString(label.comments)
          }))
          .filter(label => label.value !== undefined) as Array<{ value: string, type?: string, comments?: string }>
        const externalModel = rawString(group.externalModel)
        const externalId = rawString(group.externalId)
        const prefix = rawString(group.prefix)
        const comments = rawString(group.comments)
        if ([externalModel, externalId, prefix, comments].every(value => value === undefined) && labels.length === 0) return undefined
        return {
          externalModel,
          externalId,
          prefix,
          comments,
          labels: labels.length > 0 ? labels : undefined
        } as Labels
      })
      .filter((group): group is Labels => Boolean(group))
    return normalized.length > 0 ? normalized : undefined
  }

  function normalizeUserDefinedFromForm(attributes?: Array<{
    name?: string
    description?: string
    type?: 'xsd:string' | 'xsd:integer' | 'xsd:boolean' | 'xsd:float'
    value?: string
  }>): UserDefined | undefined {
    if (!attributes?.length) return undefined
    const normalized = attributes
      .map(attribute => ({
        name: rawString(attribute.name),
        description: rawString(attribute.description),
        type: attribute.type,
        value: rawString(attribute.value)
      }))
      .filter(attribute => typeof attribute.name === 'string' && attribute.name.trim().length > 0)
    return normalized.length > 0 ? { attributes: normalized } : undefined
  }

  function normalizeMetadataItemsFromForm(items?: Array<{
    type?: 'author' | 'imageProperties' | 'processingStep' | 'other'
    name?: string
    value?: string
    date?: string
    labels?: Array<{
      externalModel?: string
      externalId?: string
      prefix?: string
      comments?: string
      labels?: Array<{ value?: string, type?: string, comments?: string }>
    }>
  }>): MetadataItem[] | undefined {
    if (!items?.length) return undefined
    const normalized = items
      .map((item) => {
        const type = item.type
        const name = rawString(item.name)
        const value = rawString(item.value)
        const date = normalizeOptionalMetadataString(item.date)
        const labels = normalizeLabelsFromForm(item.labels)
        if (!type && name === undefined && value === undefined && !date && !labels) return undefined
        return {
          type,
          name,
          value: value ?? '',
          date,
          labels
        } as MetadataItem
      })
      .filter((item): item is MetadataItem => Boolean(item))
    return normalized.length > 0 ? normalized : undefined
  }

  function normalizeTextStyleFromForm(style?: {
    fontFamily?: string
    serif?: boolean
    monospace?: boolean
    fontSize?: number
    xHeight?: number
    kerning?: number
    textColour?: string
    textColourRgb?: number
    bgColour?: string
    bgColourRgb?: number
    reverseVideo?: boolean
    bold?: boolean
    italic?: boolean
    underlined?: boolean
    underlineStyle?: string
    subscript?: boolean
    superscript?: boolean
    strikethrough?: boolean
    smallCaps?: boolean
    letterSpaced?: boolean
  }): TextStyleAttributes | undefined {
    if (!style) return undefined
    const normalized: TextStyleAttributes = {
      fontFamily: rawString(style.fontFamily),
      serif: style.serif,
      monospace: style.monospace,
      fontSize: style.fontSize,
      xHeight: style.xHeight,
      kerning: style.kerning,
      textColour: normalizeOptionalMetadataString(style.textColour),
      textColourRgb: style.textColourRgb,
      bgColour: normalizeOptionalMetadataString(style.bgColour),
      bgColourRgb: style.bgColourRgb,
      reverseVideo: style.reverseVideo,
      bold: style.bold,
      italic: style.italic,
      underlined: style.underlined,
      underlineStyle: normalizeOptionalMetadataString(style.underlineStyle),
      subscript: style.subscript,
      superscript: style.superscript,
      strikethrough: style.strikethrough,
      smallCaps: style.smallCaps,
      letterSpaced: style.letterSpaced
    }
    return Object.values(normalized).some(value => value !== undefined) ? normalized : undefined
  }

  function parseGridPoints(raw: string | undefined): Polygon | undefined {
    const value = normalizeOptionalMetadataString(raw)
    if (!value) return undefined
    const points: Array<[number, number]> = []
    for (const token of value.split(/[\s;]+/)) {
      if (!token) continue
      const [xRaw, yRaw] = token.split(',')
      const x = Number(xRaw)
      const y = Number(yRaw)
      if (!Number.isFinite(x) || !Number.isFinite(y)) continue
      points.push([x, y])
    }
    return points.length > 0 ? new Polygon(points) : undefined
  }

  function normalizeGridRowsFromForm(rows?: Array<{ index?: number, points?: string }>): Array<{ index?: number, points: Polygon }> | undefined {
    if (!rows?.length) return undefined
    const normalized = rows
      .map((row) => {
        const points = parseGridPoints(row.points)
        if (!points) return undefined
        return {
          index: row.index,
          points
        }
      })
      .filter(row => Boolean(row)) as Array<{ index?: number, points: Polygon }>
    return normalized.length > 0 ? normalized : undefined
  }

  function normalizeTableCellRoleFromForm(role?: {
    rowIndex?: number
    columnIndex?: number
    rowSpan?: number
    colSpan?: number
    header?: boolean
  }) {
    if (!role) return undefined
    const normalized = {
      rowIndex: role.rowIndex,
      columnIndex: role.columnIndex,
      rowSpan: role.rowSpan,
      colSpan: role.colSpan,
      header: role.header
    }
    return Object.values(normalized).some(value => value !== undefined) ? normalized : undefined
  }

  function handleApplyReadingOrder(readingOrder: ReadingOrder) {
    const page = options.activePage.value
    if (!page) return
    page.readingOrder = cloneReadingOrder(readingOrder)
    touchActiveDocumentMetadata()
    triggerActiveDocumentRef()
    options.onReadingOrderUpdated()
  }

  function handleApplyMetadata(payload: MetadataApplyPayload) {
    const page = options.activePage.value
    const regions = page?.regions
    let updated = false

    switch (payload.target) {
      case 'document': {
        const metadata = options.activeDocument.value?.metadata
        if (!metadata) break
        metadata.creator = payload.data.creator
        metadata.comments = payload.data.comments
        metadata.externalRef = payload.data.externalRef
        metadata.userDefined = normalizeUserDefinedFromForm(payload.data.userDefinedAttributes)
        metadata.items = normalizeMetadataItemsFromForm(payload.data.items)
        updated = true
        break
      }
      case 'page': {
        if (!page) break
        page.imageXResolution = payload.data.imageXResolution
        page.imageYResolution = payload.data.imageYResolution
        page.imageResolutionUnit = payload.data.imageResolutionUnit
        page.custom = payload.data.custom
        page.orientation = payload.data.orientation
        page.type = payload.data.type
        page.primaryLanguage = normalizeOptionalMetadataString(payload.data.primaryLanguage)
        page.secondaryLanguage = normalizeOptionalMetadataString(payload.data.secondaryLanguage)
        page.primaryScript = normalizeOptionalMetadataString(payload.data.primaryScript)
        page.secondaryScript = normalizeOptionalMetadataString(payload.data.secondaryScript)
        page.readingDirection = payload.data.readingDirection
        page.textLineOrder = payload.data.textLineOrder
        page.conf = payload.data.conf
        page.alternativeImages = normalizeAlternativeImagesFromForm(payload.data.alternativeImages)
        page.labels = normalizeLabelsFromForm(payload.data.labels)
        page.userDefined = normalizeUserDefinedFromForm(payload.data.userDefinedAttributes)
        page.textStyle = normalizeTextStyleFromForm(payload.data.textStyle)
        updated = true
        break
      }
      case 'textRegion': {
        const region = findRegionById(regions, payload.elementId)
        if (!region || region.kind !== 'TextRegion') break
        const textRegion = region as TextRegion
        textRegion.custom = payload.data.custom
        textRegion.comments = payload.data.comments
        textRegion.continuation = payload.data.continuation
        textRegion.orientation = payload.data.orientation
        textRegion.type = payload.data.type || undefined
        textRegion.leading = payload.data.leading
        textRegion.readingDirection = payload.data.readingDirection
        textRegion.textLineOrder = payload.data.textLineOrder
        textRegion.readingOrientation = payload.data.readingOrientation
        textRegion.indented = payload.data.indented
        textRegion.align = payload.data.align
        textRegion.primaryLanguage = normalizeOptionalMetadataString(payload.data.primaryLanguage)
        textRegion.secondaryLanguage = normalizeOptionalMetadataString(payload.data.secondaryLanguage)
        textRegion.primaryScript = normalizeOptionalMetadataString(payload.data.primaryScript)
        textRegion.secondaryScript = normalizeOptionalMetadataString(payload.data.secondaryScript)
        textRegion.production = payload.data.production
        textRegion.alternativeImages = normalizeAlternativeImagesFromForm(payload.data.alternativeImages)
        textRegion.labels = normalizeLabelsFromForm(payload.data.labels)
        textRegion.userDefined = normalizeUserDefinedFromForm(payload.data.userDefinedAttributes)
        textRegion.textStyle = normalizeTextStyleFromForm(payload.data.textStyle)
        updated = true
        break
      }
      case 'genericRegion': {
        const region = findRegionById(regions, payload.elementId)
        if (!region) break
        region.custom = payload.data.custom
        region.comments = payload.data.comments
        region.continuation = payload.data.continuation
        region.orientation = payload.data.orientation
        region.numColours = payload.data.numColours
        region.embText = payload.data.embText
        region.colourDepth = payload.data.colourDepth || undefined
        region.lineColour = payload.data.lineColour || undefined
        region.lineSeparators = payload.data.lineSeparators
        region.rows = payload.data.rows
        region.columns = payload.data.columns
        region.colour = payload.data.colour || undefined
        region.penColour = payload.data.penColour || undefined
        region.borderPresent = payload.data.borderPresent
        region.textColourRgb = payload.data.textColourRgb
        region.bgColourRgb = payload.data.bgColourRgb
        region.alternativeImages = normalizeAlternativeImagesFromForm(payload.data.alternativeImages)
        region.labels = normalizeLabelsFromForm(payload.data.labels)
        region.userDefined = normalizeUserDefinedFromForm(payload.data.userDefinedAttributes)
        region.textStyle = normalizeTextStyleFromForm(payload.data.textStyle)
        const tableCellRole = normalizeTableCellRoleFromForm(payload.data.tableCellRole)
        const gridRows = normalizeGridRowsFromForm(payload.data.gridRows)
        region.roles = tableCellRole ? { tableCellRole } : undefined
        region.grid = gridRows ? { rows: gridRows, addPoints: region.grid?.addPoints } : undefined
        if ('type' in region) {
          ;(region as { type?: string }).type = payload.data.type || undefined
        }
        syncTableDimensions(region)
        const parentTable = findParentTable(regions, region.id)
        if (parentTable) syncTableDimensions(parentTable)
        updated = true
        break
      }
      case 'textLine': {
        const textLine = findTextLineById(regions, payload.elementId)
        if (!textLine) break
        textLine.primaryLanguage = normalizeOptionalMetadataString(payload.data.primaryLanguage)
        textLine.primaryScript = normalizeOptionalMetadataString(payload.data.primaryScript)
        textLine.secondaryScript = normalizeOptionalMetadataString(payload.data.secondaryScript)
        textLine.readingDirection = payload.data.readingDirection
        textLine.production = payload.data.production
        textLine.custom = payload.data.custom
        textLine.comments = payload.data.comments
        textLine.index = payload.data.index
        textLine.alternativeImages = normalizeAlternativeImagesFromForm(payload.data.alternativeImages)
        textLine.labels = normalizeLabelsFromForm(payload.data.labels)
        textLine.userDefined = normalizeUserDefinedFromForm(payload.data.userDefinedAttributes)
        textLine.textStyle = normalizeTextStyleFromForm(payload.data.textStyle)
        updated = true
        break
      }
      case 'word': {
        const word = regions ? findWordRecursive(regions, payload.elementId)?.word : undefined
        if (!word) break
        word.language = normalizeOptionalMetadataString(payload.data.language)
        word.primaryScript = normalizeOptionalMetadataString(payload.data.primaryScript)
        word.secondaryScript = normalizeOptionalMetadataString(payload.data.secondaryScript)
        word.readingDirection = payload.data.readingDirection
        word.production = payload.data.production
        word.custom = payload.data.custom
        word.comments = payload.data.comments
        word.alternativeImages = normalizeAlternativeImagesFromForm(payload.data.alternativeImages)
        word.labels = normalizeLabelsFromForm(payload.data.labels)
        word.userDefined = normalizeUserDefinedFromForm(payload.data.userDefinedAttributes)
        word.textStyle = normalizeTextStyleFromForm(payload.data.textStyle)
        updated = true
        break
      }
      case 'glyph': {
        const glyph = regions ? findGlyphRecursive(regions, payload.elementId)?.glyph : undefined
        if (!glyph) break
        glyph.script = normalizeOptionalMetadataString(payload.data.script)
        glyph.production = payload.data.production
        glyph.ligature = payload.data.ligature
        glyph.symbol = payload.data.symbol
        glyph.custom = payload.data.custom
        glyph.comments = payload.data.comments
        glyph.alternativeImages = normalizeAlternativeImagesFromForm(payload.data.alternativeImages)
        glyph.labels = normalizeLabelsFromForm(payload.data.labels)
        glyph.userDefined = normalizeUserDefinedFromForm(payload.data.userDefinedAttributes)
        glyph.textStyle = normalizeTextStyleFromForm(payload.data.textStyle)
        updated = true
        break
      }
      case 'baseline': {
        const baseline = findBaselineById(regions, payload.elementId)
        if (!baseline) break
        baseline.conf = payload.data.conf
        updated = true
        break
      }
    }

    if (updated) {
      touchActiveDocumentMetadata()
      triggerActiveDocumentRef()
    }
  }

  return {
    cloneReadingOrder,
    findRegionById,
    findTextLineById,
    findBaselineById,
    handleApplyReadingOrder,
    handleApplyMetadata
  }
}
