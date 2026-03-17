<script setup lang="ts">
import {
  SHORTCUT_DEFINITIONS,
  SHORTCUT_HELP_GROUPS,
  createShortcutPreferences,
  getEffectiveShortcutBindings,
  getShortcutConflictMap,
  getShortcutKbds,
  isReservedShortcutBinding,
  normalizeShortcutPreferences,
  resetShortcutOverride,
  serializeKeyboardEventToBinding,
  setShortcutOverride,
  type ShortcutBindingsMap,
  type ShortcutCommandId,
  type ShortcutDefinition
} from '@/composables/editor/shortcut-registry'
import { useShortcutBindings } from '@/composables/editor/use-shortcut-bindings'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const { shortcutPreferences, saveShortcutPreferences } = useShortcutBindings()

const isOpen = computed({
  get: () => props.open,
  set: value => emit('update:open', value)
})

const searchQuery = ref('')
const captureError = ref('')
const draftBindings = ref<ShortcutBindingsMap>({})
const captureState = reactive<{
  commandId: ShortcutCommandId | null
  mode: 'replace' | 'append'
  index: number
}>({
  commandId: null,
  mode: 'replace',
  index: -1
})

const effectiveBindings = computed(() =>
  getEffectiveShortcutBindings(createShortcutPreferences(draftBindings.value))
)

const conflictMap = computed(() => getShortcutConflictMap(effectiveBindings.value))

const hasConflicts = computed(() =>
  Object.values(conflictMap.value).some(conflicts => (conflicts?.length ?? 0) > 0)
)

const hasChanges = computed(() => {
  const persisted = normalizeShortcutPreferences(shortcutPreferences.value).bindings
  const draft = normalizeShortcutPreferences({ version: 1, bindings: draftBindings.value }).bindings
  return JSON.stringify(draft) !== JSON.stringify(persisted)
})

const normalizedSearchQuery = computed(() => searchQuery.value.trim().toLocaleLowerCase())

const visibleShortcutGroups = computed(() => {
  return SHORTCUT_HELP_GROUPS
    .map((group) => {
      const shortcuts = (Object.entries(SHORTCUT_DEFINITIONS) as Array<[ShortcutCommandId, ShortcutDefinition]>)
        .filter(([, definition]) => definition.showInSettings !== false && definition.group === group.id)
        .map(([id, definition]) => ({
          id,
          definition,
          bindings: effectiveBindings.value[id],
          conflicts: conflictMap.value[id] ?? [],
          isCustomized: Boolean(draftBindings.value[id])
        }))
        .filter((shortcut) => {
          if (!normalizedSearchQuery.value) return true
          const haystack = [
            shortcut.definition.description,
            group.title,
            shortcut.definition.scope,
            ...shortcut.bindings,
            ...shortcut.bindings.flatMap(binding => getShortcutKbds(binding))
          ].join(' ').toLocaleLowerCase()
          return haystack.includes(normalizedSearchQuery.value)
        })

      return {
        ...group,
        shortcuts
      }
    })
    .filter(group => group.shortcuts.length > 0)
})

const visibleShortcutCount = computed(() =>
  visibleShortcutGroups.value.reduce((count, group) => count + group.shortcuts.length, 0)
)

function syncDraftFromPreferences() {
  draftBindings.value = { ...normalizeShortcutPreferences(shortcutPreferences.value).bindings }
  captureError.value = ''
  captureState.commandId = null
  captureState.mode = 'replace'
  captureState.index = -1
}

function setCommandBindings(commandId: ShortcutCommandId, bindings: string[]) {
  draftBindings.value = setShortcutOverride(draftBindings.value, commandId, bindings)
}

function handleRemoveBinding(commandId: ShortcutCommandId, index: number) {
  const current = [...effectiveBindings.value[commandId]]
  if (current.length <= 1) return
  current.splice(index, 1)
  setCommandBindings(commandId, current)
}

function handleResetCommand(commandId: ShortcutCommandId) {
  draftBindings.value = resetShortcutOverride(draftBindings.value, commandId)
  if (captureState.commandId === commandId) {
    captureState.commandId = null
    captureError.value = ''
  }
}

function handleResetAll() {
  draftBindings.value = {}
  captureError.value = ''
  captureState.commandId = null
}

function beginReplaceCapture(commandId: ShortcutCommandId, index: number) {
  captureState.commandId = commandId
  captureState.mode = 'replace'
  captureState.index = index
  captureError.value = ''
}

