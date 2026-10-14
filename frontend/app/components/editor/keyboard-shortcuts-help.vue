<script setup lang="ts">
import {
  SHORTCUT_DEFINITIONS,
  SHORTCUT_HOLD_GESTURES,
  SHORTCUT_HELP_GROUPS,
  areShortcutBindingsEqual,
  createShortcutPreferences,
  getEffectiveShortcutBindings,
  getShortcutConflicts,
  getShortcutKbds,
  isReservedShortcutBinding,
  resetShortcutOverride,
  serializeKeyboardEventToBinding,
  setShortcutOverride,
  type ShortcutCommandId,
  type ShortcutHelpGroupId,
  type ResolvedShortcutDefinition
} from '@/composables/editor/shortcut-registry'
import { useShortcutBindings } from '@/composables/editor/use-shortcut-bindings'
import { useBlockEditorCanvasInteractions } from '@/composables/editor/use-canvas-interaction-blocker'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()
const isOpen = computed({
  get: () => props.open,
  set: value => emit('update:open', value)
})
useBlockEditorCanvasInteractions(isOpen)

const searchQuery = ref('')
const { shortcutPreferences, resolvedShortcutDefinitions, saveShortcutPreferences } = useShortcutBindings()
const editingCommandId = ref<ShortcutCommandId | null>(null)
const draftBindings = ref<string[]>([])
const captureIndex = ref<number | null>(null)
const captureInput = ref<HTMLInputElement | null>(null)
const captureError = ref('')
const resetError = ref<{ commandId: ShortcutCommandId, message: string } | null>(null)
const hasOverrides = computed(() => Object.keys(shortcutPreferences.value.bindings).length > 0)
const openingCombos = computed(() => resolvedShortcutDefinitions.value.showHelp.bindings.map(getShortcutKbds))
const editingDefinition = computed(() => editingCommandId.value ? resolvedShortcutDefinitions.value[editingCommandId.value] : null)
const draftMatchesDefaults = computed(() => areShortcutBindingsEqual(draftBindings.value, editingDefinition.value?.defaultBindings))

function conflictMessage(commandId: ShortcutCommandId, bindings: Record<ShortcutCommandId, string[]>) {
  return getShortcutConflicts(bindings)
    .filter(conflict => conflict.commandIds.includes(commandId))
    .map(conflict => `${getShortcutKbds(conflict.binding).join(' + ')} is already used by ${conflict.commandIds
      .filter(id => id !== commandId).map(id => SHORTCUT_DEFINITIONS[id].description).join(', ')}.`)
    .join(' ')
}

const draftConflict = computed(() => {
  const id = editingCommandId.value
  if (!id) return ''
  const overrides = setShortcutOverride(shortcutPreferences.value.bindings, id, draftBindings.value)
  return conflictMessage(id, getEffectiveShortcutBindings(createShortcutPreferences(overrides)))
})

async function finishEditing(restoreFocus = true) {
  const id = editingCommandId.value
  editingCommandId.value = null
  draftBindings.value = []
  captureIndex.value = null
  captureError.value = ''
  if (restoreFocus && id) {
    await nextTick()
    const target = document.getElementById(`shortcut-menu-${id}`) ?? document.getElementById('shortcut-search')
    target?.focus()
  }
}

function customize(commandId: ShortcutCommandId) {
  editingCommandId.value = commandId
  draftBindings.value = [...resolvedShortcutDefinitions.value[commandId].bindings]
  captureIndex.value = null
  captureError.value = ''
  resetError.value = null
}

async function beginCapture(index: number) {
  captureIndex.value = index
  captureError.value = ''
  await nextTick()
  captureInput.value?.focus()
}

function stopCapture() {
  captureIndex.value = null
  captureError.value = ''
  const id = editingCommandId.value
  void nextTick(() => {
    if (id === editingCommandId.value) document.getElementById(`shortcut-add-${id}`)?.focus()
  })
}

function captureKeydown(event: KeyboardEvent) {
  event.preventDefault()
  event.stopPropagation()
  if (captureIndex.value === null || event.repeat || event.isComposing) return
  if (['Control', 'Meta', 'Alt', 'Shift', 'AltGraph'].includes(event.key)) {
    captureError.value = 'Press a complete shortcut, not just a modifier key.'
    return
  }
  const binding = serializeKeyboardEventToBinding(event)
  if (!binding) return
  if (isReservedShortcutBinding(binding)) {
    captureError.value = 'That shortcut is reserved by the browser or operating system.'
    return
  }
  const next = [...draftBindings.value]
  next[captureIndex.value] = binding
  draftBindings.value = [...new Set(next)]
  stopCapture()
}

