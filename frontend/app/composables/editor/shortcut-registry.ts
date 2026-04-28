export type ShortcutScope = 'global' | 'text-view'

export type ShortcutHelpGroupId = 'editing' | 'tools' | 'view' | 'navigation' | 'text' | 'panels' | 'modes'

export interface ShortcutDefinition {
  description: string
  group: ShortcutHelpGroupId
  scope: ShortcutScope
  defaultBindings: string[]
  showInHelp?: boolean
  showInSettings?: boolean
  configurable?: boolean
}

export const SHORTCUT_HELP_GROUPS: Array<{
  id: ShortcutHelpGroupId
  title: string
  description: string
  icon: string
}> = [
  {
    id: 'editing',
    title: 'Editing',
    description: 'Selection and document actions.',
    icon: 'i-lucide-pencil'
  },
  {
    id: 'tools',
    title: 'Tools',
    description: 'Drawing and cut tool shortcuts.',
    icon: 'i-lucide-scan-line'
  },
  {
    id: 'view',
    title: 'View',
    description: 'View mode and viewport controls.',
    icon: 'i-lucide-scan-search'
  },
  {
    id: 'navigation',
    title: 'Navigation',
    description: 'Move between elements and images.',
    icon: 'i-lucide-arrow-up-down'
  },
  {
    id: 'text',
    title: 'Text View',
    description: 'Shortcuts specific to the textline list and text inputs.',
    icon: 'i-lucide-text-cursor-input'
  },
  {
    id: 'panels',
    title: 'Panels',
    description: 'Open, close, and inspect editor panels.',
    icon: 'i-lucide-panels-top-left'
  },
  {
    id: 'modes',
    title: 'Modes',
    description: 'Switch editor modes and utility overlays.',
    icon: 'i-lucide-layout-dashboard'
  }
]

