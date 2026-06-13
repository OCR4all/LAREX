<script setup lang="ts">
import { LazyShareSlideover, LazyUiDeleteSlideover } from '#components'
import type { DropdownMenuItem } from '@nuxt/ui'
import type { NormalizationProfile, NormalizationProfileCreateOrUpdateRequest, NormalizationReplacementRule } from '@/types/normalization-profile'
import { DEFAULT_RESOURCE_CAPABILITIES, type ResourceCapabilities } from '@/types/capabilities'

type UnicodeNormalizationForm = 'NFC' | 'NFD' | 'NFKC' | 'NFKD'
type ReplacementMode = 'plain' | 'regex'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const backgroundDownloads = useBackgroundDownloads()
const { allow } = useActionVisibility()
const overlay = useOverlay()
const shareSlideover = overlay.create(LazyShareSlideover)
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)

const { selectedWorkspace } = await useWorkspaceBootstrap()
const workspaceId = computed(() => selectedWorkspace.value ?? '')

const id = route.params.id as string
const isNew = id === 'new'
const profileKey = computed(() => wsKey(workspaceId.value, 'normalization-profiles', id))
const profilesListKey = computed(() => wsKey(workspaceId.value, 'normalization-profiles', 'list'))

const defaultProfile: NormalizationProfile = {
  id: '',
  name: 'New Normalization Profile',
  description: '',
  tags: [],
  unicodeNormalization: 'NONE',
  collapseWhitespace: false,
  trimText: false,
  dehyphenateLineBreaks: false,
  mapLongSToS: false,
  expandCommonLigatures: false,
  normalizeQuotes: false,
  normalizeDashes: false,
  normalizeEllipsis: false,
  replacementRules: [],
  created: '',
  updated: ''
}

const loadedCapabilities = ref<ResourceCapabilities | null>(null)
let initial = defaultProfile

if (!isNew) {
  const { data, error } = await useFetch<NormalizationProfile>(() => `/api/workspaces/${workspaceId.value}/normalization-profiles/${id}`, {
    key: profileKey
  })
  if (data.value) {
    initial = data.value
    loadedCapabilities.value = data.value.capabilities ?? null
  } else if (error.value) {
    toast.add({ title: 'Error loading normalization profile', color: 'error' })
    router.push('/normalization-profiles')
  }
}

const profileCapabilities = computed(() => ({
  ...DEFAULT_RESOURCE_CAPABILITIES,
  ...(loadedCapabilities.value ?? {})
}))
const canEditProfile = computed(() => isNew || allow(profileCapabilities.value.canEdit))
const canShareProfile = computed(() => !isNew && allow(profileCapabilities.value.canShare))
const canDeleteProfile = computed(() => !isNew && allow(profileCapabilities.value.canDelete))

const isSaving = ref(false)
const isDeleting = ref(false)
const isExporting = ref(false)

const name = ref(initial.name)
const description = ref(initial.description ?? '')
const tags = ref<string[]>([...(initial.tags ?? [])])
const unicodeNormalization = ref(initial.unicodeNormalization || 'NFC')
const collapseWhitespace = ref(Boolean(initial.collapseWhitespace))
const trimText = ref(Boolean(initial.trimText))
const dehyphenateLineBreaks = ref(Boolean(initial.dehyphenateLineBreaks))
const mapLongSToS = ref(Boolean(initial.mapLongSToS))
const expandCommonLigatures = ref(Boolean(initial.expandCommonLigatures))
const normalizeQuotes = ref(Boolean(initial.normalizeQuotes))
const normalizeDashes = ref(Boolean(initial.normalizeDashes))
const normalizeEllipsis = ref(Boolean(initial.normalizeEllipsis))
const replacementRules = ref<NormalizationReplacementRule[]>((initial.replacementRules ?? []).map(rule => ({
  search: rule.search ?? '',
  replacement: rule.replacement ?? '',
  regex: Boolean(rule.regex)
})))
const previewInput = ref('ſæ ligature — “Quoted” text...\nLine-\nbreak   spacing')
const activeTab = ref<'rules' | 'preview'>('rules')
const tabs = [
  { label: 'Rules', value: 'rules', slot: 'rules', icon: 'i-lucide-list-tree' },
  { label: 'Preview', value: 'preview', slot: 'preview', icon: 'i-lucide-scan-eye' }
]