function removeBinding(index: number) {
  if (draftBindings.value.length <= 1) return
  stopCapture()
  draftBindings.value = draftBindings.value.filter((_, bindingIndex) => bindingIndex !== index)
}

function saveCommand() {
  const id = editingCommandId.value
  if (!id || draftConflict.value || captureIndex.value !== null || !draftBindings.value.length) return
  saveShortcutPreferences(createShortcutPreferences(setShortcutOverride(shortcutPreferences.value.bindings, id, draftBindings.value)))
  void finishEditing()
}

function resetCommand(commandId: ShortcutCommandId) {
  const overrides = resetShortcutOverride(shortcutPreferences.value.bindings, commandId)
  const message = conflictMessage(commandId, getEffectiveShortcutBindings(createShortcutPreferences(overrides)))
  resetError.value = message ? { commandId, message } : null
  if (message) return
  saveShortcutPreferences(createShortcutPreferences(overrides))
  if (editingCommandId.value === commandId) void finishEditing()
}

function resetAll() {
  saveShortcutPreferences(createShortcutPreferences({}))
  resetError.value = null
  void finishEditing()
}

function shortcutMenu(commandId: ShortcutCommandId) {
  return [
    { label: 'Customize', icon: 'i-lucide-pencil', onSelect: () => customize(commandId) },
    { label: 'Reset', icon: 'i-lucide-rotate-ccw', disabled: !shortcutPreferences.value.bindings[commandId], onSelect: () => resetCommand(commandId) }
  ]
}

type ShortcutHelpItem = {
  id: string
  commandId?: ShortcutCommandId
  description: string
  group: ShortcutHelpGroupId
  combos: { kbds: string[] }[]
  searchText: string
  isCustomized?: boolean
}

const allShortcutItems = computed<ShortcutHelpItem[]>(() => {
  const commandItems = (Object.entries(resolvedShortcutDefinitions.value) as Array<[ShortcutCommandId, ResolvedShortcutDefinition]>)
    .filter(([, definition]) => definition.showInHelp !== false)
    .map(([id, definition]) => {
      const bindings = editingCommandId.value === id ? draftBindings.value : definition.bindings
      const combos = bindings.map(binding => ({ kbds: getShortcutKbds(binding) }))
      return {
        id,
        commandId: definition.configurable !== false && definition.showInSettings !== false ? id : undefined,
        description: definition.description,
        group: definition.group,
        combos,
        isCustomized: Boolean(shortcutPreferences.value.bindings[id]),
        searchText: [definition.description, definition.group, definition.scope, ...combos.map(combo => combo.kbds.join(' '))].join(' ').toLocaleLowerCase()
      }
    })
  const holdItems: ShortcutHelpItem[] = SHORTCUT_HOLD_GESTURES.map(gesture => ({
    id: gesture.id,
    description: gesture.description,
    group: gesture.group,
    combos: [{ kbds: gesture.kbds }],
    searchText: [gesture.description, gesture.group, ...gesture.kbds].join(' ').toLocaleLowerCase()
  }))
  return [...commandItems, ...holdItems]
})
const normalizedSearchQuery = computed(() => searchQuery.value.trim().toLocaleLowerCase())
const visibleShortcutGroups = computed(() => SHORTCUT_HELP_GROUPS.map(group => ({
  ...group,
  shortcuts: allShortcutItems.value.filter(shortcut => shortcut.group === group.id
    && (shortcut.id === editingCommandId.value || shortcut.id === resetError.value?.commandId || !normalizedSearchQuery.value || shortcut.searchText.includes(normalizedSearchQuery.value)))
})).filter(group => group.shortcuts.length > 0))
const totalShortcutCount = computed(() => allShortcutItems.value.length)
const visibleShortcutCount = computed(() => visibleShortcutGroups.value.reduce((count, group) => count + group.shortcuts.length, 0))

watch(() => props.open, (open) => {
  if (!open) {
    void finishEditing(false)
    resetError.value = null
  }
})
</script>

