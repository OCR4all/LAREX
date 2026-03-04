export { PolygonType } from './types'
export type {
  Vertex,
  Point,
  PolygonData,
  PolylineData,
  TextContentVariantData,
  BoundingBox,
  View,
  AspectRatioScale,
  ImageSize,
  HoveredEdgeInfo,
  DraggedNodeInfo,
  GetWorldCoordsFunction
} from './types'

export { Polygon, Polyline } from './geometry'

export { TextLine, Word, Glyph, TextContentVariant } from './text'
export type { Baseline, GraphemeElement, Graphemes } from './text'

export {
  isTextRegion,
  canContainTextLines,
  parseCustomLabels,
  serializeCustomLabels,
  ALL_REGION_KINDS,
  ALL_TEXT_REGION_SUBTYPES,
  ALL_GRAPHIC_REGION_SUBTYPES,
  ALL_CHART_REGION_SUBTYPES,
  TEXT_CAPABLE_REGION_KINDS
} from './region'
export type {
  Region,
  RegionBase,
  TextRegion,
  GraphicRegion,
  ChartRegion,
  GenericRegion,
  RegionKind,
  TextRegionSubtype,
  GraphicRegionSubtype,
  ChartRegionSubtype,
  CustomLabel,
  CustomLabelData
} from './region'

export { Page } from './page'
export type { Border, Layer, PrintSpace, Relation } from './page'

export {
  type ReadingOrder,
  type ReadingOrderGroup,
  type ReadingOrderNode,
  type OrderedGroup,
  type UnorderedGroup,
  type OrderedGroupIndexed,
  type UnorderedGroupIndexed,
  type RegionRef
} from './reading-order'

export { LabelSet, LabelDefinition } from './labels'

export {
  createPageXmlLabelSet,
  createSimpleDocumentLabelSet,
  createNewspaperLabelSet,
  getAllPresetLabelSets,
  getPresetLabelSet
} from './label-presets'

export { Styles, TextStyle, ParagraphStyle } from './styles'

export {
  PcGts,
  Metadata
} from './document'

export type {
  AlternativeImage,
  Label,
  Labels,
  MetadataItem,
  MetadataItemType,
  TextStyleAttributes,
  UserAttribute,
  UserAttributeType,
  UserDefined
} from './document'