const presetRuleOptions: Array<{ key: Exclude<NormalizationPresetRuleKey, 'unicodeNormalization'>, label: string, model: Ref<boolean> }> = [
  { key: 'collapseWhitespace', label: 'Collapse whitespace', model: collapseWhitespace },
  { key: 'trimText', label: 'Trim text', model: trimText },
  { key: 'dehyphenateLineBreaks', label: 'Dehyphenate line breaks', model: dehyphenateLineBreaks },
  { key: 'mapLongSToS', label: 'Map long s to s', model: mapLongSToS },
  { key: 'expandCommonLigatures', label: 'Expand common ligatures', model: expandCommonLigatures },
  { key: 'normalizeQuotes', label: 'Normalize quotes', model: normalizeQuotes },
  { key: 'normalizeDashes', label: 'Normalize dashes', model: normalizeDashes },
  { key: 'normalizeEllipsis', label: 'Normalize ellipsis', model: normalizeEllipsis }
]

const breadcrumbItems = computed(() => [
  { label: 'Home', icon: 'i-lucide-home', to: '/' },
  { label: 'Normalization Profiles', icon: 'i-lucide-wand-sparkles', to: '/normalization-profiles' },
  { label: isNew ? 'New Profile' : (name.value || 'Edit Profile') }
])

const enabledTransformationCount = computed(() => [
  collapseWhitespace.value,
  trimText.value,
  dehyphenateLineBreaks.value,
  mapLongSToS.value,
  expandCommonLigatures.value,
  normalizeQuotes.value,
  normalizeDashes.value,
  normalizeEllipsis.value,
  replacementRules.value.length > 0
].filter(Boolean).length + (unicodeNormalization.value === 'NONE' ? 0 : 1))

const actionItems = computed<DropdownMenuItem[]>(() => {
  const items: DropdownMenuItem[] = []

  if (!isNew) {
    items.push({
      label: 'Export package',
      icon: 'i-lucide-download',
      disabled: isExporting.value,
      onSelect: exportProfile
    })
  }

  if (canShareProfile.value) {
    items.push({
      label: 'Share profile',
      icon: 'i-lucide-share-2',
      disabled: isExporting.value || isDeleting.value,
      onSelect: () => shareSlideover.open({
        resourceId: id,
        resourceName: name.value,
        resourceType: 'NORMALIZATION_PROFILE',
        currentWorkspaceId: workspaceId.value
      })
    })
  }

  if (canDeleteProfile.value) {
    items.push({
      label: 'Delete profile',
      icon: 'i-lucide-trash',
      color: 'error' as const,
      disabled: isDeleting.value || isSaving.value,
      onSelect: deleteProfile
    })
  }

  return items
})

function createReplacementRule(): NormalizationReplacementRule {
  return {
    search: '',
    replacement: '',
    regex: false
  }
}

function addReplacementRule() {
  replacementRules.value = [...replacementRules.value, createReplacementRule()]
}

function removeReplacementRule(index: number) {
  replacementRules.value = replacementRules.value.filter((_, ruleIndex) => ruleIndex !== index)
}

function replacementMode(rule: NormalizationReplacementRule): ReplacementMode {
  return rule.regex ? 'regex' : 'plain'
}

function updateReplacementMode(rule: NormalizationReplacementRule, value: string) {
  rule.regex = value === 'regex'
}

