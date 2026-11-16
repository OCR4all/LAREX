import type {
  AlternativeImageDto,
  LabelsDto,
  PageDto,
  PolygonDto,
  RegionDto,
  TextContentVariantDto,
  TextStyleDto,
  UserDefinedDto
} from '@/types/page-dto'

function polygon(offset: number, confidence: number): PolygonDto {
  return {
    points: [[offset, offset], [offset + 0.125, offset], [offset + 0.125, offset - 0.0625], [offset, offset - 0.0625]],
    confidence
  }
}

function labels(owner: string): LabelsDto[] {
  return [
    {
      externalModel: `${owner}-model`,
      externalId: `${owner}-external`,
      prefix: `${owner}-prefix`,
      comments: `${owner} labels comment`,
      labels: [
        { value: `${owner}-value-1`, type: `${owner}-type-1`, comments: `${owner} label comment 1` },
        { value: `${owner}-value-2`, type: `${owner}-type-2`, comments: `${owner} label comment 2` }
      ]
    },
    {
      externalModel: `${owner}-model-b`,
      labels: [{ value: `${owner}-value-b` }]
    }
  ]
}

function userDefined(owner: string): UserDefinedDto {
  return {
    attributes: [
      { name: `${owner}-string`, description: `${owner} description`, type: 'xsd:string', value: `${owner} value` },
      { name: `${owner}-integer`, type: 'xsd:integer', value: '42' },
      { name: `${owner}-boolean`, type: 'xsd:boolean', value: 'true' },
      { name: `${owner}-float`, type: 'xsd:float', value: '0.25' }
    ]
  }
}

function alternativeImages(owner: string): AlternativeImageDto[] {
  return [
    { filename: `${owner}-binarized.png`, comments: `${owner} binarized`, confidence: 0.81 },
    { filename: `${owner}-raw.png` }
  ]
}

function textStyle(owner: string, fontSize: number): TextStyleDto {
  return {
    fontFamily: `${owner} Garamond`,
    serif: true,
    monospace: false,
    fontSize,
    xHeight: 11,
    kerning: 2,
    textColour: 'black',
    textColourRgb: 1052688,
    bgColour: 'white',
    bgColourRgb: 16777215,
    reverseVideo: false,
    bold: true,
    italic: false,
    underlined: true,
    underlineStyle: 'doubleLine',
    subscript: false,
    superscript: true,
    strikethrough: false,
    smallCaps: true,
    letterSpaced: false
  }
}

function variants(owner: string): TextContentVariantDto[] {
  return [
    {
      unicode: `${owner} ſ text`,
      plainText: `${owner} s text`,
      index: 0,
      confidence: 0.93,
      comments: `${owner} ground truth`,
      dataType: 'xsd:string',
      dataTypeDetails: `${owner} details`
    },
    { unicode: `${owner} alternative`, index: 1, confidence: 0.4 }
  ]
}

function regionBase(id: string, kind: string, offset: number): RegionDto {
  return {
    id,
    kind,
    coords: polygon(offset, 0.71),
    alternativeImages: alternativeImages(id),
    labels: labels(id),
    userDefined: userDefined(id),
    orientation: -1.5,
    custom: `readingOrder {index:${offset};} ${id}`,
    comments: `${id} comment`,
    continuation: true,
    confidence: 0.66
  }
}

const tableCell: RegionDto = {
  ...regionBase('cell-1', 'TextRegion', 0.3),
  type: 'paragraph',
  roles: { tableCellRole: { rowIndex: 1, columnIndex: 2, rowSpan: 2, colSpan: 3, header: true } },
  textContentVariants: [{ unicode: 'cell text', index: 0 }]
}

