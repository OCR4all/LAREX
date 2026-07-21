export function normalizeFullTextComment(comment: string | null | undefined): string {
  return comment?.trim() ?? ''
}

export function serializeFullTextComment(comment: string | null | undefined): string | undefined {
  const normalized = normalizeFullTextComment(comment)
  return normalized.length > 0 ? normalized : undefined
}
