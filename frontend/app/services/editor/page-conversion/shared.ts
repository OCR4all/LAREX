import {
  Polygon,
  type AlternativeImage,
  type Labels,
  type Layer,
  type TextStyleAttributes,
  type UserAttribute,
  type UserDefined
} from '@/models/editor'
import type {
  AlternativeImageDto,
  LabelsDto,
  LayersDto,
  PolygonDto,
  TextStyleDto,
  UserAttributeDto,
  UserDefinedDto
} from '@/types/page-dto'

export function convertPolygonFromDto(dto?: PolygonDto): Polygon | undefined {
  if (!dto?.points) return undefined
  return new Polygon(dto.points)
}

export function convertPolygonToDto(polygon?: Polygon): PolygonDto | undefined {
  if (!polygon) return undefined
  return {
    points: polygon.points,
    confidence: undefined
  }
}

export function undefinedIfBlank(value?: unknown): string | undefined {
  if (typeof value === 'string') {
    const trimmed = value.trim()
    return trimmed.length > 0 ? trimmed : undefined
  }
  if (value && typeof value === 'object' && 'value' in value) {
    return undefinedIfBlank((value as { value?: unknown }).value)
  }
  return undefined
}

export function convertAlternativeImagesFromDto(images?: AlternativeImageDto[]): AlternativeImage[] | undefined {
  if (!images?.length) return undefined
  const converted = images
    .filter(img => typeof img.filename === 'string' && img.filename.length > 0)
    .map(img => ({
      filename: img.filename,
      comments: undefinedIfBlank(img.comments),
      confidence: img.confidence
    }))
  return converted.length > 0 ? converted : undefined
}

export function convertAlternativeImagesToDto(images?: AlternativeImage[]): AlternativeImageDto[] | undefined {
  if (!images?.length) return undefined
  const converted = images
    .filter(img => typeof img.filename === 'string' && img.filename.length > 0)
    .map(img => ({
      filename: img.filename,
      comments: undefinedIfBlank(img.comments),
      confidence: img.confidence
    }))
  return converted.length > 0 ? converted : undefined
}

export function convertLabelsFromDto(groups?: LabelsDto[]): Labels[] | undefined {
  if (!groups?.length) return undefined
  const converted = groups.map(group => ({
    externalModel: undefinedIfBlank(group.externalModel),
    externalId: undefinedIfBlank(group.externalId),
    prefix: undefinedIfBlank(group.prefix),
    comments: undefinedIfBlank(group.comments),
    labels: group.labels
      ?.filter(label => undefinedIfBlank(label.value))
      .map(label => ({
        value: label.value?.trim() ?? '',
        type: undefinedIfBlank(label.type),
        comments: undefinedIfBlank(label.comments)
      }))
  }))
  return converted.length > 0 ? converted : undefined
}

export function convertLabelsToDto(groups?: Labels[]): LabelsDto[] | undefined {
  if (!groups?.length) return undefined
  const converted = groups.map(group => ({
    externalModel: undefinedIfBlank(group.externalModel),
    externalId: undefinedIfBlank(group.externalId),
    prefix: undefinedIfBlank(group.prefix),
    comments: undefinedIfBlank(group.comments),
    labels: group.labels
      ?.filter(label => undefinedIfBlank(label.value))
      .map(label => ({
        value: label.value?.trim(),
        type: undefinedIfBlank(label.type),
        comments: undefinedIfBlank(label.comments)
      }))
  }))
  return converted.length > 0 ? converted : undefined
}

export function convertUserDefinedFromDto(userDefined?: UserDefinedDto): UserDefined | undefined {
  if (!userDefined?.attributes?.length) return undefined
  const attributes = userDefined.attributes
    .filter(attr => undefinedIfBlank(attr.name))
    .map(attr => ({
      name: undefinedIfBlank(attr.name),
      description: undefinedIfBlank(attr.description),
      type: attr.type,
      value: undefinedIfBlank(attr.value)
    })) as UserAttribute[]
  return attributes.length > 0 ? { attributes } : undefined
}

export function convertUserDefinedToDto(userDefined?: UserDefined): UserDefinedDto | undefined {
  if (!userDefined?.attributes?.length) return undefined
  const attributes = userDefined.attributes
    .filter(attr => undefinedIfBlank(attr.name))
    .map(attr => ({
      name: undefinedIfBlank(attr.name),
      description: undefinedIfBlank(attr.description),
      type: attr.type,
      value: undefinedIfBlank(attr.value)
    })) as UserAttributeDto[]
  return attributes.length > 0 ? { attributes } : undefined
}

export function convertTextStyleFromDto(style?: TextStyleDto): TextStyleAttributes | undefined {
  if (!style) return undefined
  const converted: TextStyleAttributes = {
    fontFamily: undefinedIfBlank(style.fontFamily),
    serif: style.serif,
    monospace: style.monospace,
    fontSize: style.fontSize,
    xHeight: style.xHeight,
    kerning: style.kerning,
    textColour: undefinedIfBlank(style.textColour),
    textColourRgb: style.textColourRgb,
    bgColour: undefinedIfBlank(style.bgColour),
    bgColourRgb: style.bgColourRgb,
    reverseVideo: style.reverseVideo,
    bold: style.bold,
    italic: style.italic,
    underlined: style.underlined,
    underlineStyle: undefinedIfBlank(style.underlineStyle),
    subscript: style.subscript,
    superscript: style.superscript,
    strikethrough: style.strikethrough,
    smallCaps: style.smallCaps,
    letterSpaced: style.letterSpaced
  }
  return Object.values(converted).some(value => value !== undefined) ? converted : undefined
}

export function convertTextStyleToDto(style?: TextStyleAttributes): TextStyleDto | undefined {
  if (!style) return undefined
  const converted: TextStyleDto = {
    fontFamily: undefinedIfBlank(style.fontFamily),
    serif: style.serif,
    monospace: style.monospace,
    fontSize: style.fontSize,
    xHeight: style.xHeight,
    kerning: style.kerning,
    textColour: undefinedIfBlank(style.textColour),
    textColourRgb: style.textColourRgb,
    bgColour: undefinedIfBlank(style.bgColour),
    bgColourRgb: style.bgColourRgb,
    reverseVideo: style.reverseVideo,
    bold: style.bold,
    italic: style.italic,
    underlined: style.underlined,
    underlineStyle: undefinedIfBlank(style.underlineStyle),
    subscript: style.subscript,
    superscript: style.superscript,
    strikethrough: style.strikethrough,
    smallCaps: style.smallCaps,
    letterSpaced: style.letterSpaced
  }
  return Object.values(converted).some(value => value !== undefined) ? converted : undefined
}

export function convertLayersFromDto(dto?: LayersDto): Layer[] | undefined {
  if (!dto?.layers?.length) return undefined
  return dto.layers.map(layer => ({
    id: undefinedIfBlank(layer.id),
    zIndex: layer.zIndex,
    caption: undefinedIfBlank(layer.caption),
    regionRefs: layer.regionRefs ?? []
  }))
}

export function convertLayersToDto(layers?: Layer[]): LayersDto | undefined {
  if (!layers?.length) return undefined
  return {
    layers: layers.map(layer => ({
      id: undefinedIfBlank(layer.id),
      zIndex: layer.zIndex,
      caption: undefinedIfBlank(layer.caption),
      regionRefs: layer.regionRefs ?? []
    }))
  }
}
