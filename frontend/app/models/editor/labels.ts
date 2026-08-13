/** Flexible labeling system */
export class LabelSet {
  id: string
  name: string
  labels: LabelDefinition[]
  description?: string
  defaultLabelId?: string | null

  constructor(
    id: string,
    name: string,
    labels: LabelDefinition[],
    description?: string,
    defaultLabelId?: string | null
  ) {
    this.id = id
    this.name = name
    this.labels = labels
    this.description = description
    this.defaultLabelId = defaultLabelId
  }

  getLabel(id: string): LabelDefinition | undefined {
    return this.labels.find(l => l.id === id)
  }

  getLabelsByType(type: string): LabelDefinition[] {
    return this.labels.filter(l => l.pageXmlRegionType === type)
  }

  getDefaultLabel(): LabelDefinition | undefined {
    if (this.defaultLabelId) {
      const configured = this.getLabel(this.defaultLabelId)
      if (configured) return configured
    }
    return this.labels[0]
  }

  addLabel(label: LabelDefinition): LabelSet {
    return new LabelSet(
      this.id,
      this.name,
      [...this.labels, label],
      this.description,
      this.defaultLabelId
    )
  }
}

export type LabelScope = 'region'

export interface PageXmlMapping {
  regionType?: string | null
  textType?: string | null
  customSubType?: string | null
  customKey: string
  customData?: string | null
}

export interface LabelMapping {
  pageXml: PageXmlMapping
}

export class LabelDefinition {
  id: string
  name: string
  scope: LabelScope
  color?: string
  description?: string
  hasText: boolean
  isContainer: boolean
  group?: string | null
  mapping: LabelMapping

  constructor(
    id: string,
    name: string,
    scope: LabelScope,
    color: string,
    description: string,
    hasText: boolean,
    isContainer: boolean,
    group: string | null,
    mapping: LabelMapping
  ) {
    this.id = id
    this.name = name
    this.scope = scope
    this.color = color
    this.description = description
    this.hasText = hasText
    this.isContainer = isContainer
    this.group = group
    this.mapping = mapping
  }

  get pageXmlRegionType(): string | null {
    return this.mapping?.pageXml?.regionType ?? null
  }

  get pageXmlTextType(): string | null {
    return this.mapping?.pageXml?.textType ?? null
  }

  get pageXmlCustomSubType(): string | null {
    return this.mapping?.pageXml?.customSubType ?? null
  }
}
