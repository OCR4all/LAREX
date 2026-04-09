/**
 * Color mapping for PAGE XML region types and subtypes.
 * Provides distinct colors for visual differentiation in the editor.
 */

import type { RGBA } from '@/utils/editor/editor-constants'
import type { RegionKind, TextRegionSubtype, GraphicRegionSubtype, ChartRegionSubtype } from '@/models/editor/region'

/** Default colors for each PAGE XML region kind */
export const REGION_KIND_COLORS: Record<RegionKind, string> = {
  TextRegion: '#4CAF50', // Green
  ImageRegion: '#E91E63', // Pink
  LineDrawingRegion: '#9C27B0', // Purple
  GraphicRegion: '#673AB7', // Deep Purple
  TableRegion: '#3F51B5', // Indigo
  ChartRegion: '#2196F3', // Blue
  MapRegion: '#00BCD4', // Cyan
  SeparatorRegion: '#607D8B', // Blue Grey
  MathsRegion: '#FF9800', // Orange
  ChemRegion: '#FF5722', // Deep Orange
  MusicRegion: '#795548', // Brown
  AdvertRegion: '#CDDC39', // Lime
  NoiseRegion: '#9E9E9E', // Grey
  UnknownRegion: '#757575', // Dark Grey
  CustomRegion: '#00BFA5' // Teal accent
}

/** Colors for TextRegion subtypes (TextTypeSimpleType) */
export const TEXT_REGION_SUBTYPE_COLORS: Record<TextRegionSubtype, string> = {
  'paragraph': '#4CAF50', // Green (default text)
  'heading': '#1976D2', // Blue
  'caption': '#7B1FA2', // Purple
  'header': '#0288D1', // Light Blue
  'footer': '#00796B', // Teal
  'page-number': '#5D4037', // Brown
  'drop-capital': '#C2185B', // Pink
  'credit': '#512DA8', // Deep Purple
  'floating': '#00ACC1', // Cyan
  'signature-mark': '#8D6E63', // Brown (lighter)
  'catch-word': '#6D4C41', // Brown (dark)
  'marginalia': '#689F38', // Light Green
  'footnote': '#FFA000', // Amber
  'footnote-continued': '#FF8F00', // Amber (darker)
  'endnote': '#F57C00', // Orange
  'TOC-entry': '#0097A7', // Cyan (darker)
  'list-label': '#388E3C', // Green (darker)
  'other': '#78909C' // Blue Grey
}

/** Colors for GraphicRegion subtypes (GraphicsTypeSimpleType) */
export const GRAPHIC_REGION_SUBTYPE_COLORS: Record<GraphicRegionSubtype, string> = {
  'logo': '#673AB7', // Deep Purple
  'letterhead': '#5E35B1', // Deep Purple (darker)
  'decoration': '#AB47BC', // Purple
  'frame': '#7E57C2', // Deep Purple (lighter)
  'handwritten-annotation': '#EC407A', // Pink
  'stamp': '#D81B60', // Pink (darker)
  'signature': '#AD1457', // Pink (dark)
  'barcode': '#37474F', // Blue Grey (dark)
  'paper-grow': '#90A4AE', // Blue Grey (light)
  'punch-hole': '#546E7A', // Blue Grey
  'other': '#78909C' // Blue Grey
}

/** Colors for ChartRegion subtypes (ChartTypeSimpleType) */
export const CHART_REGION_SUBTYPE_COLORS: Record<ChartRegionSubtype, string> = {
  bar: '#2196F3', // Blue
  line: '#03A9F4', // Light Blue
  pie: '#00BCD4', // Cyan
  scatter: '#009688', // Teal
  surface: '#4DD0E1', // Cyan (light)
  other: '#78909C' // Blue Grey
}

/**
 * Get the color for a region based on its kind and optional subtype.
 * @param kind The region kind (TextRegion, ImageRegion, etc.)
 * @param subtype Optional subtype for regions that support it
 * @returns Hex color string
 */
export function getRegionColor(kind: RegionKind, subtype?: string): string {
  if (subtype) {
    if (kind === 'TextRegion' && subtype in TEXT_REGION_SUBTYPE_COLORS) {
      return TEXT_REGION_SUBTYPE_COLORS[subtype as TextRegionSubtype]
    }
    if (kind === 'GraphicRegion' && subtype in GRAPHIC_REGION_SUBTYPE_COLORS) {
      return GRAPHIC_REGION_SUBTYPE_COLORS[subtype as GraphicRegionSubtype]
    }
    if (kind === 'ChartRegion' && subtype in CHART_REGION_SUBTYPE_COLORS) {
      return CHART_REGION_SUBTYPE_COLORS[subtype as ChartRegionSubtype]
    }
  }

  return REGION_KIND_COLORS[kind] || '#4CAF50'
}

/**
 * Get the color for a region as RGBA array (for WebGL rendering).
 * @param kind The region kind
 * @param subtype Optional subtype
 * @param alpha Alpha value (0-1)
 * @returns RGBA array [r, g, b, a] normalized to 0-1
 */
export function getRegionColorRGBA(
  kind: RegionKind,
  subtype?: string,
  alpha: number = 0.3
): RGBA {
  const hex = getRegionColor(kind, subtype)
  return hexToRgba(hex, alpha)
}

/**
 * Convert hex color to RGBA array.
 * @param hex Hex color string (e.g., "#4CAF50" or "4CAF50")
 * @param alpha Alpha value (0-1)
 * @returns RGBA array [r, g, b, a] normalized to 0-1
 */
export function hexToRgba(hex: string, alpha: number = 1.0): RGBA {
  hex = hex.replace(/^#/, '')

  const r = parseInt(hex.substring(0, 2), 16) / 255
  const g = parseInt(hex.substring(2, 4), 16) / 255
  const b = parseInt(hex.substring(4, 6), 16) / 255

  return [r, g, b, alpha]
}

/**
 * Get display name for a region kind.
 */
export function getRegionKindDisplayName(kind: RegionKind): string {
  return kind.replace(/Region$/, '').replace(/([A-Z])/g, ' $1').trim()
}

/**
 * Get display name for a TextRegion subtype.
 */
export function getTextRegionSubtypeDisplayName(subtype: TextRegionSubtype): string {
  return subtype
    .split('-')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

/**
 * Get icon name for a region kind (Lucide icons).
 */
export function getRegionKindIcon(kind: RegionKind): string {
  const icons: Record<RegionKind, string> = {
    TextRegion: 'lucide:type',
    ImageRegion: 'lucide:image',
    LineDrawingRegion: 'lucide:pen-line',
    GraphicRegion: 'lucide:shapes',
    TableRegion: 'lucide:table',
    ChartRegion: 'lucide:bar-chart-2',
    MapRegion: 'lucide:map',
    SeparatorRegion: 'lucide:minus',
    MathsRegion: 'lucide:sigma',
    ChemRegion: 'lucide:flask-conical',
    MusicRegion: 'lucide:music',
    AdvertRegion: 'lucide:megaphone',
    NoiseRegion: 'lucide:alert-triangle',
    UnknownRegion: 'lucide:help-circle',
    CustomRegion: 'lucide:tag'
  }
  return icons[kind] || 'lucide:square'
}
