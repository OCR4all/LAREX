import { useEditorPreferences } from '@/composables/use-editor-preferences'
import {
  createShortcutPreferences,
  getResolvedShortcutDefinitions,
  getShortcutKbds,
  normalizeShortcutPreferences,
  type ShortcutCommandId,
  type ShortcutPreferences
} from './shortcut-registry'

export function useShortcutBindings() {
  const editorPreferences = useEditorPreferences()

  const shortcutPreferences = computed<ShortcutPreferences>(() =>
    normalizeShortcutPreferences(editorPreferences.preferences.value.shortcutBindings)
  )

  const resolvedShortcutDefinitions = computed(() =>
    getResolvedShortcutDefinitions(shortcutPreferences.value)
  )

  function saveShortcutPreferences(preferences: ShortcutPreferences) {
    editorPreferences.updatePreference('shortcutBindings', createShortcutPreferences(preferences.bindings))
  }

  function getTooltipProps(commandId: ShortcutCommandId): { text: string, kbds: string[] } {
    const definition = resolvedShortcutDefinitions.value[commandId]
    const primaryBinding = definition.bindings[0]
    return {
      text: definition.description,
      kbds: primaryBinding ? getShortcutKbds(primaryBinding) : []
    }
  }

  return {
    shortcutPreferences,
    resolvedShortcutDefinitions,
    saveShortcutPreferences,
    getTooltipProps
  }
}

export function getTooltipProps(commandId: ShortcutCommandId): { text: string, kbds: string[] } {
  return useShortcutBindings().getTooltipProps(commandId)
}
