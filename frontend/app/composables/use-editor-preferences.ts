import type { ToolbarLayout, VirtualKeyboardMode, TextItemLayout } from '@/stores/editor/types'
import type { ShortcutPreferences } from '@/composables/editor/shortcut-registry'

export interface EditorPreferences {
  backgroundColor: string | null
  backgroundOpacity: number | null
  toolbarLayout: ToolbarLayout | null
  toolbarCompact: boolean | null
  leftCollapsed: boolean | null
  rightCollapsed: boolean | null
  leftWidthPx: number | null
  rightWidthPx: number | null
  constrainToImage: boolean | null
  constrainToParent: boolean | null
  autoSelect: boolean | null
  preventOverlapOnCreate: boolean | null
  moveWithChildren: boolean | null
  cutMinAreaThreshold: number | null
  defaultLineWidth: string | null
  virtualKeyboardMode: VirtualKeyboardMode | null
  selectedVirtualKeyboardId: string | null
  textViewFontSize: number | null
  textViewPadding: number | null
  textItemLayout: TextItemLayout | null
  canvasTextCorrectionOverlaySnapToLine: boolean | null
  canvasTextCorrectionOverlayXRatio: number | null
  canvasTextCorrectionOverlayYRatio: number | null
  canvasTextCorrectionZoom: number | null
  highlightUnknownCodecChars: boolean | null
  shortcutBindings: ShortcutPreferences | null
  tableColumnVisibility: Record<string, Record<string, boolean>> | null
  onboardingDashboardTourVersion: number | null
  onboardingEditorTourVersion: number | null
  onboardingTourCompletion: Record<string, true> | null
  onboardingToursOptedOut: boolean | null
}

interface EditorPreferencesState {
  preferences: EditorPreferences
  initialized: boolean
  isLoading: boolean
}

const DEFAULT_PREFERENCES: EditorPreferences = {
  backgroundColor: null,
  backgroundOpacity: null,
  toolbarLayout: null,
  toolbarCompact: null,
  leftCollapsed: null,
  rightCollapsed: null,
  leftWidthPx: null,
  rightWidthPx: null,
  constrainToImage: null,
  constrainToParent: null,
  autoSelect: null,
  preventOverlapOnCreate: null,
  moveWithChildren: null,
  cutMinAreaThreshold: null,
  defaultLineWidth: null,
  virtualKeyboardMode: null,
  selectedVirtualKeyboardId: null,
  textViewFontSize: null,
  textViewPadding: null,
  textItemLayout: null,
  canvasTextCorrectionOverlaySnapToLine: null,
  canvasTextCorrectionOverlayXRatio: null,
  canvasTextCorrectionOverlayYRatio: null,
  canvasTextCorrectionZoom: null,
  highlightUnknownCodecChars: null,
  shortcutBindings: null,
  tableColumnVisibility: null,
  onboardingDashboardTourVersion: null,
  onboardingEditorTourVersion: null,
  onboardingTourCompletion: null,
  onboardingToursOptedOut: null
}

const SAVE_DEBOUNCE_MS = 1200

export const useEditorPreferences = () => {
  const state = useState<EditorPreferencesState>('editor-preferences', () => ({
    preferences: { ...DEFAULT_PREFERENCES },
    initialized: false,
    isLoading: false
  }))

  let saveTimer: ReturnType<typeof setTimeout> | null = null
  let pendingSavePatch: Partial<EditorPreferences> = {}

  const savePreferences = async (prefs: Partial<EditorPreferences>) => {
    try {
      const data = await $fetch<EditorPreferences>('/api/editor/preferences', {
        method: 'PUT',
        body: prefs
      })
      state.value.preferences = { ...state.value.preferences, ...data }
      return true
    } catch (e) {
      console.error('Failed to save editor preferences:', e)
      return false
    }
  }

  const flushPendingSave = async () => {
    if (import.meta.server) return true
    if (Object.keys(pendingSavePatch).length === 0) return true

    const patch = { ...pendingSavePatch }
    pendingSavePatch = {}
    return savePreferences(patch)
  }

  const scheduleSave = (prefs: Partial<EditorPreferences>) => {
    if (import.meta.server) return

    pendingSavePatch = { ...pendingSavePatch, ...prefs }

    if (saveTimer) {
      clearTimeout(saveTimer)
    }

    saveTimer = setTimeout(() => {
      saveTimer = null
      void flushPendingSave()
    }, SAVE_DEBOUNCE_MS)
  }

  const fetchPreferences = async () => {
    if (import.meta.server || state.value.initialized) return state.value.preferences
    state.value.isLoading = true
    try {
      const data = await $fetch<EditorPreferences>('/api/editor/preferences')
      state.value.preferences = data
      state.value.initialized = true
      return data
    } catch (e) {
      console.error('Failed to fetch editor preferences:', e)
      return null
    } finally {
      state.value.isLoading = false
    }
  }

  const updatePreference = <K extends keyof EditorPreferences>(key: K, value: EditorPreferences[K]) => {
    state.value.preferences[key] = value
    scheduleSave({ [key]: value })
  }

  const updatePreferences = (prefs: Partial<EditorPreferences>) => {
    Object.assign(state.value.preferences, prefs)
    scheduleSave(prefs)
  }

  return {
    preferences: computed(() => state.value.preferences),
    isLoading: computed(() => state.value.isLoading),
    initialized: computed(() => state.value.initialized),
    fetchPreferences,
    updatePreference,
    updatePreferences,
    savePreferences
  }
}