function beginAppendCapture(commandId: ShortcutCommandId) {
  captureState.commandId = commandId
  captureState.mode = 'append'
  captureState.index = effectiveBindings.value[commandId].length
  captureError.value = ''
}

function stopCapture() {
  captureState.commandId = null
  captureState.mode = 'replace'
  captureState.index = -1
  captureError.value = ''
}

function saveAndClose() {
  if (hasConflicts.value) return
  saveShortcutPreferences(createShortcutPreferences(draftBindings.value))
  isOpen.value = false
}

function describeScope(commandId: ShortcutCommandId): string {
  return SHORTCUT_DEFINITIONS[commandId].scope === 'text-view' ? 'Text view' : 'Global'
}

function isCapturing(commandId: ShortcutCommandId, index: number): boolean {
  return captureState.commandId === commandId
    && captureState.mode === 'replace'
    && captureState.index === index
}

function isCapturingAppend(commandId: ShortcutCommandId): boolean {
  return captureState.commandId === commandId
    && captureState.mode === 'append'
}

watch(() => props.open, (open) => {
  if (open) {
    syncDraftFromPreferences()
  } else {
    stopCapture()
  }
}, { immediate: true })

const handleCaptureKeydown = (event: KeyboardEvent) => {
  if (!props.open || !captureState.commandId) return

  event.preventDefault()
  event.stopPropagation()

  const binding = serializeKeyboardEventToBinding(event)
  if (!binding) {
    captureError.value = 'Press a complete shortcut, not just a modifier key.'
    return
  }

  if (isReservedShortcutBinding(binding)) {
    captureError.value = 'That shortcut is reserved by the browser or operating system.'
    return
  }

  const current = [...effectiveBindings.value[captureState.commandId]]
  if (captureState.mode === 'append') {
    current.push(binding)
  } else {
    current[captureState.index] = binding
  }

  setCommandBindings(captureState.commandId, current)
  stopCapture()
}

watchEffect((onCleanup) => {
  if (!props.open || !captureState.commandId || !import.meta.client) return

  window.addEventListener('keydown', handleCaptureKeydown, true)
  onCleanup(() => window.removeEventListener('keydown', handleCaptureKeydown, true))
})
</script>

