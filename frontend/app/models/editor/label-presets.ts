/**
 * Preset Label Sets
 *
 * This file contains predefined label sets that can be used with the editor.
 * Users can use these presets or create their own custom label sets.
 */

import { LabelSet, LabelDefinition } from './labels'
import type { LabelMapping } from './labels'

const TEXT_SUBTYPE_COLORS = [
  '#1E88E5',
  '#D81B60',
  '#8E24AA',
  '#5E35B1',
  '#3949AB',
  '#039BE5',
  '#00897B',
  '#43A047',
  '#7CB342',
  '#C0CA33',
  '#FDD835',
  '#FFB300',
  '#FB8C00',
  '#F4511E',
  '#6D4C41',
  '#546E7A',
  '#00ACC1',
  '#7E57C2'
]

function getTextSubtypeColor(index: number): string {
  return TEXT_SUBTYPE_COLORS[index % TEXT_SUBTYPE_COLORS.length] || '#4CAF50'
}

function createMapping(pageRegionType: string | null, textType: string | null = null, customSubType: string | null = null): LabelMapping {
  return {
    pageXml: {
      regionType: pageRegionType,
      textType: textType,
      customSubType,
      customKey: 'structure',
      customData: ''
    }
  }
}

/**
 * PAGE XML Standard Label Set
 *
 * Provides labels matching the PAGE XML schema region types.
 * Includes all 14 region types + CustomRegion and TextRegion subtypes.
 */
export function createPageXmlLabelSet(): LabelSet {
  const labels: LabelDefinition[] = []

  const addLabel = (
    id: string,
    name: string,
    scope: 'region' | 'line',
    color: string,
    description: string,
    hasText: boolean,
    group: string | null,
    mapping: LabelMapping
  ) => {
    labels.push(new LabelDefinition(
      id, name, scope, color, description, hasText, false, group, mapping
    ))
  }

  addLabel('text-region', 'Text Region', 'region', '#4CAF50', 'Pure text content region', true, 'Text',
    createMapping('TextRegion'))

  addLabel('image-region', 'Image Region', 'region', '#FF5722', 'Photographic or pictorial image region', false, null,
    createMapping('ImageRegion'))

  addLabel('line-drawing-region', 'Line Drawing Region', 'region', '#607D8B', 'Single color illustration without solid areas', false, null,
    createMapping('LineDrawingRegion'))

  addLabel('graphic-region', 'Graphic Region', 'region', '#795548', 'Simple graphic such as a company logo', false, null,
    createMapping('GraphicRegion'))

  addLabel('table-region', 'Table Region', 'region', '#2196F3', 'Tabular data in any form', false, null,
    createMapping('TableRegion'))

  addLabel('chart-region', 'Chart Region', 'region', '#9C27B0', 'Chart or graph region', false, null,
    createMapping('ChartRegion'))

  addLabel('map-region', 'Map Region', 'region', '#8BC34A', 'Map or cartographic content', false, null,
    createMapping('MapRegion'))

  addLabel('separator-region', 'Separator Region', 'region', '#9E9E9E', 'Line separating columns or paragraphs', false, null,
    createMapping('SeparatorRegion'))

  addLabel('maths-region', 'Maths Region', 'region', '#FF9800', 'Equations and mathematical symbols', false, null,
    createMapping('MathsRegion'))

  addLabel('chem-region', 'Chemistry Region', 'region', '#00BCD4', 'Chemical formulas', false, null,
    createMapping('ChemRegion'))

  addLabel('music-region', 'Music Region', 'region', '#CDDC39', 'Musical notations', false, null,
    createMapping('MusicRegion'))

  addLabel('advert-region', 'Advertisement Region', 'region', '#E91E63', 'Advertisement content', false, null,
    createMapping('AdvertRegion'))

  addLabel('noise-region', 'Noise Region', 'region', '#F44336', 'Noise or artifact (scanner noise, etc.)', false, null,
    createMapping('NoiseRegion'))

  addLabel('unknown-region', 'Unknown Region', 'region', '#757575', 'Region of unknown type', false, null,
    createMapping('UnknownRegion'))

  addLabel('custom-region', 'Custom Region', 'region', '#00BFA5', 'Custom region type for content not covered by standard types', false, null,
    createMapping('UnknownRegion', null, 'custom'))

  const textSubtypes = [
    { id: 'paragraph', name: 'Paragraph', desc: 'Body text paragraph' },
    { id: 'heading', name: 'Heading', desc: 'Section heading' },
    { id: 'caption', name: 'Caption', desc: 'Image or table caption' },
    { id: 'header', name: 'Header', desc: 'Page or section header' },
    { id: 'footer', name: 'Footer', desc: 'Page or section footer' },
    { id: 'page-number', name: 'Page Number', desc: 'Page number indicator' },
    { id: 'drop-capital', name: 'Drop Capital', desc: 'Drop capital letter' },
    { id: 'credit', name: 'Credit', desc: 'Credits or byline information' },
    { id: 'floating', name: 'Floating', desc: 'Floating text element' },
    { id: 'signature-mark', name: 'Signature Mark', desc: 'Signature or mark' },
    { id: 'catch-word', name: 'Catch Word', desc: 'Catch word at page bottom' },
    { id: 'marginalia', name: 'Marginalia', desc: 'Marginal notes' },
    { id: 'footnote', name: 'Footnote', desc: 'Footnote text' },
    { id: 'footnote-continued', name: 'Footnote Continued', desc: 'Continued footnote' },
    { id: 'endnote', name: 'Endnote', desc: 'Endnote text' },
    { id: 'TOC-entry', name: 'TOC Entry', desc: 'Table of contents entry' },
    { id: 'list-label', name: 'List Label', desc: 'List item label' },
    { id: 'other', name: 'Other', desc: 'Other text type' }
  ]

  for (const [index, subtype] of textSubtypes.entries()) {
    addLabel(
      `text-subtype-${subtype.id}`,
      subtype.name,
      'region',
      getTextSubtypeColor(index),
      subtype.desc,
      true,
      'Text',
      createMapping('TextRegion', subtype.id)
    )
  }

  addLabel('text-line', 'Text Line', 'line', '#3B82F6', 'A line of text within a text region', false, null,
    createMapping(null))

  return new LabelSet(
    'page-xml',
    'PAGE XML Standard',
    labels,
    'Standard PAGE XML region types based on the PAGE XML schema. This is a system-provided labelset.'
  )
}

