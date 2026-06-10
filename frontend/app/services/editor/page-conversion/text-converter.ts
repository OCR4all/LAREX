import { Glyph, Polyline, TextContentVariant, TextLine, Word, type GraphemeElement } from '@/models/editor'
import type {
  GlyphDto,
  GraphemeElementDto,
  TextContentVariantDto,
  TextLineDto,
  WordDto
} from '@/types/page-dto'
import {
  convertAlternativeImagesFromDto,
  convertAlternativeImagesToDto,
  convertLabelsFromDto,
  convertLabelsToDto,
  convertPolygonFromDto,
  convertPolygonToDto,
  convertTextStyleFromDto,
  convertTextStyleToDto,
  convertUserDefinedFromDto,
  convertUserDefinedToDto,
  undefinedIfBlank
} from './shared'

export function convertTextContentVariantFromDto(dto: TextContentVariantDto): TextContentVariant {
  return new TextContentVariant(
    dto.unicode ?? '',
    dto.plainText,
    dto.confidence,
    dto.index,
    dto.dataType,
    dto.dataTypeDetails,
    dto.comments
  )
}

export function convertTextContentVariantToDto(te: TextContentVariant): TextContentVariantDto {
  return {
    unicode: te.unicode,
    plainText: te.plainText,
    index: te.index,
    confidence: te.confidence,
    comments: te.comments,
    dataType: te.dataType,
    dataTypeDetails: te.dataTypeDetails
  }
}

function convertGraphemeElementsFromDto(elements?: GraphemeElementDto[]): GraphemeElement[] | undefined {
  if (!elements?.length) return undefined
  return elements.map(element => ({
    kind: element.kind ?? 'grapheme',
    id: undefinedIfBlank(element.id),
    index: element.index,
    charType: undefinedIfBlank(element.charType),
    ligature: element.ligature,
    custom: undefinedIfBlank(element.custom),
    comments: undefinedIfBlank(element.comments),
    coords: convertPolygonFromDto(element.coords),
    textContentVariants: element.textContentVariants?.map(convertTextContentVariantFromDto),
    labels: convertLabelsFromDto(element.labels),
    members: convertGraphemeElementsFromDto(element.members)
  })) as GraphemeElement[]
}

function convertGraphemeElementsToDto(elements?: GraphemeElement[]): GraphemeElementDto[] | undefined {
  if (!elements?.length) return undefined
  return elements.map(element => ({
    kind: element.kind,
    id: undefinedIfBlank(element.id),
    index: element.index,
    charType: undefinedIfBlank(element.charType),
    ligature: element.ligature,
    custom: undefinedIfBlank(element.custom),
    comments: undefinedIfBlank(element.comments),
    coords: convertPolygonToDto(element.coords),
    textContentVariants: element.textContentVariants?.map(convertTextContentVariantToDto),
    labels: convertLabelsToDto(element.labels),
    members: convertGraphemeElementsToDto(element.members)
  }))
}

function convertGlyphFromDto(dto: GlyphDto): Glyph {
  const textContentVariants = dto.textContentVariants?.map(convertTextContentVariantFromDto)
  return new Glyph(
    dto.id,
    convertPolygonFromDto(dto.coords)!,
    textContentVariants?.[0]?.unicode,
    textContentVariants,
    dto.ligature,
    dto.symbol,
    undefinedIfBlank(dto.script),
    undefinedIfBlank(dto.production) as Glyph['production'],
    dto.confidence,
    undefined,
    undefinedIfBlank(dto.custom),
    undefinedIfBlank(dto.comments),
    convertAlternativeImagesFromDto(dto.alternativeImages),
    convertLabelsFromDto(dto.labels),
    convertUserDefinedFromDto(dto.userDefined),
    convertTextStyleFromDto(dto.textStyle),
    dto.graphemes ? { elements: convertGraphemeElementsFromDto(dto.graphemes.elements) } : undefined
  )
}

function convertGlyphToDto(glyph: Glyph): GlyphDto {
  const glyphVariants = glyph.textContentVariants?.map(convertTextContentVariantToDto)
    ?? (glyph.unicode ? [{ unicode: glyph.unicode, confidence: glyph.confidence }] : undefined)
  return {
    id: glyph.id,
    coords: convertPolygonToDto(glyph.coords),
    textContentVariants: glyphVariants,
    alternativeImages: convertAlternativeImagesToDto(glyph.alternativeImages),
    labels: convertLabelsToDto(glyph.labels),
    userDefined: convertUserDefinedToDto(glyph.userDefined),
    textStyle: convertTextStyleToDto(glyph.textStyle),
    graphemes: glyph.graphemes ? { elements: convertGraphemeElementsToDto(glyph.graphemes.elements) } : undefined,
    ligature: glyph.ligature,
    symbol: glyph.symbol,
    script: undefinedIfBlank(glyph.script),
    production: undefinedIfBlank(glyph.production),
    confidence: glyph.confidence,
    custom: undefinedIfBlank(glyph.custom),
    comments: undefinedIfBlank(glyph.comments)
  }
}

