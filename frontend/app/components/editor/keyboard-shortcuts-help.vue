<script setup lang="ts">
import {
  SHORTCUT_HELP_GROUPS,
  getShortcutKbds,
  type ShortcutCommandId,
  type ShortcutHelpGroupId,
  type ResolvedShortcutDefinition
} from '@/composables/editor/shortcut-registry'
import { useShortcutBindings } from '@/composables/editor/use-shortcut-bindings'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'customize'): void
}>()

const isOpen = computed({
  get: () => props.open,
  set: value => emit('update:open', value)
})

const searchQuery = ref('')
const { resolvedShortcutDefinitions } = useShortcutBindings()

type ShortcutCombo = {
  kbds: string[]
}

type ShortcutHelpItem = {
  id: ShortcutCommandId
  description: string
  group: ShortcutHelpGroupId
  combos: ShortcutCombo[]
  searchText: string
}

const allShortcutItems = computed<ShortcutHelpItem[]>(() => {
  return (Object.entries(resolvedShortcutDefinitions.value) as Array<[ShortcutCommandId, ResolvedShortcutDefinition]>)
    .filter(([, definition]) => definition.showInHelp !== false)
    .map(([id, definition]) => {
      const combos: ShortcutCombo[] = definition.bindings.map(binding => ({
        kbds: getShortcutKbds(binding)
      }))

      return {
        id,
        description: definition.description,
        group: definition.group,
        combos,
        searchText: [
          definition.description,
          definition.group,
          ...combos.flatMap(combo => [combo.kbds.join(' ')])
        ].join(' ').toLocaleLowerCase()
      }
    })
})

const normalizedSearchQuery = computed(() => searchQuery.value.trim().toLocaleLowerCase())

const visibleShortcutGroups = computed(() => {
  return SHORTCUT_HELP_GROUPS
    .map((group) => {
      const shortcuts = allShortcutItems.value.filter((shortcut) => {
        if (shortcut.group !== group.id) return false
        if (!normalizedSearchQuery.value) return true
        return shortcut.searchText.includes(normalizedSearchQuery.value)
      })

      return {
        ...group,
        shortcuts
      }
    })
    .filter(group => group.shortcuts.length > 0)
})

const totalShortcutCount = computed(() => allShortcutItems.value.length)
const visibleShortcutCount = computed(() =>
  visibleShortcutGroups.value.reduce((count, group) => count + group.shortcuts.length, 0)
)

function handleCustomize() {
  isOpen.value = false
  emit('customize')
}
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
                  icon="i-lucide-sliders-horizontal"
                  color="neutral"
                  variant="soft"
                  size="sm"
                  label="Customize"
                  @click="handleCustomize"
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

            <div class="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center">
              <UInput
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
                  class="flex flex-col gap-3 px-4 py-3 md:flex-row md:items-start md:justify-between"
                >
                  <div class="min-w-0 space-y-1">
                    <p class="text-sm font-medium text-highlighted">
                      {{ shortcut.description }}
                    </p>
                    <p v-if="shortcut.combos.length > 1" class="text-xs text-muted">
                      Alternative shortcuts are listed on the right.
                    </p>
                  </div>

                  <div class="flex flex-wrap items-center gap-2 md:max-w-[52%] md:justify-end">
                    <div
                      v-for="(combo, comboIndex) in shortcut.combos"
                      :key="`${shortcut.id}-${comboIndex}`"
                      class="flex items-center gap-1.5 rounded-md border border-default bg-elevated px-2 py-1"
                    >
                      <template v-for="(kbd, index) in combo.kbds" :key="`${shortcut.id}-${comboIndex}-${kbd}-${index}`">
                        <span v-if="index > 0" class="text-[11px] text-muted">+</span>
                        <UKbd :value="kbd" size="sm" variant="subtle" />
                      </template>
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
              <div class="flex items-center gap-1 rounded-md border border-default bg-elevated px-2 py-1">
                <UKbd value="shift" size="sm" variant="subtle" />
                <span class="text-[11px] text-muted">+</span>
                <UKbd value="/" size="sm" variant="subtle" />
              </div>
            </div>

            <div class="flex items-center gap-2">
              <span>Modifier labels adapt to your platform automatically.</span>
              <UButton
                color="neutral"
                variant="ghost"
                size="xs"
                label="Customize"
                @click="handleCustomize"
              />
            </div>
          </div>
        </template>
      </UCard>
    </template>
  </UModal>
</template>