/**
 * Simple Document Label Set
 *
 * A minimal label set for basic document annotation with text support.
 */
export function createSimpleDocumentLabelSet(): LabelSet {
  const labels: LabelDefinition[] = [
    new LabelDefinition(
      'text', 'Text', 'region', '#4CAF50', 'Text content', true, false, null,
      { pageXml: { regionType: null, textType: null, customKey: 'structure', customData: '' } }
    ),
    new LabelDefinition(
      'image', 'Image', 'region', '#2196F3', 'Image content', false, false, null,
      { pageXml: { regionType: null, textType: null, customKey: 'structure', customData: '' } }
    ),
    new LabelDefinition(
      'table', 'Table', 'region', '#9C27B0', 'Table content', false, false, null,
      { pageXml: { regionType: null, textType: null, customKey: 'structure', customData: '' } }
    ),
    new LabelDefinition(
      'title', 'Title', 'region', '#FF9800', 'Document title', true, false, null,
      { pageXml: { regionType: null, textType: null, customKey: 'structure', customData: '' } }
    ),
    new LabelDefinition(
      'caption', 'Caption', 'region', '#00BCD4', 'Image or table caption', true, false, null,
      { pageXml: { regionType: null, textType: null, customKey: 'structure', customData: '' } }
    )
  ]

  return new LabelSet(
    'simple-document',
    'Simple Document',
    labels,
    'A minimal label set for basic document annotation'
  )
}

/**
 * Newspaper Label Set
 *
 * Labels for annotating newspaper layouts.
 */
export function createNewspaperLabelSet(): LabelSet {
  const labels: LabelDefinition[] = [
    new LabelDefinition(
      'headline', 'Headline', 'region', '#F44336', 'Article headline', true, false, null,
      { pageXml: { regionType: null, textType: null, customKey: 'structure', customData: '' } }
    ),
    new LabelDefinition(
      'article', 'Article', 'region', '#4CAF50', 'Article body text', true, false, null,
      { pageXml: { regionType: null, textType: null, customKey: 'structure', customData: '' } }
    ),
    new LabelDefinition(
      'photo', 'Photo', 'region', '#2196F3', 'Photograph', false, false, null,
      { pageXml: { regionType: null, textType: null, customKey: 'structure', customData: '' } }
    ),
    new LabelDefinition(
      'caption', 'Caption', 'region', '#00BCD4', 'Photo caption', true, false, null,
      { pageXml: { regionType: null, textType: null, customKey: 'structure', customData: '' } }
    ),
    new LabelDefinition(
      'byline', 'Byline', 'region', '#9C27B0', 'Author byline', true, false, null,
      { pageXml: { regionType: null, textType: null, customKey: 'structure', customData: '' } }
    ),
    new LabelDefinition(
      'advertisement', 'Advertisement', 'region', '#FF9800', 'Advertisement', false, false, null,
      { pageXml: { regionType: null, textType: null, customKey: 'structure', customData: '' } }
    )
  ]

  return new LabelSet(
    'newspaper',
    'Newspaper',
    labels,
    'Labels for newspaper layout annotation'
  )
}

/**
 * Get all available preset label sets
 */
export function getAllPresetLabelSets(): LabelSet[] {
  return [
    createPageXmlLabelSet(),
    createSimpleDocumentLabelSet(),
    createNewspaperLabelSet()
  ]
}

/**
 * Get a preset label set by ID
 */
export function getPresetLabelSet(id: string): LabelSet | undefined {
  const presets = getAllPresetLabelSets()
  return presets.find(preset => preset.id === id)
}