function convertWordFromDto(dto: WordDto): Word {
  return new Word(
    dto.id,
    convertPolygonFromDto(dto.coords)!,
    dto.textContentVariants?.map(convertTextContentVariantFromDto),
    dto.glyphs?.map(convertGlyphFromDto),
    undefined,
    undefined,
    dto.confidence,
    undefinedIfBlank(dto.language),
    undefinedIfBlank(dto.primaryScript) ?? undefinedIfBlank(dto.script),
    undefinedIfBlank(dto.secondaryScript),
    undefinedIfBlank(dto.script),
    undefinedIfBlank(dto.readingDirection),
    undefined,
    undefinedIfBlank(dto.production),
    undefinedIfBlank(dto.custom),
    undefinedIfBlank(dto.comments),
    convertAlternativeImagesFromDto(dto.alternativeImages),
    convertLabelsFromDto(dto.labels),
    convertUserDefinedFromDto(dto.userDefined),
    convertTextStyleFromDto(dto.textStyle)
  )
}

function convertWordToDto(word: Word): WordDto {
  return {
    id: word.id,
    coords: convertPolygonToDto(word.coords),
    textContentVariants: word.textContentVariants?.map(convertTextContentVariantToDto),
    glyphs: word.glyphs?.map(convertGlyphToDto),
    alternativeImages: convertAlternativeImagesToDto(word.alternativeImages),
    labels: convertLabelsToDto(word.labels),
    userDefined: convertUserDefinedToDto(word.userDefined),
    textStyle: convertTextStyleToDto(word.textStyle),
    language: undefinedIfBlank(word.language),
    primaryScript: undefinedIfBlank(word.primaryScript) ?? undefinedIfBlank(word.script),
    secondaryScript: undefinedIfBlank(word.secondaryScript),
    script: undefinedIfBlank(word.script),
    readingDirection: undefinedIfBlank(word.readingDirection),
    production: undefinedIfBlank(word.production),
    confidence: word.confidence,
    custom: undefinedIfBlank(word.custom),
    comments: undefinedIfBlank(word.comments)
  }
}

export function convertTextLineFromDto(dto: TextLineDto): TextLine {
  return new TextLine({
    id: dto.id,
    coords: convertPolygonFromDto(dto.coords)!,
    baseline: dto.baseline ? { points: new Polyline(dto.baseline.points), conf: dto.baseline.confidence } : undefined,
    textContentVariants: dto.textContentVariants?.map(convertTextContentVariantFromDto),
    words: dto.words?.map(convertWordFromDto),
    alternativeImages: convertAlternativeImagesFromDto(dto.alternativeImages),
    labels: convertLabelsFromDto(dto.labels),
    userDefined: convertUserDefinedFromDto(dto.userDefined),
    textStyle: convertTextStyleFromDto(dto.textStyle),
    primaryLanguage: undefinedIfBlank(dto.primaryLanguage),
    primaryScript: undefinedIfBlank(dto.primaryScript),
    secondaryScript: undefinedIfBlank(dto.secondaryScript),
    readingDirection: undefinedIfBlank(dto.readingDirection) as TextLine['readingDirection'],
    production: undefinedIfBlank(dto.production) as TextLine['production'],
    custom: undefinedIfBlank(dto.custom),
    comments: undefinedIfBlank(dto.comments),
    index: dto.index,
    confidence: dto.confidence
  })
}

export function convertTextLineToDto(line: TextLine): TextLineDto {
  return {
    id: line.id,
    coords: convertPolygonToDto(line.coords),
    baseline: line.baseline?.points
      ? {
          points: line.baseline.points.points,
          confidence: line.baseline.conf
        }
      : undefined,
    textContentVariants: line.textContentVariants?.map(convertTextContentVariantToDto),
    words: line.words?.map(convertWordToDto),
    alternativeImages: convertAlternativeImagesToDto(line.alternativeImages),
    labels: convertLabelsToDto(line.labels),
    userDefined: convertUserDefinedToDto(line.userDefined),
    textStyle: convertTextStyleToDto(line.textStyle),
    bold: line.textStyle?.bold,
    italic: line.textStyle?.italic,
    underlined: line.textStyle?.underlined,
    underlineStyle: undefinedIfBlank(line.textStyle?.underlineStyle),
    subscript: line.textStyle?.subscript,
    superscript: line.textStyle?.superscript,
    strikethrough: line.textStyle?.strikethrough,
    smallCaps: line.textStyle?.smallCaps,
    letterSpaced: line.textStyle?.letterSpaced,
    primaryLanguage: undefinedIfBlank(line.primaryLanguage),
    primaryScript: undefinedIfBlank(line.primaryScript),
    secondaryScript: undefinedIfBlank(line.secondaryScript),
    readingDirection: undefinedIfBlank(line.readingDirection),
    production: undefinedIfBlank(line.production),
    custom: undefinedIfBlank(line.custom),
    comments: undefinedIfBlank(line.comments),
    index: line.index,
    confidence: line.confidence
  }
}
