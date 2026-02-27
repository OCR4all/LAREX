import type { Polygon, Polyline } from './geometry'

export interface Baseline {
  points: Polyline
  conf?: number
}

/** PAGE XML 2019: TextLineType */
export class TextLine {
  /** @id (xsd:ID) */
  id: string
  /** Required Coords */
  coords: Polygon
  /** Optional Baseline */
  baseline?: Baseline
  words?: Word[]
  textContentVariants?: TextContentVariant[]

  styleRefs?: string[]
  processingRefs?: string[]
  confidence?: number
  primaryLanguage?: string
  primaryScript?: string
  secondaryScript?: string
  readingDirection?: 'left-to-right' | 'right-to-left' | 'top-to-bottom' | 'bottom-to-top'
  production?: 'printed' | 'handwritten-cursive' | 'handwritten-printscript' | 'medieval-manuscript' | 'typewritten'
  custom?: string
  comments?: string
  index?: number

  constructor(params: {
    id: string
    coords: Polygon
    baseline?: Baseline
    words?: Word[]
    textContentVariants?: TextContentVariant[]
    styleRefs?: string[]
    processingRefs?: string[]
    confidence?: number
    primaryLanguage?: string
    primaryScript?: string
    secondaryScript?: string
    readingDirection?: 'left-to-right' | 'right-to-left' | 'top-to-bottom' | 'bottom-to-top'
    production?: 'printed' | 'handwritten-cursive' | 'handwritten-printscript' | 'medieval-manuscript' | 'typewritten'
    custom?: string
    comments?: string
    index?: number
  }) {
    this.id = params.id
    this.coords = params.coords
    this.baseline = params.baseline
    this.words = params.words
    this.textContentVariants = params.textContentVariants
    this.styleRefs = params.styleRefs
    this.processingRefs = params.processingRefs
    this.confidence = params.confidence
    this.primaryLanguage = params.primaryLanguage
    this.primaryScript = params.primaryScript
    this.secondaryScript = params.secondaryScript
    this.readingDirection = params.readingDirection
    this.production = params.production
    this.custom = params.custom
    this.comments = params.comments
    this.index = params.index
  }

  getText(): string {
    if (this.textContentVariants && this.textContentVariants.length > 0) {
      const indexed = this.textContentVariants.filter(te => typeof te.index === 'number')
      const best = indexed.length > 0
        ? indexed.reduce((min, current) => ((current.index as number) < (min.index as number) ? current : min))
        : this.textContentVariants[0]
      if (best?.unicode) return best.unicode
    }

    if (this.words) {
      return this.words
        .map(word => word.getText())
        .filter(Boolean)
        .join(' ')
    }

    return ''
  }
}

/** Word-level annotation */
export class Word {
  id: string
  coords: Polygon
  textContentVariants?: TextContentVariant[]
  glyphs?: Glyph[]
  styleRefs?: string[]
  processingRefs?: string[]
  confidence?: number
  language?: string
  script?: string
  readingDirection?: string
  metadata?: Record<string, unknown>

  constructor(
    id: string,
    coords: Polygon,
    textContentVariants?: TextContentVariant[],
    glyphs?: Glyph[],
    styleRefs?: string[],
    processingRefs?: string[],
    confidence?: number,
    language?: string,
    script?: string,
    readingDirection?: string,
    metadata?: Record<string, unknown>
  ) {
    this.id = id
    this.coords = coords
    this.textContentVariants = textContentVariants
    this.glyphs = glyphs
    this.styleRefs = styleRefs
    this.processingRefs = processingRefs
    this.confidence = confidence
    this.language = language
    this.script = script
    this.readingDirection = readingDirection
    this.metadata = metadata
  }

  addGlyph(glyph: Glyph): Word {
    return new Word(
      this.id,
      this.coords,
      this.textContentVariants,
      [...(this.glyphs || []), glyph],
      this.styleRefs,
      this.processingRefs,
      this.confidence,
      this.language,
      this.script,
      this.readingDirection,
      this.metadata
    )
  }

  getText(): string {
    if (this.textContentVariants && this.textContentVariants.length > 0) {
      const indexed = this.textContentVariants.filter(te => typeof te.index === 'number')
      const best = indexed.length > 0
        ? indexed.reduce((min, current) => ((current.index as number) < (min.index as number) ? current : min))
        : this.textContentVariants[0]
      if (best?.unicode) return best.unicode
    }

    if (this.glyphs) {
      return this.glyphs
        .map(glyph => glyph.unicode || '')
        .join('')
    }

    return ''
  }

  getCharacterCount(): number {
    return this.getText().length
  }
}

/** Character-level annotation */
export class Glyph {
  id: string
  coords: Polygon
  unicode?: string
  ligature?: boolean
  symbol?: boolean
  script?: string
  production?: 'manual' | 'automatic'
  confidence?: number
  metadata?: Record<string, unknown>

  constructor(
    id: string,
    coords: Polygon,
    unicode?: string,
    ligature?: boolean,
    symbol?: boolean,
    script?: string,
    production?: 'manual' | 'automatic',
    confidence?: number,
    metadata?: Record<string, unknown>
  ) {
    this.id = id
    this.coords = coords
    this.unicode = unicode
    this.ligature = ligature
    this.symbol = symbol
    this.script = script
    this.production = production
    this.confidence = confidence
    this.metadata = metadata
  }

  isLigature(): boolean {
    return this.ligature || false
  }

  isSymbol(): boolean {
    return this.symbol || false
  }
}

/** Unified text representation (PAGE XML TextEquiv) */
export class TextContentVariant {
  unicode: string
  plainText?: string
  confidence?: number
  index?: number
  dataType?: string
  dataTypeDetails?: string
  comments?: string

  constructor(
    unicode: string,
    plainText?: string,
    confidence?: number,
    index?: number,
    dataType?: string,
    dataTypeDetails?: string,
    comments?: string
  ) {
    this.unicode = unicode
    this.plainText = plainText
    this.confidence = confidence
    this.index = index
    this.dataType = dataType
    this.dataTypeDetails = dataTypeDetails
    this.comments = comments
  }

  getDisplayText(): string {
    return this.plainText || this.unicode
  }
}