export const SHORTCUT_DEFINITIONS = {
  undo: {
    description: 'Undo',
    group: 'editing',
    scope: 'global',
    defaultBindings: ['meta_z']
  },
  redo: {
    description: 'Redo',
    group: 'editing',
    scope: 'global',
    defaultBindings: ['meta_shift_z', 'meta_y']
  },

  selectMode: {
    description: 'Select mode',
    group: 'tools',
    scope: 'global',
    defaultBindings: ['v']
  },
  moveMode: {
    description: 'Move mode',
    group: 'tools',
    scope: 'global',
    defaultBindings: ['g']
  },

  regionPolygon: {
    description: 'Region (Polygon)',
    group: 'tools',
    scope: 'global',
    defaultBindings: ['p']
  },
  regionRectangle: {
    description: 'Region (Rectangle)',
    group: 'tools',
    scope: 'global',
    defaultBindings: ['r']
  },

  textlinePolygon: {
    description: 'Textline (Polygon)',
    group: 'tools',
    scope: 'global',
    defaultBindings: ['t']
  },
  textlineRectangle: {
    description: 'Textline (Rectangle)',
    group: 'tools',
    scope: 'global',
    defaultBindings: ['shift_t']
  },

  baseline: {
    description: 'Baseline',
    group: 'tools',
    scope: 'global',
    defaultBindings: ['b']
  },

  cutLine: {
    description: 'Cut line',
    group: 'tools',
    scope: 'global',
    defaultBindings: ['c']
  },
  cutPolygon: {
    description: 'Cut polygon',
    group: 'tools',
    scope: 'global',
    defaultBindings: ['shift_c']
  },
  cutRectangle: {
    description: 'Cut rectangle',
    group: 'tools',
    scope: 'global',
    defaultBindings: ['alt_c']
  },

  defaultView: {
    description: 'Default view',
    group: 'view',
    scope: 'global',
    defaultBindings: ['1']
  },
  textlineView: {
    description: 'Textline view',
    group: 'view',
    scope: 'global',
    defaultBindings: ['2']
  },
  baselineView: {
    description: 'Baseline view',
    group: 'view',
    scope: 'global',
    defaultBindings: ['3']
  },

  clearSelection: {
    description: 'Clear selection',
    group: 'editing',
    scope: 'global',
    defaultBindings: []
  },
  selectAll: {
    description: 'Select all',
    group: 'editing',
    scope: 'global',
    defaultBindings: ['meta_a']
  },
  delete: {
    description: 'Delete selected',
    group: 'editing',
    scope: 'global',
    defaultBindings: ['delete', 'backspace']
  },
  merge: {
    description: 'Merge selection',
    group: 'editing',
    scope: 'global',
    defaultBindings: ['m']
  },

  nextElement: {
    description: 'Next element',
    group: 'navigation',
    scope: 'global',
    defaultBindings: ['arrowdown']
  },
  prevElement: {
    description: 'Previous element',
    group: 'navigation',
    scope: 'global',
    defaultBindings: ['arrowup']
  },

  nextTextField: {
    description: 'Next text variant field',
    group: 'text',
    scope: 'text-view',
    defaultBindings: ['tab']
  },
  nextTextlineGtField: {
    description: 'Next textline GT field',
    group: 'text',
    scope: 'text-view',
    defaultBindings: ['enter']
  },
  prevTextlineGtField: {
    description: 'Previous textline GT field',
    group: 'text',
    scope: 'text-view',
    defaultBindings: ['shift_enter']
  },
  prevTextField: {
    description: 'Previous text variant field',
    group: 'text',
    scope: 'text-view',
    defaultBindings: ['shift_tab']
  },
  blurTextField: {
    description: 'Blur active text field',
    group: 'text',
    scope: 'text-view',
    defaultBindings: ['escape']
  },
  nextSameIndexField: {
    description: 'Jump to next field with same index',
    group: 'text',
    scope: 'text-view',
    defaultBindings: ['alt_enter']
  },
  createGtFromRecognition: {
    description: 'Create GT from selected textline recognition',
    group: 'text',
    scope: 'text-view',
    defaultBindings: ['meta_alt_g']
  },

  zoomIn: {
    description: 'Zoom in',
    group: 'view',
    scope: 'global',
    defaultBindings: ['meta_=']
  },
  zoomOut: {
    description: 'Zoom out',
    group: 'view',
    scope: 'global',
    defaultBindings: ['meta_-']
  },
  fitToContent: {
    description: 'Fit to content',
    group: 'view',
    scope: 'global',
    defaultBindings: ['meta_0']
  },
  centerOnSelection: {
    description: 'Center on selection',
    group: 'view',
    scope: 'global',
    defaultBindings: ['f', 'space']
  },

  toggleLeftSidebar: {
    description: 'Toggle left sidebar',
    group: 'panels',
    scope: 'global',
    defaultBindings: ['meta_\\']
  },
  toggleRightSidebar: {
    description: 'Toggle right sidebar',
    group: 'panels',
    scope: 'global',
    defaultBindings: ['meta_shift_\\']
  },

  layoutMode: {
    description: 'Layout mode',
    group: 'modes',
    scope: 'global',
    defaultBindings: ['l']
  },
  textMode: {
    description: 'Text mode',
    group: 'modes',
    scope: 'global',
    defaultBindings: ['shift_l']
  },
  toggleVirtualKeyboard: {
    description: 'Toggle virtual keyboard',
    group: 'modes',
    scope: 'global',
    defaultBindings: ['k']
  },

  history: {
    description: 'History',
    group: 'panels',
    scope: 'global',
    defaultBindings: ['h'],
    configurable: false,
    showInHelp: false,
    showInSettings: false
  },
  save: {
    description: 'Save',
    group: 'editing',
    scope: 'global',
    defaultBindings: ['meta_s']
  },
  nextImage: {
    description: 'Next image',
    group: 'navigation',
    scope: 'global',
    defaultBindings: ['meta_arrowdown']
  },
  prevImage: {
    description: 'Previous image',
    group: 'navigation',
    scope: 'global',
    defaultBindings: ['meta_arrowup']
  },
  closeActiveTab: {
    description: 'Close active tab',
    group: 'panels',
    scope: 'global',
    defaultBindings: ['alt_w']
  },
  showHelp: {
    description: 'Open keyboard shortcuts',
    group: 'panels',
    scope: 'global',
    defaultBindings: ['shift_/']
  }
} as const satisfies Record<string, ShortcutDefinition>

export type ShortcutCommandId = keyof typeof SHORTCUT_DEFINITIONS

export type ShortcutBindingsMap = Partial<Record<ShortcutCommandId, string[]>>

export interface ShortcutPreferences {
  version: 1
  bindings: ShortcutBindingsMap
}

export interface ResolvedShortcutDefinition extends ShortcutDefinition {
  id: ShortcutCommandId
  bindings: string[]
}

export interface ShortcutConflict {
  binding: string
  commandIds: ShortcutCommandId[]
  scope: ShortcutScope
}

const EMPTY_SHORTCUT_PREFERENCES: ShortcutPreferences = {
  version: 1,
  bindings: {}
}

