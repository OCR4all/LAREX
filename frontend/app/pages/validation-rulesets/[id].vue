<script setup lang="ts">
import { LazyShareSlideover, LazyUiDeleteSlideover } from '#components'
import type { DropdownMenuItem } from '@nuxt/ui'
import type { ValidationRule, ValidationRuleset, ValidationRulesetCreateOrUpdateRequest, ValidationSeverity } from '@/types/validation-ruleset'
import { DEFAULT_RESOURCE_CAPABILITIES, type ResourceCapabilities } from '@/types/capabilities'

interface ValidationPreviewRuleResult {
  ruleId: string
  ruleName: string
  severity: ValidationSeverity
  message: string
  occurrenceCount: number
  matchedSamples: string[]
}

interface ValidationPreviewIssue {
  ruleId: string
  ruleName: string
  message: string
}

const route = useRoute()
const router = useRouter()
const toast = useToast()
const { allow } = useActionVisibility()
const overlay = useOverlay()
const shareSlideover = overlay.create(LazyShareSlideover)
const deleteSlideover = overlay.create(LazyUiDeleteSlideover)

const { selectedWorkspace } = await useWorkspaceBootstrap()
const workspaceId = computed(() => selectedWorkspace.value ?? '')

const id = route.params.id as string
const isNew = id === 'new'
const rulesetKey = computed(() => wsKey(workspaceId.value, 'validation-rulesets', id))
const rulesetsListKey = computed(() => wsKey(workspaceId.value, 'validation-rulesets', 'list'))

const emptyRule = (): ValidationRule => ({
  id: null,
  name: '',
  description: '',
  severity: 'WARNING',
  pattern: '',
  flags: '',
  message: ''
})

const defaultRuleset: ValidationRuleset = {
  id: '',
  name: 'New Validation Ruleset',
  description: '',
  tags: [],
  rules: [
    {
      id: 'repeated-punctuation',
      name: 'Repeated punctuation',
      description: 'Flags repeated punctuation sequences that often come from OCR noise.',
      severity: 'WARNING',
      pattern: '[!?.,;:]{2,}',
      flags: '',
      message: 'Repeated punctuation'
    }
  ],
  created: '',
  updated: ''
}

const loadedCapabilities = ref<ResourceCapabilities | null>(null)
let initial = defaultRuleset

if (!isNew) {
  const { data, error } = await useFetch<ValidationRuleset>(() => `/api/workspaces/${workspaceId.value}/validation-rulesets/${id}`, {
    key: rulesetKey
  })
  if (data.value) {
    initial = data.value
    loadedCapabilities.value = data.value.capabilities ?? null
  } else if (error.value) {
    toast.add({ title: 'Error loading validation ruleset', color: 'error' })
    router.push('/validation-rulesets')
  }
}

const rulesetCapabilities = computed(() => ({
  ...DEFAULT_RESOURCE_CAPABILITIES,
  ...(loadedCapabilities.value ?? {})
}))
const canEditRuleset = computed(() => isNew || allow(rulesetCapabilities.value.canEdit))
const canShareRuleset = computed(() => !isNew && allow(rulesetCapabilities.value.canShare))
const canDeleteRuleset = computed(() => !isNew && allow(rulesetCapabilities.value.canDelete))

const isSaving = ref(false)
const isDeleting = ref(false)
const isExporting = ref(false)
const activeTab = ref<'rules' | 'preview'>('rules')
const previewText = ref('Lorem ipsum??\n[unclear\nsic] and 1234.')

const name = ref(initial.name)
const description = ref(initial.description ?? '')
const tags = ref<string[]>([...(initial.tags ?? [])])
const rules = ref<ValidationRule[]>((initial.rules ?? []).map(rule => ({ ...rule })))
const tabs = [
  { label: 'Rules', icon: 'i-lucide-list-tree', value: 'rules' },
  { label: 'Preview', icon: 'i-lucide-scan-eye', value: 'preview' }
]