const previewOutput = computed(() => {
  let output = previewInput.value

  if (dehyphenateLineBreaks.value) {
    output = output.replace(/([\p{L}\p{N}])[-‐‑‒–—]\s*\r?\n\s*([\p{L}\p{N}])/gu, '$1$2')
  }

  if (unicodeNormalization.value !== 'NONE') {
    output = output.normalize(unicodeNormalization.value as UnicodeNormalizationForm)
  }

  if (expandCommonLigatures.value) {
    const ligatures = new Map([
      ['ﬀ', 'ff'],
      ['ﬁ', 'fi'],
      ['ﬂ', 'fl'],
      ['ﬃ', 'ffi'],
      ['ﬄ', 'ffl'],
      ['ﬅ', 'st'],
      ['ﬆ', 'st']
    ])
    for (const [source, target] of ligatures.entries()) {
      output = output.replaceAll(source, target)
    }
  }

  if (mapLongSToS.value) {
    output = output.replaceAll('ſ', 's').replaceAll('ẜ', 's')
  }

  if (normalizeQuotes.value) {
    const quoteMap = new Map([
      ['“', '"'],
      ['”', '"'],
      ['„', '"'],
      ['‟', '"'],
      ['«', '"'],
      ['»', '"'],
      ['’', '\''],
      ['‘', '\''],
      ['‚', '\''],
      ['‛', '\'']
    ])
    for (const [source, target] of quoteMap.entries()) {
      output = output.replaceAll(source, target)
    }
  }

  if (normalizeDashes.value) {
    output = output.replace(/[‐‑‒–—]/g, '-')
  }

  if (normalizeEllipsis.value) {
    output = output.replace(/\u2026/g, '...')
  }

  for (const rule of replacementRules.value) {
    if (!rule.search.trim()) {
      continue
    }

    try {
      output = rule.regex
        ? output.replace(new RegExp(rule.search, 'g'), rule.replacement ?? '')
        : output.replaceAll(rule.search, rule.replacement ?? '')
    } catch {
      continue
    }
  }

  if (collapseWhitespace.value) {
    output = output.replace(/\s+/g, ' ')
  }

  if (trimText.value) {
    output = output.trim()
  }

  return output
})

function buildBody(): NormalizationProfileCreateOrUpdateRequest {
  return {
    name: name.value.trim(),
    description: description.value.trim() || null,
    tags: tags.value,
    unicodeNormalization: unicodeNormalization.value,
    collapseWhitespace: collapseWhitespace.value,
    trimText: trimText.value,
    dehyphenateLineBreaks: dehyphenateLineBreaks.value,
    mapLongSToS: mapLongSToS.value,
    expandCommonLigatures: expandCommonLigatures.value,
    normalizeQuotes: normalizeQuotes.value,
    normalizeDashes: normalizeDashes.value,
    normalizeEllipsis: normalizeEllipsis.value,
    replacementRules: replacementRules.value.map(rule => ({
      search: rule.search,
      replacement: rule.replacement,
      regex: rule.regex
    }))
  }
}

async function saveProfile() {
  if (isSaving.value) return

  try {
    isSaving.value = true
    if (isNew) {
      const created = await $fetch<NormalizationProfile>(`/api/workspaces/${workspaceId.value}/normalization-profiles`, {
        method: 'POST',
        body: buildBody()
      })
      await refreshNuxtData(profilesListKey.value)
      toast.add({ title: 'Normalization profile created', color: 'success' })
      await navigateTo(`/normalization-profiles/${created.id}`)
      return
    }

    await $fetch(`/api/workspaces/${workspaceId.value}/normalization-profiles/${id}`, {
      method: 'PUT',
      body: buildBody()
    })
    await Promise.all([refreshNuxtData(profileKey.value), refreshNuxtData(profilesListKey.value)])
    toast.add({ title: 'Normalization profile saved', color: 'success' })
  } catch (error: unknown) {
    toast.add({ title: 'Failed to save normalization profile', description: extractApiErrorMessage(error, 'Failed to save normalization profile'), color: 'error' })
  } finally {
    isSaving.value = false
  }
}

