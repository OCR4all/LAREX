/**
 * Service facade for converting between backend PageDto and frontend PcGts formats.
 *
 * Backend sends world coordinates as [x, y] arrays in the range [-1, 1].
 * Frontend uses [number, number][] for polygon points.
 */

import { Page, PcGts } from '@/models/editor'
import type { PageDto } from '@/types/page-dto'
import {
  convertAlternativeImagesFromDto,
  convertAlternativeImagesToDto,
  convertLabelsFromDto,
  convertLabelsToDto,
  convertLayersFromDto,
  convertLayersToDto,
  convertPolygonFromDto,
  convertPolygonToDto,
  convertTextStyleFromDto,
  convertTextStyleToDto,
  convertUserDefinedFromDto,
  convertUserDefinedToDto,
  undefinedIfBlank
} from './page-conversion/shared'
import { convertMetadataFromDto, convertMetadataToDto } from './page-conversion/metadata-converter'
import { convertReadingOrderFromDto, convertReadingOrderToDto } from './page-conversion/reading-order-converter'
import { convertRegionFromDto, convertRegionToDto } from './page-conversion/region-converter'
import { convertRelationsFromDto, convertRelationsToDto } from './page-conversion/relations-converter'

export type { PageDto } from '@/types/page-dto'

export function convertPageDtoToPcGts(dto: PageDto): PcGts {
  const metadata = convertMetadataFromDto(dto.metadata)

  const page = new Page({
    imageFilename: dto.imageFilename ?? '',
    imageWidth: dto.imageWidth,
    imageHeight: dto.imageHeight,
    imageXResolution: dto.imageXResolution,
    imageYResolution: dto.imageYResolution,
    imageResolutionUnit: undefinedIfBlank(dto.imageResolutionUnit) as Page['imageResolutionUnit'],
    border: dto.border ? { coords: convertPolygonFromDto(dto.border)! } : undefined,
    printSpace: dto.printSpace ? { coords: convertPolygonFromDto(dto.printSpace)! } : undefined,
    readingOrder: dto.readingOrder ? convertReadingOrderFromDto(dto.readingOrder) : undefined,
    alternativeImages: convertAlternativeImagesFromDto(dto.alternativeImages),
    labels: convertLabelsFromDto(dto.labels),
    userDefined: convertUserDefinedFromDto(dto.userDefined),
    textStyle: convertTextStyleFromDto(dto.textStyle),
    layers: convertLayersFromDto(dto.layers),
    relations: convertRelationsFromDto(dto.relations),
    regions: dto.regions?.map(convertRegionFromDto) ?? [],
    custom: undefinedIfBlank(dto.custom),
    orientation: dto.orientation,
    type: undefinedIfBlank(dto.type) as Page['type'],
    primaryLanguage: undefinedIfBlank(dto.primaryLanguage),
    secondaryLanguage: undefinedIfBlank(dto.secondaryLanguage),
    primaryScript: undefinedIfBlank(dto.primaryScript),
    secondaryScript: undefinedIfBlank(dto.secondaryScript),
    readingDirection: undefinedIfBlank(dto.readingDirection) as Page['readingDirection'],
    textLineOrder: undefinedIfBlank(dto.textLineOrder) as Page['textLineOrder'],
    conf: dto.confidence
  })

  return new PcGts(metadata, page, dto.pcGtsId)
}

export function convertPcGtsToPageDto(pcGts: PcGts): PageDto {
  return {
    imageFilename: pcGts.page.imageFilename,
    imageWidth: pcGts.page.imageWidth,
    imageHeight: pcGts.page.imageHeight,
    imageXResolution: pcGts.page.imageXResolution,
    imageYResolution: pcGts.page.imageYResolution,
    imageResolutionUnit: undefinedIfBlank(pcGts.page.imageResolutionUnit),
    metadata: convertMetadataToDto(pcGts.metadata),
    pcGtsId: pcGts.pcGtsId,
    type: undefinedIfBlank(pcGts.page.type),
    custom: undefinedIfBlank(pcGts.page.custom),
    orientation: pcGts.page.orientation,
    primaryLanguage: undefinedIfBlank(pcGts.page.primaryLanguage),
    secondaryLanguage: undefinedIfBlank(pcGts.page.secondaryLanguage),
    primaryScript: undefinedIfBlank(pcGts.page.primaryScript),
    secondaryScript: undefinedIfBlank(pcGts.page.secondaryScript),
    readingDirection: undefinedIfBlank(pcGts.page.readingDirection),
    textLineOrder: undefinedIfBlank(pcGts.page.textLineOrder),
    confidence: pcGts.page.conf,
    border: pcGts.page.border ? convertPolygonToDto(pcGts.page.border.coords) : undefined,
    printSpace: pcGts.page.printSpace ? convertPolygonToDto(pcGts.page.printSpace.coords) : undefined,
    readingOrder: convertReadingOrderToDto(pcGts.page.readingOrder),
    alternativeImages: convertAlternativeImagesToDto(pcGts.page.alternativeImages),
    labels: convertLabelsToDto(pcGts.page.labels),
    userDefined: convertUserDefinedToDto(pcGts.page.userDefined),
    textStyle: convertTextStyleToDto(pcGts.page.textStyle),
    layers: convertLayersToDto(pcGts.page.layers),
    relations: convertRelationsToDto(pcGts.page.relations),
    regions: pcGts.page.regions?.map(convertRegionToDto)
  }
}