const textRegion: RegionDto = {
  ...regionBase('r-text', 'TextRegion', -0.9),
  textStyle: textStyle('r-text', 12.5),
  type: 'heading',
  textColour: 'black',
  bgColour: 'yellow',
  reverseVideo: false,
  fontSize: 12.5,
  fontFamily: 'Garamond',
  serif: true,
  monospace: false,
  xHeight: 11,
  leading: 4,
  kerning: 2,
  align: 'justify',
  textColourRgb: 1052688,
  bgColourRgb: 16776960,
  readingDirection: 'left-to-right',
  readingOrientation: 0.5,
  textLineOrder: 'top-to-bottom',
  indented: true,
  primaryLanguage: 'German',
  secondaryLanguage: 'Latin',
  primaryScript: 'Latf - Latin (Fraktur variant)',
  secondaryScript: 'Latn - Latin',
  production: 'printed',
  mirrored: 'horizontally',
  customLanguages: 'r-text languages',
  customScripts: 'r-text scripts',
  textContentVariants: variants('r-text'),
  textLines: [
    {
      id: 'l1',
      coords: polygon(-0.85, 0.72),
      baseline: { points: [[-0.85, -0.9], [-0.7, -0.9]], confidence: 0.61 },
      textContentVariants: variants('l1'),
      alternativeImages: alternativeImages('l1'),
      labels: labels('l1'),
      userDefined: userDefined('l1'),
      textStyle: textStyle('l1', 10),
      bold: true,
      italic: false,
      underlined: true,
      underlineStyle: 'doubleLine',
      subscript: false,
      superscript: true,
      strikethrough: false,
      smallCaps: true,
      letterSpaced: false,
      primaryLanguage: 'German',
      primaryScript: 'Latf - Latin (Fraktur variant)',
      secondaryScript: 'Latn - Latin',
      readingDirection: 'left-to-right',
      production: 'handwritten-cursive',
      custom: 'structure {type:line;}',
      comments: 'l1 comment',
      index: 0,
      confidence: 0.88,
      orientation: 2.5,
      mirrored: 'vertically',
      secondaryLanguage: 'Latin',
      customLanguages: 'l1 languages',
      customScripts: 'l1 scripts',
      ascentLine: { points: [[-0.85, -0.95], [-0.7, -0.95]], confidence: 0.62 },
      meanLine: { points: [[-0.85, -0.93], [-0.7, -0.93]] },
      descentLine: { points: [[-0.85, -0.88], [-0.7, -0.88]], confidence: 0.63 },
      words: [
        {
          id: 'w1',
          coords: polygon(-0.84, 0.73),
          textContentVariants: variants('w1'),
          alternativeImages: alternativeImages('w1'),
          labels: labels('w1'),
          userDefined: userDefined('w1'),
          textStyle: textStyle('w1', 9),
          language: 'German',
          primaryScript: 'Latf - Latin (Fraktur variant)',
          secondaryScript: 'Latn - Latin',
          script: 'Latf - Latin (Fraktur variant)',
          readingDirection: 'left-to-right',
          production: 'printed',
          confidence: 0.77,
          custom: 'w1 custom',
          comments: 'w1 comment',
          orientation: -3,
          mirrored: 'other',
          customLanguages: 'w1 languages',
          customScripts: 'w1 scripts',
          glyphs: [
            {
              id: 'gl1',
              coords: polygon(-0.83, 0.74),
              textContentVariants: [
                { unicode: 'ſ', index: 0, confidence: 0.95 },
                { unicode: 's', index: 1, confidence: 0.5 }
              ],
              alternativeImages: alternativeImages('gl1'),
              labels: labels('gl1'),
              userDefined: userDefined('gl1'),
              textStyle: textStyle('gl1', 8),
              ligature: true,
              symbol: false,
              script: 'Latf - Latin (Fraktur variant)',
              production: 'printed',
              confidence: 0.91,
              custom: 'gl1 custom',
              comments: 'gl1 comment',
              orientation: 1,
              mirrored: 'not-mirrored',
              customScript: 'gl1 script',
              graphemes: {
                elements: [
                  {
                    kind: 'grapheme',
                    id: 'gr1',
                    index: 0,
                    charType: 'base',
                    ligature: false,
                    custom: 'gr1 custom',
                    comments: 'gr1 comment',
                    coords: polygon(-0.82, 0.75),
                    textContentVariants: [{ unicode: 'ſ', index: 0, confidence: 0.9 }],
                    labels: labels('gr1')
                  },
                  {
                    kind: 'nonPrintingChar',
                    id: 'np1',
                    index: 1,
                    charType: 'combining',
                    textContentVariants: [{ unicode: '‍', index: 0 }]
                  },
                  {
                    kind: 'graphemeGroup',
                    id: 'gg1',
                    index: 2,
                    charType: 'base',
                    ligature: true,
                    custom: 'gg1 custom',
                    comments: 'gg1 comment',
                    labels: labels('gg1'),
                    members: [
                      { kind: 'grapheme', id: 'gr2', index: 0, coords: polygon(-0.81, 0.76), textContentVariants: [{ unicode: 't', index: 0 }] },
                      { kind: 'nonPrintingChar', id: 'np2', index: 1, textContentVariants: [{ unicode: '‌', index: 0 }] }
                    ]
                  }
                ]
              }
            }
          ]
        }
      ]
    }
  ],
  nestedRegions: [
    {
      ...regionBase('r-nested', 'TextRegion', -0.6),
      type: 'marginalia',
      textContentVariants: [{ unicode: 'nested', index: 0 }]
    }
  ]
}

const tableRegion: RegionDto = {
  ...regionBase('r-table', 'TableRegion', 0.2),
  rows: 4,
  columns: 5,
  lineColour: 'blue',
  bgColour: 'white',
  lineSeparators: true,
  embText: true,
  borderPresent: true,
  grid: {
    rows: [
      { index: 0, points: { points: [[0.2, 0.2], [0.4, 0.2], [0.6, 0.2]] } },
      { index: 1, points: { points: [[0.2, 0.1], [0.4, 0.1], [0.6, 0.1]] } }
    ],
    addPoints: [
      { row: 0, col: 1, left: '495,1000', top: '700,905 710,905' },
      { row: 1, col: 0, top: 'free text' }
    ]
  },
  nestedRegions: [tableCell]
}

