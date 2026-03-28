export interface ClipboardCopyOptions {
  successTitle: string
  failureTitle?: string
  failureDescription?: string
}

export async function copyTextToClipboard(text: string, options: ClipboardCopyOptions): Promise<boolean> {
  const toast = useToast()

  if (!import.meta.client || !navigator.clipboard) {
    toast.add({
      title: options.failureTitle || 'Clipboard unavailable',
      description: options.failureDescription || 'Your browser blocked clipboard access.',
      color: 'warning',
      icon: 'i-lucide-clipboard-x'
    })
    return false
  }

  try {
    await navigator.clipboard.writeText(text)
    toast.add({
      title: options.successTitle,
      color: 'success',
      icon: 'i-lucide-check'
    })
    return true
  } catch {
    toast.add({
      title: options.failureTitle || 'Copy failed',
      description: options.failureDescription || 'Your browser blocked clipboard access.',
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
    return false
  }
}