const breadcrumbItems = computed(() => [
  { label: 'Home', icon: 'i-lucide-home', to: '/' },
  { label: 'Validation Rulesets', icon: 'i-lucide-shield-alert', to: '/validation-rulesets' },
  { label: isNew ? 'New Ruleset' : (name.value || 'Edit Ruleset') }
])

const ruleCounts = computed(() => ({
  total: rules.value.length,
  errors: rules.value.filter(rule => rule.severity === 'ERROR').length,
  warnings: rules.value.filter(rule => rule.severity === 'WARNING').length,
  infos: rules.value.filter(rule => rule.severity === 'INFO').length
}))

const previewEvaluation = computed(() => {
  const results: ValidationPreviewRuleResult[] = []
  const issues: ValidationPreviewIssue[] = []

  for (const [index, rule] of rules.value.entries()) {
    const ruleId = rule.id || `rule-${index + 1}`
    const ruleName = rule.name?.trim() || `Rule ${index + 1}`
    const message = rule.message?.trim() || `Matched rule: ${ruleName}`
    const severity = (rule.severity || 'WARNING') as ValidationSeverity
    const normalizedFlags = (rule.flags || '').replace(/[\s,]+/g, '')
    const unsupportedFlags = Array.from(new Set(normalizedFlags.split('').filter(flag => flag && !['i', 'm', 's', 'u'].includes(flag))))

    if (!rule.pattern?.trim()) {
      issues.push({ ruleId, ruleName, message: 'Preview skipped because the regex pattern is empty.' })
      continue
    }

    if (unsupportedFlags.length > 0) {
      issues.push({
        ruleId,
        ruleName,
        message: `Preview does not support regex flag(s): ${unsupportedFlags.join(', ')}.`
      })
      continue
    }

    try {
      const flags = Array.from(new Set(`${normalizedFlags}g`.split(''))).join('')
      const regex = new RegExp(rule.pattern, flags)
      const matchedSamples: string[] = []
      let occurrenceCount = 0
      let match: RegExpExecArray | null

      match = regex.exec(previewText.value)
      while (match) {
        occurrenceCount += 1
        const sample = match[0]
        if (sample && matchedSamples.length < 5 && !matchedSamples.includes(sample)) {
          matchedSamples.push(sample)
        }
        if (sample === '') {
          regex.lastIndex += 1
        }
        match = regex.exec(previewText.value)
      }

      if (occurrenceCount > 0) {
        results.push({
          ruleId,
          ruleName,
          severity,
          message,
          occurrenceCount,
          matchedSamples
        })
      }
    } catch (error: unknown) {
      issues.push({
        ruleId,
        ruleName,
        message: error instanceof Error ? error.message : 'Invalid regex pattern.'
      })
    }
  }

  return {
    results,
    issues
  }
})

const previewTotalOccurrences = computed(() => previewEvaluation.value.results.reduce((count, result) => count + result.occurrenceCount, 0))
const previewIsValid = computed(() => previewTotalOccurrences.value === 0)

function severityColor(severity: ValidationSeverity): 'info' | 'warning' | 'error' | 'neutral' {
  switch (severity) {
    case 'ERROR':
      return 'error'
    case 'WARNING':
      return 'warning'
    case 'INFO':
      return 'info'
    default:
      return 'neutral'
  }
}

const actionItems = computed<DropdownMenuItem[]>(() => {
  const items: DropdownMenuItem[] = []

  if (!isNew) {
    items.push({
      label: 'Export package',
      icon: 'i-lucide-download',
      disabled: isExporting.value,
      onSelect: exportRuleset
    })
  }

  if (canShareRuleset.value) {
    items.push({
      label: 'Share ruleset',
      icon: 'i-lucide-share-2',
      disabled: isExporting.value || isDeleting.value,
      onSelect: () => shareSlideover.open({
        resourceId: id,
        resourceName: name.value,
        resourceType: 'VALIDATION_RULESET',
        currentWorkspaceId: workspaceId.value
      })
    })
  }

  if (canDeleteRuleset.value) {
    items.push({
      label: 'Delete ruleset',
      icon: 'i-lucide-trash',
      color: 'error' as const,
      disabled: isDeleting.value || isSaving.value,
      onSelect: deleteRuleset
    })
  }

  return items
})

