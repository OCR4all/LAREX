import { Metadata } from '@/models/editor'
import type { MetadataItem } from '@/models/editor/document'
import type { MetadataDto, MetadataItemDto } from '@/types/page-dto'
import {
  convertLabelsFromDto,
  convertLabelsToDto,
  convertUserDefinedFromDto,
  convertUserDefinedToDto,
  freeText,
  undefinedIfBlank
} from './shared'

export function convertMetadataFromDto(dto?: MetadataDto): Metadata {
  return new Metadata({
    creator: freeText(dto?.creator),
    created: dto?.created,
    lastChange: dto?.lastChange,
    comments: freeText(dto?.comments),
    externalRef: freeText(dto?.externalRef),
    userDefined: convertUserDefinedFromDto(dto?.userDefined),
    items: convertMetadataItemsFromDto(dto?.items)
  })
}

export function convertMetadataToDto(metadata: Metadata): MetadataDto | undefined {
  const dto: MetadataDto = {
    creator: freeText(metadata.creator),
    created: undefinedIfBlank(metadata.created),
    lastChange: undefinedIfBlank(metadata.lastChange),
    comments: freeText(metadata.comments),
    externalRef: freeText(metadata.externalRef),
    userDefined: convertUserDefinedToDto(metadata.userDefined),
    items: convertMetadataItemsToDto(metadata.items)
  }
  return Object.values(dto).some(value => value !== undefined) ? dto : undefined
}

function convertMetadataItemsFromDto(items?: MetadataItemDto[]): MetadataItem[] | undefined {
  if (!items?.length) return undefined
  return items.map(item => ({
    type: item.type as MetadataItem['type'],
    name: freeText(item.name),
    value: item.value ?? '',
    date: undefinedIfBlank(item.date),
    labels: convertLabelsFromDto(item.labels)
  }))
}

function convertMetadataItemsToDto(items?: MetadataItem[]): MetadataItemDto[] | undefined {
  if (!items?.length) return undefined
  return items.map(item => ({
    type: item.type,
    name: freeText(item.name),
    value: item.value,
    date: undefinedIfBlank(item.date),
    labels: convertLabelsToDto(item.labels)
  }))
}