async function deleteProfile() {
  if (isDeleting.value) return
  const instance = deleteSlideover.open({
    name: name.value,
    entityType: 'Normalization Profile',
    warningMessage: 'This action cannot be undone.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    isDeleting.value = true
    await $fetch(`/api/workspaces/${workspaceId.value}/normalization-profiles/${id}`, { method: 'DELETE' })
    await Promise.all([refreshNuxtData(profileKey.value), refreshNuxtData(profilesListKey.value)])
    toast.add({ title: 'Normalization profile deleted', color: 'success' })
    await navigateTo('/normalization-profiles')
  } catch (error: unknown) {
    toast.add({ title: 'Failed to delete normalization profile', description: extractApiErrorMessage(error, 'Failed to delete normalization profile'), color: 'error' })
  } finally {
    isDeleting.value = false
  }
}

async function exportProfile() {
  if (isExporting.value || isNew) return
  try {
    isExporting.value = true
    await backgroundDownloads.runBackgroundJob({
      title: 'Exporting normalization profile',
      subtitle: name.value || 'Normalization profile',
      statusLabel: 'Generating',
      completedLabel: 'Exported',
      icon: 'i-lucide-list-restart',
      task: async (job) => {
        const response = await fetch(`/api/workspaces/${workspaceId.value}/toolkit/export`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ selectors: [{ type: 'NORMALIZATION_PROFILE', ids: [id] }] })
        })
        if (!response.ok) throw new Error(`Export failed (${response.status})`)
        await backgroundDownloads.downloadBlobResponse(response, `${name.value || 'normalization-profile'}.larex-toolkit.json`, job)
      }
    })
    toast.add({ title: 'Normalization profile exported', color: 'success' })
  } catch (error: unknown) {
    toast.add({ title: 'Failed to export normalization profile', description: extractApiErrorMessage(error, 'Failed to export normalization profile'), color: 'error' })
  } finally {
    isExporting.value = false
  }
}
</script>

