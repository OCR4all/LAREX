import type { AlternativeImage, Labels, TextStyleAttributes, UserDefined } from './document'

export interface Layer {
  id?: string
  zIndex?: number
  caption?: string
  regionRefs?: string[]
}

export interface Relation {
  id?: string
  type?: string
  sourceRegionRef?: string
  targetRegionRef?: string
  custom?: string
  comments?: string
  labels?: Labels[]
}

export class Page {
  imageFilename: string
  imageWidth: number
  imageHeight: number
  imageXResolution?: number
  imageYResolution?: number
  imageResolutionUnit?: 'PPI' | 'PPCM' | 'other'

  border?: Border
  printSpace?: PrintSpace
  readingOrder?: import('./reading-order').ReadingOrder
  alternativeImages?: AlternativeImage[]
  labels?: Labels[]
  userDefined?: UserDefined
  textStyle?: TextStyleAttributes
  layers?: Layer[]
  relations?: Relation[]

  regions: import('./region').Region[]

  custom?: string
  orientation?: number
  type?: 'front-cover' | 'back-cover' | 'title' | 'table-of-contents' | 'index' | 'content' | 'blank' | 'other'
  primaryLanguage?: string
  secondaryLanguage?: string
  primaryScript?: string
  secondaryScript?: string
  readingDirection?: 'left-to-right' | 'right-to-left' | 'top-to-bottom' | 'bottom-to-top'
  textLineOrder?: 'top-to-bottom' | 'bottom-to-top' | 'left-to-right' | 'right-to-left'
  conf?: number

  constructor(params: {
    imageFilename: string
    imageWidth: number
    imageHeight: number
    imageXResolution?: number
    imageYResolution?: number
    imageResolutionUnit?: 'PPI' | 'PPCM' | 'other'
    border?: Border
    printSpace?: PrintSpace
    readingOrder?: import('./reading-order').ReadingOrder
    alternativeImages?: AlternativeImage[]
    labels?: Labels[]
    userDefined?: UserDefined
    textStyle?: TextStyleAttributes
    layers?: Layer[]
    relations?: Relation[]
    regions?: import('./region').Region[]
    custom?: string
    orientation?: number
    type?: 'front-cover' | 'back-cover' | 'title' | 'table-of-contents' | 'index' | 'content' | 'blank' | 'other'
    primaryLanguage?: string
    secondaryLanguage?: string
    primaryScript?: string
    secondaryScript?: string
    readingDirection?: 'left-to-right' | 'right-to-left' | 'top-to-bottom' | 'bottom-to-top'
    textLineOrder?: 'top-to-bottom' | 'bottom-to-top' | 'left-to-right' | 'right-to-left'
    conf?: number
  }) {
    this.imageFilename = params.imageFilename
    this.imageWidth = params.imageWidth
    this.imageHeight = params.imageHeight
    this.imageXResolution = params.imageXResolution
    this.imageYResolution = params.imageYResolution
    this.imageResolutionUnit = params.imageResolutionUnit
    this.border = params.border
    this.printSpace = params.printSpace
    this.readingOrder = params.readingOrder
    this.alternativeImages = params.alternativeImages
    this.labels = params.labels
    this.userDefined = params.userDefined
    this.textStyle = params.textStyle
    this.layers = params.layers
    this.relations = params.relations
    this.regions = params.regions ?? []
    this.custom = params.custom
    this.orientation = params.orientation
    this.type = params.type
    this.primaryLanguage = params.primaryLanguage
    this.secondaryLanguage = params.secondaryLanguage
    this.primaryScript = params.primaryScript
    this.secondaryScript = params.secondaryScript
    this.readingDirection = params.readingDirection
    this.textLineOrder = params.textLineOrder
    this.conf = params.conf
  }

  getDimensions(): { width: number, height: number } {
    return { width: this.imageWidth, height: this.imageHeight }
  }
}

export interface Border {
  coords: import('./geometry').Polygon
}

export interface PrintSpace {
  coords: import('./geometry').Polygon
}