function addRule() {
  rules.value = [...rules.value, emptyRule()]
}

function removeRule(index: number) {
  rules.value = rules.value.filter((_, ruleIndex) => ruleIndex !== index)
}

function buildBody(): ValidationRulesetCreateOrUpdateRequest {
  return {
    name: name.value.trim(),
    description: description.value.trim() || null,
    tags: tags.value,
    rules: rules.value.map((rule, index) => ({
      id: rule.id || `${name.value.trim().toLowerCase().replaceAll(/[^a-z0-9]+/g, '-') || 'rule'}-${index + 1}`,
      name: rule.name,
      description: rule.description || null,
      severity: rule.severity || 'WARNING',
      pattern: rule.pattern,
      flags: rule.flags || null,
      message: rule.message || null
    }))
  }
}

async function saveRuleset() {
  if (isSaving.value) return
  try {
    isSaving.value = true
    if (isNew) {
      const created = await $fetch<ValidationRuleset>(`/api/workspaces/${workspaceId.value}/validation-rulesets`, {
        method: 'POST',
        body: buildBody()
      })
      await refreshNuxtData(rulesetsListKey.value)
      toast.add({ title: 'Validation ruleset created', color: 'success' })
      await navigateTo(`/validation-rulesets/${created.id}`)
      return
    }

    await $fetch(`/api/workspaces/${workspaceId.value}/validation-rulesets/${id}`, {
      method: 'PUT',
      body: buildBody()
    })
    await Promise.all([refreshNuxtData(rulesetKey.value), refreshNuxtData(rulesetsListKey.value)])
    toast.add({ title: 'Validation ruleset saved', color: 'success' })
  } catch (error: unknown) {
    toast.add({ title: 'Failed to save validation ruleset', description: extractApiErrorMessage(error, 'Failed to save validation ruleset'), color: 'error' })
  } finally {
    isSaving.value = false
  }
}

