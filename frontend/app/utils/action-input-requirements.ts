import type { ImageType } from '@/types/image'
import type { ActionInputLevel, ActionInputRequirement, ActionTarget } from '@/types/action'

export function actionInputLevelForTarget(
  requirement: ActionInputRequirement | null | undefined,
  target: ActionTarget,
  legacyAccepted = false
): ActionInputLevel {
  if (!requirement) return legacyAccepted ? 'OPTIONAL' : 'NONE'
  return requirement.requiredForTargets?.includes(target) ? 'REQUIRED' : requirement.level
}

export function actionImageSelectionValues(
  value: unknown,
  previous: string[] = [],
  required = false
): string[] {
  const selected = typeof value === 'string'
    ? (value ? [value] : [])
    : Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
  return required && selected.length === 0 ? [...previous] : selected
}

export function compatibleActionImages<T extends { imageType?: ImageType | null }>(
  images: T[], requirement: ActionInputRequirement | null | undefined
): T[] {
  const accepted = requirement?.imageTypes
  // Missing metadata is pending, not incompatible. The server resolves it before execution.
  return accepted?.length ? images.filter(image => image.imageType == null || accepted.some(type => type === image.imageType)) : images
}

export function selectableActionImages<T extends { imageType?: ImageType | null, variant?: string | null, fileName?: string, id?: string }>(
  images: T[], requirement: ActionInputRequirement | null | undefined, required: boolean
): T[] {
  const compatible = compatibleActionImages(images, requirement)
  // Pending images can reach server review, but cannot be offered as a confirmed type match.
  return required && requirement?.imageTypes?.length
    ? compatible.filter(image => image.imageType != null).sort((left, right) =>
        (left.variant ?? '').localeCompare(right.variant ?? '', undefined, { sensitivity: 'accent' })
        || (left.fileName ?? '').localeCompare(right.fileName ?? '', undefined, { sensitivity: 'accent' })
        || (left.id ?? '').localeCompare(right.id ?? ''))
    : compatible
}

export function defaultActionImageVariants(
  groups: Array<Array<{ variant?: string | null, imageType?: ImageType | null }>>, multiple: boolean
): string[] {
  if (!groups.length || groups.some(images => !images.length)) return []
  const variants = actionImageVariantOptions(groups.flat()).map(option => option.value)
  return multiple ? variants : variants.filter(variant => groups.every(images => images.filter(image => image.variant?.trim() === variant).length === 1)).slice(0, 1)
}

export function actionImageVariantOptions(images: Array<{ variant?: string | null, imageType?: ImageType | null }>) {
  const typesByVariant = new Map<string, Set<string>>()
  for (const image of images) {
    const variant = image.variant?.trim()
    if (!variant) continue
    const types = typesByVariant.get(variant) ?? new Set<string>()
    if (image.imageType) types.add(image.imageType.toLowerCase())
    else if (image.imageType === null) types.add('type pending')
    typesByVariant.set(variant, types)
  }
  return [...typesByVariant.entries()].sort(([left], [right]) => left.localeCompare(right))
    .map(([value, types]) => ({ label: types.size ? `${value} · ${[...types].sort().join(', ')}` : value, value }))
}
