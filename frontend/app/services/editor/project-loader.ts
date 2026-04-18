import type { PageData, ImageVariant, XmlFile, ResolvedTag, PageIndexingStatus } from '@/stores/editor/types'

export interface PageResponse {
  id: string
  name: string
  thumbnail?: string
  thumbnailUrl?: string
  tags?: string[]
  resolvedTags?: ResolvedTag[] | null
  locked?: boolean
  lockedReason?: string | null
  imageCount?: number
  xmlFileCount?: number
  indexingStatus?: PageIndexingStatus
}

function projectAnnotationContext(projectId: string, pageId: string) {
  return {
    mode: 'PROJECT' as const,
    basePath: `/api/projects/${projectId}/pages/${pageId}/annotations`,
    createAllowed: true
  }
}

interface ImageResponse {
  id: string
  fileName: string
  filePath: string
  variant: string
  baseName: string
  thumbnailPath?: string
}

interface XmlResponse {
  id: string
  fileName: string
  schema: string
  schemaVersion?: string
  variant?: string
}

/**
 * Create skeleton PageData[] from PageResponse[] with no API calls.
 * Skeleton pages have empty imageVariants and xmlFiles arrays.
 */
export function createSkeletonPageData(
  pages: PageResponse[],
  options?: { projectId?: string, projectName?: string }
): PageData[] {
  return pages.map(page => ({
    id: page.id,
    projectId: options?.projectId ?? '',
    projectName: options?.projectName,
    label: page.name,
    thumbnail: page.thumbnailUrl ?? undefined,
    imageVariants: [],
    xmlFiles: [],
    tags: page.tags ?? [],
    resolvedTags: page.resolvedTags ?? null,
    locked: page.locked ?? false,
    lockedReason: page.lockedReason ?? null,
    imageCount: page.imageCount ?? 0,
    xmlFileCount: page.xmlFileCount ?? 0,
    indexingStatus: page.indexingStatus ?? 'NOT_APPLICABLE',
    annotationContext: options?.projectId ? projectAnnotationContext(options.projectId, page.id) : undefined
  }))
}

/**
 * Load full data (images + XML metadata) for a single page.
 * Returns the enriched PageData with imageVariants and xmlFiles populated.
 */
export async function loadSinglePageData(projectId: string, page: PageResponse): Promise<PageData> {
  try {
    const [images, xmlFiles] = await Promise.all([
      $fetch<ImageResponse[]>(`/api/projects/${projectId}/pages/${page.id}/images`),
      $fetch<XmlResponse[]>(`/api/projects/${projectId}/pages/${page.id}/xml`).catch((err) => {
        console.error(`[project-loader] Failed to fetch XML files for page ${page.id}:`, err)
        return [] as XmlResponse[]
      })
    ])

    const firstImageWithThumbnail = images.find(img => img.thumbnailPath)
    const fallbackImage = images[0]
    const thumbnailSource = firstImageWithThumbnail ?? fallbackImage
    const thumbnailUrl = thumbnailSource
      ? `/api/projects/${projectId}/pages/images/${thumbnailSource.id}/thumbnail`
      : undefined

    const imageVariants: ImageVariant[] = images.map(img => ({
      id: img.id,
      url: `/api/projects/${projectId}/pages/images/${img.id}/blob`,
      fileName: img.fileName,
      type: img.variant || undefined,
      label: img.variant || img.fileName
    }))

    const mappedXmlFiles: XmlFile[] = xmlFiles.map(xml => ({
      id: xml.id,
      fileName: xml.fileName,
      schema: xml.schema as XmlFile['schema'],
      schemaVersion: xml.schemaVersion,
      variant: xml.variant
    }))

    return {
      id: page.id,
      projectId,
      label: page.name,
      thumbnail: thumbnailUrl,
      imageVariants,
      xmlFiles: mappedXmlFiles,
      tags: page.tags ?? [],
      resolvedTags: page.resolvedTags ?? null,
      locked: page.locked ?? false,
      lockedReason: page.lockedReason ?? null,
      indexingStatus: page.indexingStatus ?? 'NOT_APPLICABLE',
      annotationContext: projectAnnotationContext(projectId, page.id)
    }
  } catch (error) {
    console.error(`Failed to load data for page ${page.id}:`, error)
    return {
      id: page.id,
      projectId,
      label: page.name,
      thumbnail: undefined,
      imageVariants: [],
      xmlFiles: [],
      tags: page.tags ?? [],
      resolvedTags: page.resolvedTags ?? null,
      locked: page.locked ?? false,
      lockedReason: page.lockedReason ?? null,
      indexingStatus: page.indexingStatus ?? 'NOT_APPLICABLE',
      annotationContext: projectAnnotationContext(projectId, page.id)
    }
  }
}

export async function loadProjectPages(
  projectId: string,
  pages: PageResponse[],
  selectedPageIds?: string[]
): Promise<PageData[]> {
  const pagesToLoad = selectedPageIds && selectedPageIds.length > 0
    ? pages.filter(p => selectedPageIds.includes(p.id))
    : pages

  const pageDataPromises = pagesToLoad.map(page => loadSinglePageData(projectId, page))

  return await Promise.all(pageDataPromises)
}