<template>
  <UModal v-model:open="isOpen" :ui="{ content: 'sm:max-w-6xl' }">
    <template #content>
      <UCard class="max-h-[85vh] overflow-hidden">
        <template #header>
          <div class="flex flex-col gap-4">
            <div class="flex items-start justify-between gap-4">
              <div class="space-y-1">
                <div class="flex items-center gap-2">
                  <UIcon name="i-lucide-keyboard" class="size-5 text-primary" />
                  <h2 class="text-lg font-semibold text-highlighted">
                    Keyboard Shortcuts
                  </h2>
                </div>
                <p class="text-sm text-muted">
                  Complete editor shortcut reference. Global shortcuts are ignored while typing, while text-view shortcuts stay active inside the textline editor.
                </p>
              </div>

              <div class="flex items-center gap-2">
                <UButton
                  icon="i-lucide-rotate-ccw"
                  color="neutral"
                  variant="soft"
                  size="sm"
                  label="Reset all"
                  :disabled="!hasOverrides"
                  @click="resetAll"
                />

                <UButton
                  icon="i-lucide-x"
                  color="neutral"
                  variant="ghost"
                  size="sm"
                  aria-label="Close"
                  @click="() => { isOpen = false }"
                />
              </div>
            </div>

            <div class="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center">
              <UInput
                id="shortcut-search"
                v-model="searchQuery"
                icon="i-lucide-search"
                placeholder="Search shortcuts, actions, or keys"
                size="lg"
              />

              <div class="flex items-center gap-2">
                <UBadge color="neutral" variant="subtle" size="sm">
                  {{ visibleShortcutCount }} / {{ totalShortcutCount }} shortcuts
                </UBadge>
                <UBadge color="primary" variant="subtle" size="sm">
                  Editor
                </UBadge>
              </div>
            </div>
          </div>
        </template>

        <div class="max-h-[62vh] overflow-y-auto pr-1">
          <div v-if="visibleShortcutGroups.length > 0" class="grid grid-cols-1 gap-4 xl:grid-cols-2">
            <UCard
              v-for="group in visibleShortcutGroups"
              :key="group.id"
              :ui="{ body: 'p-0' }"
              class="border border-default/80 bg-default/70"
            >
              <template #header>
                <div class="flex items-start justify-between gap-3">
                  <div class="flex items-start gap-3">
                    <div class="rounded-md border border-default bg-elevated p-2">
                      <UIcon :name="group.icon" class="size-4 text-primary" />
                    </div>

                    <div class="space-y-1">
                      <div class="flex items-center gap-2">
                        <h3 class="text-sm font-semibold text-highlighted">
                          {{ group.title }}
                        </h3>
                        <UBadge color="neutral" variant="soft" size="xs">
                          {{ group.shortcuts.length }}
                        </UBadge>
                      </div>
                      <p class="text-xs text-muted">
                        {{ group.description }}
                      </p>
                    </div>
                  </div>
                </div>
              </template>

              <div class="divide-y divide-default">
                <div
                  v-for="shortcut in group.shortcuts"
                  :key="shortcut.id"
                  class="px-4 py-3"
                >
                  <div class="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                    <div class="min-w-0 space-y-1">
                      <p class="text-sm font-medium text-highlighted">
                        {{ shortcut.description }}
                        <UBadge
                          v-if="shortcut.isCustomized"
                          color="primary"
                          variant="subtle"
                          size="xs"
                        >
                          Custom
                        </UBadge>
                      </p>
                      <p v-if="shortcut.combos.length > 1 && editingCommandId !== shortcut.id" class="text-xs text-muted">
                        Alternative shortcuts are listed on the right.
                      </p>
                    </div>

                    <div class="flex shrink-0 flex-wrap items-center gap-2 md:max-w-[52%] md:justify-end">
                      <span v-if="!shortcut.combos.length && editingCommandId !== shortcut.id" class="text-xs text-muted">Unassigned</span>
                      <template v-if="editingCommandId !== shortcut.id">
                        <div
                          v-for="(combo, comboIndex) in shortcut.combos"
                          :key="`${shortcut.id}-${comboIndex}`"
                          class="flex items-center gap-1.5"
                        >
                          <template v-for="(kbd, index) in combo.kbds" :key="`${shortcut.id}-${comboIndex}-${kbd}-${index}`">
                            <span v-if="index > 0" class="text-[11px] text-muted">+</span>
                            <UKbd :value="kbd" size="lg" variant="subtle" />
                          </template>
                        </div>
                      </template>
                      <UDropdownMenu v-if="shortcut.commandId" :items="shortcutMenu(shortcut.commandId)">
                        <UButton
                          :id="`shortcut-menu-${shortcut.id}`"
                          icon="i-lucide-ellipsis-vertical"
                          color="neutral"
                          variant="ghost"
                          size="xs"
                          :aria-label="`Options for ${shortcut.description}`"
                        />
                      </UDropdownMenu>
                    </div>
                  </div>
                  <p v-if="resetError?.commandId === shortcut.id" role="alert" class="mt-2 text-sm text-error">
                    {{ resetError.message }}
                  </p>
                  <div v-if="editingCommandId === shortcut.id && editingDefinition" class="mt-3 space-y-3 border-t border-default pt-3">
                    <p class="text-xs text-muted">
                      Click a shortcut to change it.
                    </p>
                    <div class="flex flex-wrap gap-2">
                      <div v-for="(binding, index) in draftBindings" :key="`${index}-${binding}`" class="flex items-center gap-1">
                        <UButton
                          color="neutral"
                          variant="outline"
                          size="sm"
                          class="bg-default"
                          :aria-label="`Replace ${getShortcutKbds(binding).join(' + ')}`"
                          @click="beginCapture(index)"
                        >
                          <UKbd
                            v-for="(kbd, kbdIndex) in getShortcutKbds(binding)"
                            :key="`${kbdIndex}-${kbd}`"
                            :value="kbd"
                            size="sm"
                            variant="outline"
                            class="bg-transparent px-0 ring-0"
                          />
                        </UButton>
                        <UButton
                          icon="i-lucide-x"
                          color="neutral"
                          variant="ghost"
                          size="xs"
                          :aria-label="`Remove ${getShortcutKbds(binding).join(' + ')}`"
                          :disabled="draftBindings.length <= 1"
                          @click="removeBinding(index)"
                        />
                      </div>
                      <UButton
                        :id="`shortcut-add-${shortcut.id}`"
                        icon="i-lucide-plus"
                        color="neutral"
                        variant="ghost"
                        size="sm"
                        label="Add shortcut"
                        @click="beginCapture(draftBindings.length)"
                      />
                    </div>
                    <div v-if="!draftMatchesDefaults" class="flex flex-wrap items-center gap-1 text-xs text-muted">
                      Default:
                      <span v-if="!editingDefinition.defaultBindings.length">Unassigned</span>
                      <span v-for="(binding, index) in editingDefinition.defaultBindings" :key="binding" class="inline-flex items-center gap-1">
                        <span v-if="index">/</span>
                        <UKbd
                          v-for="(kbd, kbdIndex) in getShortcutKbds(binding)"
                          :key="`${kbdIndex}-${kbd}`"
                          :value="kbd"
                          size="sm"
                          variant="subtle"
                        />
                      </span>
                    </div>
                    <div v-if="captureIndex !== null" class="space-y-2">
                      <label :for="`shortcut-capture-${shortcut.id}`" class="text-sm text-highlighted">Press the new shortcut</label>
                      <div class="flex items-center gap-2">
                        <input
                          :id="`shortcut-capture-${shortcut.id}`"
                          :ref="el => { captureInput = el as HTMLInputElement | null }"
                          readonly
                          placeholder="Press keys…"
                          :aria-describedby="captureError ? 'shortcut-capture-error' : undefined"
                          class="min-w-0 flex-1 rounded-md border border-default bg-default px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary"
                          @keydown="captureKeydown"
                        >
                        <UButton
                          color="neutral"
                          variant="ghost"
                          size="xs"
                          label="Cancel recording"
                          @click="stopCapture"
                        />
                      </div>
                    </div>
                    <p
                      v-if="captureError"
                      id="shortcut-capture-error"
                      role="alert"
                      class="text-sm text-error"
                    >
                      {{ captureError }}
                    </p>
                    <p v-if="draftConflict" role="alert" class="text-sm text-error">
                      {{ draftConflict }}
                    </p>
                    <div class="flex justify-end gap-2">
                      <UButton
                        color="neutral"
                        variant="ghost"
                        size="sm"
                        label="Cancel"
                        @click="finishEditing()"
                      />
                      <UButton
                        size="sm"
                        label="Save"
                        :disabled="Boolean(draftConflict) || captureIndex !== null || !draftBindings.length"
                        @click="saveCommand"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </UCard>
          </div>

          <div
            v-else
            class="flex min-h-60 flex-col items-center justify-center gap-3 rounded-lg border border-dashed border-default px-6 text-center"
          >
            <div class="rounded-full border border-default bg-elevated p-3">
              <UIcon name="i-lucide-search-x" class="size-5 text-muted" />
            </div>

            <div class="space-y-1">
              <p class="text-sm font-medium text-highlighted">
                No shortcuts match your search
              </p>
              <p class="text-sm text-muted">
                Try a tool name, action, or key like <span class="font-medium text-highlighted">tab</span> or <span class="font-medium text-highlighted">zoom</span>.
              </p>
            </div>
          </div>
        </div>

        <template #footer>
          <div class="flex flex-col gap-2 text-xs text-muted sm:flex-row sm:items-center sm:justify-between">
            <div class="flex items-center gap-2">
              <span>Open this dialog with</span>
              <div v-for="(combo, comboIndex) in openingCombos" :key="comboIndex" class="flex items-center gap-1">
                <template v-for="(kbd, index) in combo" :key="`${index}-${kbd}`">
                  <span v-if="index" class="text-[11px] text-muted">+</span>
                  <UKbd :value="kbd" size="lg" variant="subtle" />
                </template>
              </div>
            </div>

            <div class="flex items-center gap-2">
              <span>Modifier labels adapt to your platform automatically.</span>
            </div>
          </div>
        </template>
      </UCard>
    </template>
  </UModal>
</template>
