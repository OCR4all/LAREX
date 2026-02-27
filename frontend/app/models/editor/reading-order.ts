export type ReadingOrderNode = ReadingOrderGroup | RegionRef

/** PAGE XML 2019: ReadingOrderType (contains exactly one group) */
export interface ReadingOrder {
  root: ReadingOrderGroup
}

export type ReadingOrderGroup = OrderedGroup | UnorderedGroup | OrderedGroupIndexed | UnorderedGroupIndexed

export interface BaseGroup {
  id: string
  /** Optional link of a group to a region (RegionType/@id) */
  regionRef?: string
  elements: ReadingOrderNode[]
}

export interface OrderedGroup extends BaseGroup {
  kind: 'OrderedGroup'
}

export interface UnorderedGroup extends BaseGroup {
  kind: 'UnorderedGroup'
}

export interface OrderedGroupIndexed extends BaseGroup {
  kind: 'OrderedGroupIndexed'
}

export interface UnorderedGroupIndexed extends BaseGroup {
  kind: 'UnorderedGroupIndexed'
}

export interface RegionRef {
  kind: 'RegionRef' | 'RegionRefIndexed'
  /** xsd:ID for the reading-order node */
  id: string
  /** IDREF to a RegionType/@id */
  regionRef: string
  index?: number
}