const SHIFTED_SYMBOL_MAP: Record<string, string> = {
  '?': '/',
  '+': '=',
  '_': '-',
  ':': ';',
  '"': '\'',
  '<': ',',
  '>': '.',
  '{': '[',
  '}': ']',
  '|': '\\'
}

const DISPLAY_KEY_MAP: Record<string, string> = {
  arrowup: 'Up',
  arrowdown: 'Down',
  arrowleft: 'Left',
  arrowright: 'Right',
  escape: 'Esc',
  enter: 'Enter',
  tab: 'Tab',
  space: 'Space',
  delete: 'Del',
  backspace: 'Backspace'
}

const RESERVED_SHORTCUT_BINDINGS = new Set([
  'meta_r',
  'meta_shift_r',
  'meta_t',
  'meta_shift_t',
  'meta_n',
  'meta_shift_n',
  'meta_p',
  'meta_shift_p',
  'meta_q',
  'meta_w',
  'meta_shift_w',
  'meta_l',
  'meta_shift_m',
  'meta_alt_i'
])

function isMacLikePlatform(platform?: string): boolean {
  if (platform) return platform.toLowerCase() === 'mac'
  if (typeof navigator === 'undefined') return false
  return /mac|iphone|ipad|ipod/i.test(navigator.userAgent)
}

function normalizeKeyToken(token: string): string | null {
  const normalized = token.trim().toLowerCase()
  if (!normalized) return null

  if (normalized === ' ') return 'space'
  if (normalized === 'esc') return 'escape'
  if (normalized === 'del') return 'delete'
  if (normalized === 'return') return 'enter'
  if (normalized === 'cmd' || normalized === 'command' || normalized === 'mod') return 'meta'
  if (normalized === 'option') return 'alt'

  return SHIFTED_SYMBOL_MAP[normalized] ?? normalized
}

function sortAndNormalizeModifiers(tokens: string[]): string[] {
  const unique = new Set(tokens)
  return ['meta', 'ctrl', 'alt', 'shift'].filter(modifier => unique.has(modifier))
}

function sanitizeBindingList(bindings: Iterable<string> | undefined): string[] {
  const normalized = new Set<string>()
  for (const binding of bindings ?? []) {
    const next = normalizeShortcutBinding(binding)
    if (next) normalized.add(next)
  }
  return [...normalized]
}

export function normalizeShortcutBinding(binding: string | null | undefined): string | null {
  if (!binding) return null

  const parts = binding
    .split('_')
    .map(part => normalizeKeyToken(part))
    .filter((part): part is string => Boolean(part))

  if (parts.length === 0) return null

  const key = parts[parts.length - 1]
  if (!key || ['meta', 'ctrl', 'alt', 'shift'].includes(key)) return null

  const modifiers = sortAndNormalizeModifiers(parts.slice(0, -1))
  return [...modifiers, key].join('_')
}

export function areShortcutBindingsEqual(a: string[] | undefined, b: string[] | undefined): boolean {
  const normalizedA = sanitizeBindingList(a)
  const normalizedB = sanitizeBindingList(b)
  if (normalizedA.length !== normalizedB.length) return false
  return normalizedA.every((binding, index) => binding === normalizedB[index])
}

export function normalizeShortcutPreferences(input: unknown): ShortcutPreferences {
  if (!input || typeof input !== 'object') return { ...EMPTY_SHORTCUT_PREFERENCES }

  const raw = input as { version?: unknown, bindings?: unknown }
  const bindings: ShortcutBindingsMap = {}

  if (raw.bindings && typeof raw.bindings === 'object') {
    for (const [key, value] of Object.entries(raw.bindings as Record<string, unknown>)) {
      if (!(key in SHORTCUT_DEFINITIONS)) continue
      if (!Array.isArray(value)) continue

      const normalized = sanitizeBindingList(value.map(item => String(item)))
      if (normalized.length > 0) {
        bindings[key as ShortcutCommandId] = normalized
      }
    }
  }

  return {
    version: 1,
    bindings
  }
}

export function createShortcutPreferences(overrides: ShortcutBindingsMap): ShortcutPreferences {
  return normalizeShortcutPreferences({
    version: 1,
    bindings: overrides
  })
}

export function getDefaultShortcutBindings(): Record<ShortcutCommandId, string[]> {
  return Object.fromEntries(
    (Object.entries(SHORTCUT_DEFINITIONS) as Array<[ShortcutCommandId, ShortcutDefinition]>)
      .map(([id, definition]) => [id, [...definition.defaultBindings]])
  ) as Record<ShortcutCommandId, string[]>
}

