import type { ReadingOrder, ReadingOrderGroup, ReadingOrderNode } from '@/models/editor/reading-order'
import type {
  GroupDto,
  GroupMemberDto,
  ReadingOrderDto
} from '@/types/page-dto'
import {
  convertLabelsFromDto,
  convertLabelsToDto,
  convertUserDefinedFromDto,
  convertUserDefinedToDto,
  undefinedIfBlank
} from './shared'

export function convertReadingOrderFromDto(dto: ReadingOrderDto): ReadingOrder | undefined {
  if (!dto.root) return undefined

  const convertGroupDto = (groupDto: GroupDto): ReadingOrderGroup => {
    const elements: ReadingOrderNode[] = []
    let hasIndex = groupDto.index !== undefined
    if (groupDto.members) {
      for (const member of groupDto.members) {
        if (member.type === 'regionRef') {
          const refDto = member
          if (refDto.index !== undefined) hasIndex = true
          elements.push({
            kind: refDto.index !== undefined ? 'RegionRefIndexed' : 'RegionRef',
            id: refDto.id ?? `ref-${refDto.regionRef ?? 'unknown'}`,
            regionRef: refDto.regionRef ?? '',
            index: refDto.index
          })
        } else if (member.type === 'nestedGroup') {
          const nested = member.group
          const converted = convertGroupDto(nested)
          if ('index' in converted && converted.index !== undefined) hasIndex = true
          elements.push(converted)
        }
      }
    }
    // The editor uses array order for traversal and drag-and-drop edits. Resolve
    // PAGE indices on import so every consumer sees the same effective order.
    if (groupDto.ordered) {
      elements.sort((a, b) => {
        const ai = typeof a.index === 'number' && Number.isFinite(a.index) && a.index >= 0 ? a.index : Infinity
        const bi = typeof b.index === 'number' && Number.isFinite(b.index) && b.index >= 0 ? b.index : Infinity
        return ai === bi ? 0 : ai - bi
      })
    }
    const kind = groupDto.ordered
      ? hasIndex ? 'OrderedGroupIndexed' : 'OrderedGroup'
      : hasIndex ? 'UnorderedGroupIndexed' : 'UnorderedGroup'
    return {
      kind,
      id: groupDto.id ?? 'group-root',
      index: groupDto.index,
      regionRef: groupDto.regionRef,
      caption: undefinedIfBlank(groupDto.caption),
      groupType: undefinedIfBlank(groupDto.groupType),
      continuation: groupDto.continuation,
      custom: undefinedIfBlank(groupDto.custom),
      comments: undefinedIfBlank(groupDto.comments),
      userDefined: convertUserDefinedFromDto(groupDto.userDefined),
      labels: convertLabelsFromDto(groupDto.labels),
      elements
    } as ReadingOrderGroup
  }

  return {
    root: convertGroupDto(dto.root),
    conf: dto.confidence
  }
}

export function convertReadingOrderToDto(ro?: ReadingOrder): ReadingOrderDto | undefined {
  if (!ro?.root) return undefined

  const convertGroup = (group: ReadingOrderGroup): GroupDto => {
    const ordered = group.kind === 'OrderedGroup' || group.kind === 'OrderedGroupIndexed'
    const members: GroupMemberDto[] = []
    for (const element of group.elements) {
      // Edits change array order; refresh indices so reload/collaboration keeps it.
      const index = ordered ? members.length : undefined
      if ('elements' in element) {
        members.push({
          type: 'nestedGroup',
          group: { ...convertGroup(element), index }
        })
      } else {
        members.push({
          type: 'regionRef',
          id: element.id,
          regionRef: element.regionRef,
          index
        })
      }
    }
    return {
      id: group.id,
      ordered,
      index: group.index,
      caption: undefinedIfBlank(group.caption),
      groupType: undefinedIfBlank(group.groupType),
      regionRef: group.regionRef,
      members,
      continuation: group.continuation,
      userDefined: convertUserDefinedToDto(group.userDefined),
      labels: convertLabelsToDto(group.labels),
      custom: undefinedIfBlank(group.custom),
      comments: undefinedIfBlank(group.comments)
    }
  }

  return {
    root: convertGroup(ro.root),
    confidence: ro.conf
  }
}
