import { buildApiErrorClipboardPayload, extractApiErrorDetails } from '@/utils/api-error'
import { copyTextToClipboard } from '@/utils/clipboard'

export interface ApiErrorToastOptions {
  title: string
  error: unknown
  fallback: string
}

export function showApiErrorToast(options: ApiErrorToastOptions) {
  const toast = useToast()
  const errorDetails = extractApiErrorDetails(options.error, options.fallback)
  const pageUrl = import.meta.client ? window.location.href : undefined
  const copyPayload = buildApiErrorClipboardPayload(errorDetails, pageUrl)

  return toast.add({
    title: options.title,
    description: errorDetails.message,
    color: 'error',
    icon: 'i-lucide-alert-circle',
    actions: errorDetails.errorId
      ? [{
          label: 'Copy error',
          color: 'neutral',
          variant: 'outline',
          onClick: async () => {
            await copyTextToClipboard(copyPayload, {
              successTitle: 'Error details copied',
              failureTitle: 'Copy failed',
              failureDescription: 'Unable to copy the error details to the clipboard.'
            })
          }
        }]
      : undefined
  })
}
