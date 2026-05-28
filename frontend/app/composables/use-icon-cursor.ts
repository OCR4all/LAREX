import type { ComputedRef, Ref } from 'vue'
import { useAppConfig } from 'nuxt/app'

interface IconCursorOptions {
  fallback?: string
  size?: number
  color?: string
  outlineColor?: string
  outlineWidth?: number
  hotspotX?: number
  hotspotY?: number
}

interface IconCursorPreset extends IconCursorOptions {
  iconName: string
}

interface IconifyIconData {
  body: string
  width?: number
  height?: number
}

interface IconifyCollectionResponse {
  icons?: Record<string, IconifyIconData>
}

interface ParsedIconName {
  collection: string
  icon: string
}

const DEFAULT_FALLBACK_CURSOR = 'crosshair'
const DEFAULT_CURSOR_SIZE = 24
const DEFAULT_OUTLINE_COLOR = '#000000'
const DEFAULT_OUTLINE_WIDTH = 1.2
const iconCursorCache = new Map<string, Promise<string | null>>()

const ICON_CURSOR_PRESETS = {
  actionWand: {
    iconName: 'i-lucide-wand-sparkles',
    fallback: 'crosshair',
    color: '#ffffff',
    hotspotX: 4,
    hotspotY: 4,
    size: 24
  }
} as const satisfies Record<string, IconCursorPreset>

export type IconCursorPresetName = keyof typeof ICON_CURSOR_PRESETS

function normalizeCursorOptionNumber(value: number | undefined, fallback: number): number {
  return Number.isFinite(value) ? Number(value) : fallback
}

export function parseIconifyIconName(iconName: string): ParsedIconName | null {
  if (!iconName) return null

  if (iconName.startsWith('i-')) {
    const segments = iconName.slice(2).split('-')
    if (segments.length < 2) return null

    const [collection, ...iconParts] = segments
    const icon = iconParts.join('-')
    return collection && icon ? { collection, icon } : null
  }

  if (iconName.includes(':')) {
    const [collection, icon] = iconName.split(':', 2)
    return collection && icon ? { collection, icon } : null
  }

  return null
}

function buildCursorOutlineFilter(outlineColor: string, outlineWidth: number): string {
  if (outlineWidth <= 0) return ''

  return `<defs><filter id="cursor-outline" x="-50%" y="-50%" width="200%" height="200%"><feMorphology in="SourceAlpha" operator="dilate" radius="${outlineWidth}" result="outline"/><feFlood flood-color="${outlineColor}" result="outlineColor"/><feComposite in="outlineColor" in2="outline" operator="in" result="outlineFill"/><feMerge><feMergeNode in="outlineFill"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>`
}

function applyCursorColor(iconBody: string, color: string): string {
  return iconBody.replaceAll('currentColor', color)
}

export function buildIconCursorSvg(iconData: IconifyIconData, color: string, size: number, options: IconCursorOptions = {}): string {
  const width = normalizeCursorOptionNumber(iconData.width, size)
  const height = normalizeCursorOptionNumber(iconData.height, size)
  const outlineColor = options.outlineColor || DEFAULT_OUTLINE_COLOR
  const outlineWidth = normalizeCursorOptionNumber(options.outlineWidth, DEFAULT_OUTLINE_WIDTH)
  const filter = buildCursorOutlineFilter(outlineColor, outlineWidth)
  const coloredBody = applyCursorColor(iconData.body, color)
  const body = outlineWidth > 0
    ? `<g filter="url(#cursor-outline)">${coloredBody}</g>`
    : coloredBody

  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${width} ${height}" style="color:${color}">${filter}${body}</svg>`
}

export function buildIconCursorValue(svg: string, options: IconCursorOptions = {}): string {
  const fallback = options.fallback || DEFAULT_FALLBACK_CURSOR
  const hotspotX = normalizeCursorOptionNumber(options.hotspotX, 0)
  const hotspotY = normalizeCursorOptionNumber(options.hotspotY, 0)
  const encodedSvg = encodeURIComponent(svg)

  return `url("data:image/svg+xml,${encodedSvg}") ${hotspotX} ${hotspotY}, ${fallback}`
}

export function getIconCursorPreset(presetName: IconCursorPresetName): IconCursorPreset {
  return ICON_CURSOR_PRESETS[presetName]
}

async function fetchIconCursorValue(iconName: string, endpoint: string, options: IconCursorOptions): Promise<string | null> {
  const parsedIconName = parseIconifyIconName(iconName)
  if (!parsedIconName) return null

  const iconResponse = await $fetch<IconifyCollectionResponse>(`${endpoint}/${parsedIconName.collection}.json`, {
    query: {
      icons: parsedIconName.icon
    }
  })

  const iconData = iconResponse.icons?.[parsedIconName.icon]
  if (!iconData) return null

  const size = normalizeCursorOptionNumber(options.size, DEFAULT_CURSOR_SIZE)
  const color = options.color || '#ffffff'
  const svg = buildIconCursorSvg(iconData, color, size, options)
  return buildIconCursorValue(svg, options)
}

export function useIconCursor(iconName: string | Ref<string | null | undefined> | ComputedRef<string | null | undefined>, options: IconCursorOptions = {}) {
  const appConfig = useAppConfig()
  const cursor = ref(options.fallback || DEFAULT_FALLBACK_CURSOR)

  async function updateCursor(): Promise<void> {
    const resolvedIconName = toValue(iconName)
    const fallback = options.fallback || DEFAULT_FALLBACK_CURSOR

    if (!import.meta.client || !resolvedIconName) {
      cursor.value = fallback
      return
    }

    const endpoint = appConfig.icon?.localApiEndpoint || '/api/_nuxt_icon'
    const cacheKey = JSON.stringify({ icon: resolvedIconName, endpoint, options })
    let pendingCursor = iconCursorCache.get(cacheKey)

    if (!pendingCursor) {
      pendingCursor = fetchIconCursorValue(resolvedIconName, endpoint, options)
      iconCursorCache.set(cacheKey, pendingCursor)
    }

    try {
      cursor.value = await pendingCursor ?? fallback
    } catch {
      iconCursorCache.delete(cacheKey)
      cursor.value = fallback
    }
  }

  watch(() => [toValue(iconName), options.color, options.fallback, options.hotspotX, options.hotspotY, options.outlineColor, options.outlineWidth, options.size], () => {
    void updateCursor()
  }, { immediate: true })

  return {
    cursor,
    refresh: updateCursor
  }
}

export function useIconCursorPreset(presetName: IconCursorPresetName) {
  const { iconName, ...options } = getIconCursorPreset(presetName)
  return useIconCursor(iconName, options)
}