export function getEffectiveShortcutBindings(preferences: ShortcutPreferences | null | undefined): Record<ShortcutCommandId, string[]> {
  const normalized = normalizeShortcutPreferences(preferences)
  const defaults = getDefaultShortcutBindings()

  for (const [id, bindings] of Object.entries(normalized.bindings) as Array<[ShortcutCommandId, string[]]>) {
    if (bindings.length > 0) {
      defaults[id] = [...bindings]
    }
  }

  return defaults
}

export function getResolvedShortcutDefinitions(preferences: ShortcutPreferences | null | undefined): Record<ShortcutCommandId, ResolvedShortcutDefinition> {
  const bindings = getEffectiveShortcutBindings(preferences)

  return Object.fromEntries(
    (Object.entries(SHORTCUT_DEFINITIONS) as Array<[ShortcutCommandId, ShortcutDefinition]>)
      .map(([id, definition]) => [
        id,
        {
          ...definition,
          id,
          bindings: [...bindings[id]]
        }
      ])
  ) as Record<ShortcutCommandId, ResolvedShortcutDefinition>
}

export function serializeKeyboardEventToBinding(
  event: Pick<KeyboardEvent, 'key' | 'metaKey' | 'ctrlKey' | 'altKey' | 'shiftKey'>,
  options?: { platform?: 'mac' | 'other' }
): string | null {
  const key = normalizeKeyToken(event.key)
  if (!key || ['meta', 'ctrl', 'alt', 'shift'].includes(key)) return null

  const modifiers: string[] = []
  const isMac = isMacLikePlatform(options?.platform)

  if (isMac ? event.metaKey : event.ctrlKey) modifiers.push('meta')
  if (isMac ? event.ctrlKey : event.metaKey) modifiers.push('ctrl')
  if (event.altKey) modifiers.push('alt')
  if (event.shiftKey) modifiers.push('shift')

  return normalizeShortcutBinding([...modifiers, key].join('_'))
}

export function getShortcutKbds(binding: string): string[] {
  const normalized = normalizeShortcutBinding(binding)
  if (!normalized) return []

  const parts = normalized.split('_')
  return parts.map((part, index) => {
    if (index < parts.length - 1) return part
    if (part.length === 1 && /[a-z]/.test(part)) return part.toUpperCase()
    return DISPLAY_KEY_MAP[part] ?? part
  })
}

export function isReservedShortcutBinding(binding: string): boolean {
  const normalized = normalizeShortcutBinding(binding)
  if (!normalized) return false
  return RESERVED_SHORTCUT_BINDINGS.has(normalized)
}

export function getShortcutConflicts(
  bindings: Record<ShortcutCommandId, string[]>
): ShortcutConflict[] {
  const collisions = new Map<string, ShortcutCommandId[]>()

  for (const [id, definition] of Object.entries(SHORTCUT_DEFINITIONS) as Array<[ShortcutCommandId, ShortcutDefinition]>) {
    if (definition.configurable === false) continue

    for (const binding of bindings[id] ?? []) {
      const key = `${definition.scope}:${binding}`
      const existing = collisions.get(key) ?? []
      existing.push(id)
      collisions.set(key, existing)
    }
  }

  return [...collisions.entries()]
    .filter(([, commandIds]) => commandIds.length > 1)
    .map(([key, commandIds]) => {
      const [scope, binding] = key.split(':', 2) as [ShortcutScope, string]
      return { scope, binding, commandIds }
    })
}

export function getShortcutConflictMap(bindings: Record<ShortcutCommandId, string[]>): Partial<Record<ShortcutCommandId, string[]>> {
  const result: Partial<Record<ShortcutCommandId, string[]>> = {}

  for (const conflict of getShortcutConflicts(bindings)) {
    for (const commandId of conflict.commandIds) {
      result[commandId] = [...(result[commandId] ?? []), conflict.binding]
    }
  }

  return result
}

export function setShortcutOverride(
  overrides: ShortcutBindingsMap,
  commandId: ShortcutCommandId,
  bindings: string[]
): ShortcutBindingsMap {
  const normalized = sanitizeBindingList(bindings)

  if (normalized.length === 0 || areShortcutBindingsEqual(normalized, SHORTCUT_DEFINITIONS[commandId].defaultBindings)) {
    const { [commandId]: _removed, ...rest } = overrides
    return rest
  }

  return {
    ...overrides,
    [commandId]: normalized
  }
}

export function resetShortcutOverride(
  overrides: ShortcutBindingsMap,
  commandId: ShortcutCommandId
): ShortcutBindingsMap {
  const { [commandId]: _removed, ...rest } = overrides
  return rest
}