<template>
  <UDashboardPanel id="normalization-profile-detail" :ui="{ body: 'p-0 sm:p-0' }">
    <template #header>
      <UDashboardNavbar :title="isNew ? 'Create Normalization Profile' : 'Edit Normalization Profile'">
        <template #leading>
          <LazyUDashboardSidebarCollapse />
        </template>
        <template #right>
          <div class="flex items-center gap-2">
            <ToolkitHelpPopover
              title="About Normalization Profiles"
              description="Normalization profiles define reusable cleanup passes that can run before dictionary checks, search preparation, and export."
              :items="[
                'Assign a profile at workspace level to make it the default for new projects.',
                'Use project-level assignment when a specific corpus needs its own normalization behavior.',
                'Keep normalization above the codec layer and below exact-form dictionary matching.',
                'Manual replacement rules run in order and can be defined as plain-string or regex replacements.'
              ]"
            />
            <UFieldGroup>
              <UButton
                label="Save"
                color="neutral"
                variant="outline"
                icon="i-lucide-save"
                :loading="isSaving"
                :disabled="!canEditProfile"
                @click="saveProfile"
              />

              <UDropdownMenu v-if="actionItems.length > 0" :items="actionItems" :content="{ align: 'end' }">
                <UButton
                  color="neutral"
                  variant="outline"
                  icon="i-lucide-chevron-down"
                  :loading="isDeleting || isExporting"
                />
              </UDropdownMenu>
            </UFieldGroup>
          </div>
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <UBreadcrumb :items="breadcrumbItems" />
        </template>
        <template #right>
          <UTabs
            v-model="activeTab"
            :items="tabs"
            :content="false"
            :ui="{ root: 'gap-0' }"
          />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="h-full flex overflow-hidden">
        <aside class="w-80 shrink-0 border-r border-neutral-200 dark:border-neutral-700 bg-neutral-50/30 dark:bg-neutral-800/50 overflow-y-auto">
          <div class="p-4 lg:p-5 space-y-5">
            <UFormField label="Name" required>
              <UInput v-model="name" :disabled="!canEditProfile || isSaving" />
            </UFormField>

            <UFormField label="Description">
              <UTextarea v-model="description" :rows="4" :disabled="!canEditProfile || isSaving" />
            </UFormField>

            <UFormField label="Tags">
              <UInputTags v-model="tags" :disabled="!canEditProfile || isSaving" />
            </UFormField>

            <UFormField>
              <template #label>
                <div class="flex items-center gap-1">
                  <span>Unicode Normalization</span>
                  <NormalizationPresetRuleHelpPopover rule-key="unicodeNormalization" />
                </div>
              </template>
              <USelect v-model="unicodeNormalization" :items="['NFC', 'NFD', 'NFKC', 'NFKD', 'NONE']" :disabled="!canEditProfile || isSaving" />
            </UFormField>
          </div>
        </aside>

        <section class="flex-1 bg-neutral-50/70 dark:bg-neutral-900 flex flex-col min-w-0 overflow-hidden">
          <div class="flex items-center justify-between gap-3 border-b border-default px-4 py-3 lg:px-5">
            <div class="min-w-0">
              <h2 class="text-base font-semibold truncate">
                Normalization Rules
              </h2>
              <p class="text-sm text-muted">
                Configure preset cleanup passes and ordered manual replacements.
              </p>
            </div>
            <UBadge :color="enabledTransformationCount > 0 ? 'primary' : 'neutral'" variant="soft">
              {{ enabledTransformationCount }} active
            </UBadge>
          </div>

          <div class="flex-1 min-h-0 overflow-y-auto p-4 lg:p-5">
            <div v-if="activeTab === 'rules'" class="space-y-5 p-1">
              <UPageCard title="Preset Rules" variant="subtle">
                <div class="space-y-4">
                  <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                    <div
                      v-for="rule in presetRuleOptions"
                      :key="rule.key"
                      class="flex items-center gap-1"
                    >
                      <UCheckbox
                        :model-value="rule.model.value"
                        :disabled="!canEditProfile || isSaving"
                        :label="rule.label"
                        @update:model-value="rule.model.value = Boolean($event)"
                      />
                      <NormalizationPresetRuleHelpPopover :rule-key="rule.key" />
                    </div>
                  </div>
                </div>
              </UPageCard>

              <UPageCard
                title="Manual Replacement Rules"
                description="Ordered rules run after built-in character and punctuation normalization."
                variant="subtle"
              >
                <div class="space-y-4">
                  <div class="flex items-center justify-between gap-3">
                    <p class="text-sm text-muted">
                      Use plain replacements for direct substitutions and regex rules for structural patterns.
                    </p>
                    <UButton
                      icon="i-lucide-plus"
                      color="neutral"
                      variant="outline"
                      size="sm"
                      :disabled="!canEditProfile || isSaving"
                      @click="addReplacementRule"
                    >
                      Add Rule
                    </UButton>
                  </div>

                  <div v-if="replacementRules.length === 0" class="rounded-lg border border-dashed border-default p-4 text-sm text-muted">
                    No manual replacement rules configured.
                  </div>

                  <div
                    v-for="(rule, index) in replacementRules"
                    :key="`${index}-${rule.search}-${rule.replacement}`"
                    class="rounded-lg border border-default p-4 space-y-3"
                  >
                    <div class="flex items-center justify-between gap-3">
                      <span class="text-sm font-medium">Rule {{ index + 1 }}</span>
                      <UButton
                        icon="i-lucide-trash"
                        color="error"
                        variant="ghost"
                        :disabled="!canEditProfile || isSaving"
                        @click="removeReplacementRule(index)"
                      />
                    </div>

                    <UFormField label="Mode">
                      <USelect
                        :model-value="replacementMode(rule)"
                        :items="[
                          { label: 'Plain text', value: 'plain' },
                          { label: 'Regex', value: 'regex' }
                        ]"
                        :disabled="!canEditProfile || isSaving"
                        @update:model-value="updateReplacementMode(rule, String($event))"
                      />
                    </UFormField>

                    <div class="grid gap-3 md:grid-cols-2">
                      <UFormField :label="rule.regex ? 'Pattern' : 'Search'">
                        <UInput v-model="rule.search" :disabled="!canEditProfile || isSaving" />
                      </UFormField>
                      <UFormField label="Replacement">
                        <UInput v-model="rule.replacement" :disabled="!canEditProfile || isSaving" />
                      </UFormField>
                    </div>
                  </div>
                </div>
              </UPageCard>
            </div>

            <div v-else-if="activeTab === 'preview'" class="space-y-5 p-1">
              <UPageCard title="Input Sample" variant="subtle">
                <UTextarea
                  v-model="previewInput"
                  :rows="8"
                  :disabled="isSaving"
                  placeholder="Paste representative source text here..."
                  class="font-junicode"
                />
              </UPageCard>

              <UPageCard title="Normalized Output" variant="subtle">
                <UTextarea
                  :model-value="previewOutput"
                  :rows="8"
                  disabled
                  class="font-junicode"
                />
              </UPageCard>
            </div>
          </div>
        </section>
      </div>
    </template>
  </UDashboardPanel>
</template>
