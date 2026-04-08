export type TextVariantRenderIdentity = {
  index?: number
  pos: number
}

export function getTextContentVariantRenderKey(variant: TextVariantRenderIdentity): string {
  if (typeof variant.index === 'number' && Number.isFinite(variant.index)) {
    return `index:${variant.index}`
  }

  return `unindexed:${variant.pos}`
}
