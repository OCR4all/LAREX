type EditorPageLinkTarget = {
  projectId: string
  pageId: string
  variantId?: string | null
}

export function useEditorPageLink() {
  const router = useRouter()

  function getPageLink({ projectId, pageId, variantId }: EditorPageLinkTarget): string {
    const query: Record<string, string> = { projectId, pageId }
    if (variantId) query.variantId = variantId

    const href = router.resolve({ path: '/editor', query }).href
    if (typeof window === 'undefined') return href
    return new URL(href, window.location.origin).toString()
  }

  return { getPageLink }
}
