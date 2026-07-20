import type { TextModeSubmode } from '@/stores/editor/types'

export function resolveTextModeSubmodeFromQuery(textView: string | null | undefined): TextModeSubmode {
  if (textView === 'full') return 'full'
  if (textView === 'expert' || textView === 'textline' || textView === 'region') return 'expert'
  return 'visual'
}
