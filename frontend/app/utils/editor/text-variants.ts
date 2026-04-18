export type EditableTextVariant = {
  unicode: string
  plainText?: string
  confidence?: number
  index?: number
  dataType?: string
  dataTypeDetails?: string
  comments?: string
}

function normalizeGtIndex(gtIndex: number): number {
  return Number.isFinite(gtIndex) && gtIndex >= 0 ? Math.trunc(gtIndex) : 0
}

function toSortableIndex(index: number | undefined): number {
  return typeof index === 'number' && Number.isFinite(index) ? index : -1
}

export function sortEditableTextVariantsByIndex(a: EditableTextVariant, b: EditableTextVariant): number {
  return toSortableIndex(a.index) - toSortableIndex(b.index)
}

export function normalizeEditableTextVariants(variants: EditableTextVariant[] | undefined): EditableTextVariant[] {
  const current = (variants ?? []).map(variant => ({ ...variant }))
  current.sort(sortEditableTextVariantsByIndex)
  return current
}

export function ensureGtVariantAtIndex(
  variants: EditableTextVariant[] | undefined,
  gtIndex: number
): {
    variants: EditableTextVariant[]
    gtIndex: number
    gtPos: number
    created: boolean
  } {
  const normalizedGtIndex = normalizeGtIndex(gtIndex)
  const nextVariants = normalizeEditableTextVariants(variants)
  let gtPos = nextVariants.findIndex(variant => variant.index === normalizedGtIndex)
  let created = false

  if (gtPos < 0) {
    nextVariants.push({
      unicode: '',
      index: normalizedGtIndex
    })
    nextVariants.sort(sortEditableTextVariantsByIndex)
    gtPos = nextVariants.findIndex(variant => variant.index === normalizedGtIndex)
    created = true
  }

  return {
    variants: nextVariants,
    gtIndex: normalizedGtIndex,
    gtPos,
    created
  }
}

export function setGtVariantUnicode(
  variants: EditableTextVariant[] | undefined,
  gtIndex: number,
  unicode: string
): {
    variants: EditableTextVariant[]
    gtIndex: number
    gtPos: number
    created: boolean
    changed: boolean
  } {
  const ensured = ensureGtVariantAtIndex(variants, gtIndex)
  const currentGt = ensured.variants[ensured.gtPos]
  if (!currentGt) {
    return {
      ...ensured,
      changed: false
    }
  }

  if (currentGt.unicode === unicode) {
    return {
      ...ensured,
      changed: false
    }
  }

  const nextVariants = ensured.variants.map((variant, index) => {
    if (index !== ensured.gtPos) return variant
    return {
      ...variant,
      unicode
    }
  })

  return {
    variants: nextVariants,
    gtIndex: ensured.gtIndex,
    gtPos: ensured.gtPos,
    created: ensured.created,
    changed: true
  }
}