export function completePageDto(): PageDto {
  return {
    imageFilename: 'page-0001.tif',
    imageWidth: 2480,
    imageHeight: 3508,
    imageXResolution: 300,
    imageYResolution: 301.5,
    imageResolutionUnit: 'PPI',
    pcGtsId: 'pc-0001',
    type: 'content',
    custom: 'page custom',
    orientation: 0.25,
    primaryLanguage: 'German',
    secondaryLanguage: 'French',
    primaryScript: 'Latf - Latin (Fraktur variant)',
    secondaryScript: 'Latn - Latin',
    readingDirection: 'left-to-right',
    textLineOrder: 'top-to-bottom',
    confidence: 0.97,
    formatVersion: '2019-07-15',
    comments: 'page comment',
    customLanguages: 'page languages',
    customScripts: 'page scripts',
    metadata: {
      creator: 'LAREX',
      created: '2026-01-02T10:00:00',
      lastChange: '2026-01-03T11:30:00',
      comments: 'metadata comment',
      externalRef: 'urn:example:page-0001',
      userDefined: userDefined('metadata'),
      items: [
        {
          type: 'processingStep',
          name: 'OCR',
          value: 'kraken 5.2',
          date: '2026-01-02T10:05:00',
          labels: labels('item')
        },
        { type: 'author', value: 'Ada' }
      ]
    },
    border: polygon(-0.95, 0.5),
    printSpace: polygon(-0.92, 0.6),
    alternativeImages: alternativeImages('page'),
    labels: labels('page'),
    userDefined: userDefined('page'),
    textStyle: textStyle('page', 11),
    regions: [
      textRegion,
      tableRegion,
      { ...regionBase('r-image', 'ImageRegion', 0.5), colourDepth: 'colour', bgColour: 'grey', embText: true, mirrored: 'horizontally' },
      { ...regionBase('r-drawing', 'LineDrawingRegion', 0.55), penColour: 'red', bgColour: 'white', embText: false },
      { ...regionBase('r-graphic', 'GraphicRegion', 0.6), type: 'stamp', numColours: 3, embText: true },
      { ...regionBase('r-chart', 'ChartRegion', 0.65), type: 'pie', numColours: 4, bgColour: 'white', embText: false },
      { ...regionBase('r-separator', 'SeparatorRegion', 0.7), colour: 'black' },
      { ...regionBase('r-maths', 'MathsRegion', 0.72), bgColour: 'white' },
      { ...regionBase('r-chem', 'ChemRegion', 0.74), bgColour: 'white' },
      { ...regionBase('r-music', 'MusicRegion', 0.76), bgColour: 'white' },
      { ...regionBase('r-advert', 'AdvertRegion', 0.78), bgColour: 'yellow' },
      regionBase('r-map', 'MapRegion', 0.8),
      regionBase('r-noise', 'NoiseRegion', 0.82),
      regionBase('r-unknown', 'UnknownRegion', 0.84),
      { ...regionBase('r-custom', 'CustomRegion', 0.86), type: 'decoration' },
      { ...regionBase('r-form', 'FormRegion', 0.88), mirrored: 'vertically', bgColour: 'white' }
    ],
    readingOrder: {
      confidence: 0.42,
      root: {
        id: 'ro-root',
        ordered: true,
        caption: 'Root',
        groupType: 'article',
        regionRef: 'r-text',
        continuation: true,
        userDefined: userDefined('ro-root'),
        labels: labels('ro-root'),
        custom: 'ro-root custom',
        comments: 'ro-root comment',
        members: [
          { type: 'regionRef', id: 'ref-text', regionRef: 'r-text', index: 0 },
          {
            type: 'nestedGroup',
            group: {
              id: 'ro-marginalia',
              ordered: false,
              index: 1,
              caption: 'Marginalia',
              groupType: 'other',
              members: [
                { type: 'regionRef', id: 'ref-image', regionRef: 'r-image' },
                { type: 'regionRef', id: 'ref-graphic', regionRef: 'r-graphic' }
              ]
            }
          },
          {
            type: 'nestedGroup',
            group: {
              id: 'ro-table',
              ordered: true,
              index: 2,
              members: [
                { type: 'regionRef', id: 'ref-table', regionRef: 'r-table', index: 0 },
                { type: 'regionRef', id: 'ref-cell', regionRef: 'cell-1', index: 1 }
              ]
            }
          }
        ]
      }
    },
    layers: {
      layers: [
        { id: 'layer-back', zIndex: 0, caption: 'Background', regionRefs: ['r-image', 'r-graphic'] },
        { id: 'layer-front', zIndex: 2, caption: 'Foreground', regionRefs: ['r-text'] }
      ]
    },
    relations: {
      relations: [
        {
          id: 'rel-1',
          type: 'link',
          sourceRegionRef: 'r-text',
          targetRegionRef: 'r-image',
          custom: 'rel-1 custom',
          comments: 'rel-1 comment',
          labels: labels('rel-1')
        },
        { id: 'rel-2', type: 'join', sourceRegionRef: 'r-text', targetRegionRef: 'r-nested' }
      ]
    }
  }
}