async function deleteRuleset() {
  if (isDeleting.value) return
  const instance = deleteSlideover.open({
    name: name.value,
    entityType: 'Validation Ruleset',
    warningMessage: 'This action cannot be undone.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    isDeleting.value = true
    await $fetch(`/api/workspaces/${workspaceId.value}/validation-rulesets/${id}`, { method: 'DELETE' })
    await Promise.all([refreshNuxtData(rulesetKey.value), refreshNuxtData(rulesetsListKey.value)])
    toast.add({ title: 'Validation ruleset deleted', color: 'success' })
    await navigateTo('/validation-rulesets')
  } catch (error: unknown) {
    toast.add({ title: 'Failed to delete validation ruleset', description: extractApiErrorMessage(error, 'Failed to delete validation ruleset'), color: 'error' })
  } finally {
    isDeleting.value = false
  }
}

async function exportRuleset() {
  if (isExporting.value || isNew) return
  try {
    isExporting.value = true
    const response = await fetch(`/api/workspaces/${workspaceId.value}/toolkit/export`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ selectors: [{ type: 'VALIDATION_RULESET', ids: [id] }] })
    })
    if (!response.ok) throw new Error(`Export failed (${response.status})`)
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${name.value || 'validation-ruleset'}.larex-toolkit.json`
    link.click()
    window.URL.revokeObjectURL(url)
    toast.add({ title: 'Validation ruleset exported', color: 'success' })
  } catch (error: unknown) {
    toast.add({ title: 'Failed to export validation ruleset', description: extractApiErrorMessage(error, 'Failed to export validation ruleset'), color: 'error' })
  } finally {
    isExporting.value = false
  }
}
</script>

<template>
  <UDashboardPanel id="validation-ruleset-detail" :ui="{ body: 'p-0 sm:p-0' }">
    <template #header>
      <UDashboardNavbar :title="isNew ? 'Create Validation Ruleset' : 'Edit Validation Ruleset'">
        <template #leading>
          <LazyUDashboardSidebarCollapse />
        </template>
        <template #right>
          <div class="flex items-center gap-2">
            <ToolkitHelpPopover
              title="About Validation Rulesets"
              description="Validation rulesets define reusable QA checks for suspicious transcription patterns above the codec layer."
              :items="[
                'Assign a ruleset at workspace level to make it the default QA bundle for new projects.',
                'Use severities to separate informational editorial hints from blocking transcription issues.',
                'Keep regex patterns specific enough to reduce noisy matches on real project text.'
              ]"
            />
            <UFieldGroup>
              <UButton
                label="Save"
                color="neutral"
                variant="outline"
                icon="i-lucide-save"
                :loading="isSaving"
                :disabled="!canEditRuleset"
                @click="saveRuleset"
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
              <UInput v-model="name" :disabled="!canEditRuleset || isSaving" />
            </UFormField>

            <UFormField label="Description">
              <UTextarea v-model="description" :rows="4" :disabled="!canEditRuleset || isSaving" />
            </UFormField>

            <UFormField label="Tags">
              <UInputTags v-model="tags" :disabled="!canEditRuleset || isSaving" />
            </UFormField>

            <div class="border-t border-neutral-200 dark:border-neutral-700 pt-4 space-y-3 text-sm">
              <div class="flex items-center justify-between gap-3">
                <span class="text-muted">Rules</span>
                <span class="font-medium tabular-nums">{{ ruleCounts.total }}</span>
              </div>
              <div class="flex items-center justify-between gap-3">
                <span class="text-muted">Errors</span>
                <UBadge color="error" variant="soft">
                  {{ ruleCounts.errors }}
                </UBadge>
              </div>
              <div class="flex items-center justify-between gap-3">
                <span class="text-muted">Warnings</span>
                <UBadge color="warning" variant="soft">
                  {{ ruleCounts.warnings }}
                </UBadge>
              </div>
              <div class="flex items-center justify-between gap-3">
                <span class="text-muted">Infos</span>
                <UBadge color="info" variant="soft">
                  {{ ruleCounts.infos }}
                </UBadge>
              </div>
            </div>
          </div>
        </aside>

        <section class="flex-1 bg-neutral-50/70 dark:bg-neutral-900 flex flex-col min-w-0 overflow-hidden">
          <div class="flex items-center justify-between gap-3 border-b border-default px-4 py-3 lg:px-5">
            <div class="min-w-0">
              <h2 class="text-base font-semibold truncate">
                {{ activeTab === 'rules' ? 'Validation Rules' : 'Validation Preview' }}
              </h2>
              <p class="text-sm text-muted">
                {{ activeTab === 'rules'
                  ? 'Maintain the regex-based QA checks that will run against project text.'
                  : 'Test the current unsaved ruleset against sample text to see which rules match.' }}
              </p>
            </div>
            <UButton
              v-if="activeTab === 'rules'"
              icon="i-lucide-plus"
              color="neutral"
              variant="outline"
              :disabled="!canEditRuleset || isSaving"
              @click="addRule"
            >
              Add Rule
            </UButton>
          </div>

          <div class="flex-1 min-h-0 overflow-y-auto p-4 lg:p-5">
            <div v-if="activeTab === 'rules'" class="space-y-4">
              <div
                v-for="(rule, index) in rules"
                :key="rule.id || index"
                class="rounded-lg border border-default bg-default p-4 space-y-3"
              >
                <div class="flex items-start justify-between gap-3">
                  <UFormField label="Rule Name" class="flex-1">
                    <UInput v-model="rule.name" :disabled="!canEditRuleset || isSaving" />
                  </UFormField>
                  <UButton
                    icon="i-lucide-trash"
                    color="error"
                    variant="ghost"
                    :disabled="!canEditRuleset || isSaving || rules.length === 1"
                    @click="removeRule(index)"
                  />
                </div>

                <UFormField label="Description">
                  <UInput :model-value="rule.description ?? ''" :disabled="!canEditRuleset || isSaving" @update:model-value="rule.description = $event" />
                </UFormField>

                <div class="grid gap-3 md:grid-cols-[minmax(0,1fr)_180px_180px]">
                  <UFormField label="Regex Pattern">
                    <UInput v-model="rule.pattern" :disabled="!canEditRuleset || isSaving" />
                  </UFormField>
                  <UFormField label="Severity">
                    <USelect :model-value="rule.severity ?? 'WARNING'" :items="['INFO', 'WARNING', 'ERROR']" :disabled="!canEditRuleset || isSaving" @update:model-value="rule.severity = $event as ValidationSeverity" />
                  </UFormField>
                  <UFormField label="Flags">
                    <UInput :model-value="rule.flags ?? ''" placeholder="i, m, s, u, x" :disabled="!canEditRuleset || isSaving" @update:model-value="rule.flags = $event" />
                  </UFormField>
                </div>

                <UFormField label="Result Message">
                  <UInput :model-value="rule.message ?? ''" :disabled="!canEditRuleset || isSaving" @update:model-value="rule.message = $event" />
                </UFormField>
              </div>
            </div>

            <div v-else-if="activeTab === 'preview'" class="space-y-5">
              <UPageCard title="Input Sample" variant="subtle">
                <UTextarea
                  v-model="previewText"
                  :rows="8"
                  :disabled="isSaving"
                  placeholder="Paste representative project text here..."
                  class="font-junicode"
                />
              </UPageCard>

              <div class="grid gap-3 sm:grid-cols-3">
                <UPageCard title="Rules Matched" :description="String(previewEvaluation.results.length)" variant="subtle" />
                <UPageCard title="Occurrences" :description="String(previewTotalOccurrences)" variant="subtle" />
                <UPageCard title="Preview Issues" :description="String(previewEvaluation.issues.length)" variant="subtle" />
              </div>

              <UAlert
                :color="previewIsValid ? 'success' : 'warning'"
                variant="subtle"
                :title="previewIsValid ? 'No rules matched the preview text.' : 'Validation rules matched suspicious patterns.'"
                :description="previewEvaluation.issues.length > 0
                  ? 'Some rules could not be previewed exactly. Review the preview issues below.'
                  : 'Preview results reflect the current unsaved rules on this page.'"
              />

              <div v-if="previewEvaluation.issues.length > 0" class="space-y-3">
                <h3 class="text-sm font-semibold">
                  Preview Issues
                </h3>
                <div
                  v-for="issue in previewEvaluation.issues"
                  :key="`${issue.ruleId}-${issue.message}`"
                  class="rounded-lg border border-default p-3 space-y-1"
                >
                  <p class="font-medium">
                    {{ issue.ruleName }}
                  </p>
                  <p class="text-sm text-muted">
                    {{ issue.message }}
                  </p>
                </div>
              </div>

              <div v-if="previewEvaluation.results.length > 0" class="space-y-3">
                <h3 class="text-sm font-semibold">
                  Matched Rules
                </h3>
                <div
                  v-for="result in previewEvaluation.results"
                  :key="result.ruleId"
                  class="rounded-lg border border-default p-3 space-y-2"
                >
                  <div class="flex items-center justify-between gap-3">
                    <div>
                      <p class="font-medium">
                        {{ result.ruleName }}
                      </p>
                      <p class="text-xs text-muted">
                        {{ result.message }}
                      </p>
                    </div>
                    <div class="flex items-center gap-2">
                      <UBadge :color="severityColor(result.severity)" variant="soft">
                        {{ result.severity }}
                      </UBadge>
                      <UBadge color="neutral" variant="soft">
                        {{ result.occurrenceCount }} hit(s)
                      </UBadge>
                    </div>
                  </div>

                  <div v-if="result.matchedSamples.length > 0" class="space-y-2">
                    <p class="text-xs font-medium text-muted">
                      Sample Matches
                    </p>
                    <div class="flex flex-wrap gap-2">
                      <UBadge
                        v-for="sample in result.matchedSamples"
                        :key="`${result.ruleId}-${sample}`"
                        color="neutral"
                        variant="outline"
                      >
                        {{ sample }}
                      </UBadge>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>
    </template>
  </UDashboardPanel>
</template>