<template>
  <UModal v-model:open="isOpen" :ui="{ content: 'sm:max-w-6xl' }">
    <template #content>
      <UCard class="max-h-[88vh] overflow-hidden">
        <template #header>
          <div class="flex flex-col gap-4">
            <div class="flex items-start justify-between gap-4">
              <div class="space-y-1">
                <div class="flex items-center gap-2">
                  <UIcon name="i-lucide-sliders-horizontal" class="size-5 text-primary" />
                  <h2 class="text-lg font-semibold text-highlighted">
                    Shortcut Settings
                  </h2>
                </div>
                <p class="text-sm text-muted">
                  Customize the editor keymap per user. Conflicts are blocked inside the same scope.
                </p>
              </div>

              <div class="flex items-center gap-2">
                <UButton
                  color="neutral"
                  variant="soft"
                  size="sm"
                  label="Reset all"
                  :disabled="!hasChanges"
                  @click="handleResetAll"
                />
                <UButton
                  icon="i-lucide-x"
                  color="neutral"
                  variant="ghost"
                  size="sm"
                  aria-label="Close"
                  @click="isOpen = false"
                />
              </div>
            </div>

            <div class="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto_auto] lg:items-center">
              <UInput
                v-model="searchQuery"
                icon="i-lucide-search"
                placeholder="Search shortcuts, commands, or bindings"
                size="lg"
              />

              <UBadge color="neutral" variant="subtle" size="sm">
                {{ visibleShortcutCount }} commands
              </UBadge>

              <UBadge :color="hasConflicts ? 'error' : 'success'" variant="subtle" size="sm">
                {{ hasConflicts ? 'Conflicts detected' : 'Ready to save' }}
              </UBadge>
            </div>

            <div
              v-if="captureState.commandId || captureError"
              class="flex flex-col gap-2 rounded-lg border border-default bg-elevated px-4 py-3 text-sm"
            >
              <div class="flex items-center justify-between gap-3">
                <div class="flex items-center gap-2">
                  <UIcon name="i-lucide-keyboard" class="size-4 text-primary" />
                  <span class="font-medium text-highlighted">
                    {{ captureState.commandId ? SHORTCUT_DEFINITIONS[captureState.commandId].description : 'Shortcut capture' }}
                  </span>
                </div>

                <UButton
                  color="neutral"
                  variant="ghost"
                  size="xs"
                  label="Cancel"
                  @click="stopCapture"
                />
              </div>

              <p class="text-muted">
                Press the new shortcut now. Reserved browser shortcuts are rejected automatically.
              </p>
              <p v-if="captureError" class="text-error">
                {{ captureError }}
              </p>
            </div>
          </div>
        </template>

        <div class="max-h-[64vh] overflow-y-auto pr-1">
          <div v-if="visibleShortcutGroups.length > 0" class="grid grid-cols-1 gap-4">
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
                  class="flex flex-col gap-4 px-4 py-4 xl:flex-row xl:items-start xl:justify-between"
                >
                  <div class="min-w-0 space-y-2">
                    <div class="flex flex-wrap items-center gap-2">
                      <p class="text-sm font-medium text-highlighted">
                        {{ shortcut.definition.description }}
                      </p>
                      <UBadge color="neutral" variant="soft" size="xs">
                        {{ describeScope(shortcut.id) }}
                      </UBadge>
                      <UBadge
                        v-if="shortcut.isCustomized"
                        color="primary"
                        variant="subtle"
                        size="xs"
                      >
                        Custom
                      </UBadge>
                    </div>

                    <p v-if="shortcut.conflicts.length > 0" class="text-xs text-error">
                      Conflicts with another {{ describeScope(shortcut.id).toLowerCase() }} shortcut: {{ shortcut.conflicts.join(', ') }}
                    </p>
                  </div>

                  <div class="flex min-w-0 flex-1 flex-col gap-3 xl:max-w-[65%]">
                    <div class="flex flex-wrap gap-2">
                      <div
                        v-for="(binding, index) in shortcut.bindings"
                        :key="`${shortcut.id}-${binding}-${index}`"
                        class="flex items-center gap-2 rounded-lg border border-default bg-elevated px-2 py-2"
                      >
                        <button
                          type="button"
                          class="flex items-center gap-1.5 rounded-md px-1 py-0.5 text-left transition hover:bg-muted"
                          @click="beginReplaceCapture(shortcut.id, index)"
                        >
                          <template v-if="isCapturing(shortcut.id, index)">
                            <span class="text-xs font-medium text-primary">Press keys...</span>
                          </template>
                          <template v-else>
                            <template v-for="(kbd, kbdIndex) in getShortcutKbds(binding)" :key="`${shortcut.id}-${binding}-${kbd}-${kbdIndex}`">
                              <span v-if="kbdIndex > 0" class="text-[11px] text-muted">+</span>
                              <UKbd :value="kbd" size="sm" variant="subtle" />
                            </template>
                          </template>
                        </button>

                        <UButton
                          color="neutral"
                          variant="ghost"
                          size="xs"
                          icon="i-lucide-x"
                          aria-label="Remove binding"
                          :disabled="shortcut.bindings.length <= 1"
                          @click="handleRemoveBinding(shortcut.id, index)"
                        />
                      </div>
                    </div>

                    <div class="flex flex-wrap items-center gap-2">
                      <UButton
                        color="neutral"
                        variant="soft"
                        size="xs"
                        :label="isCapturingAppend(shortcut.id) ? 'Press keys...' : 'Add shortcut'"
                        icon="i-lucide-plus"
                        @click="beginAppendCapture(shortcut.id)"
                      />
                      <UButton
                        color="neutral"
                        variant="ghost"
                        size="xs"
                        label="Reset command"
                        :disabled="!shortcut.isCustomized"
                        @click="handleResetCommand(shortcut.id)"
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
                No commands match your search
              </p>
              <p class="text-sm text-muted">
                Try a tool name, action, or binding like <span class="font-medium text-highlighted">meta s</span>.
              </p>
            </div>
          </div>
        </div>

        <template #footer>
          <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p class="text-xs text-muted">
              The first binding is used for toolbar tooltips. Commands keep their default shortcut until you override them here.
            </p>

            <div class="flex items-center gap-2">
              <UButton
                color="neutral"
                variant="ghost"
                size="sm"
                label="Cancel"
                @click="isOpen = false"
              />
              <UButton
                color="primary"
                size="sm"
                label="Save shortcuts"
                :disabled="hasConflicts || !hasChanges"
                @click="saveAndClose"
              />
            </div>
          </div>
        </template>
      </UCard>
    </template>
  </UModal>
</template>
