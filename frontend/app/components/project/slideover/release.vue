<script setup lang="ts">
import * as z from 'zod'
import type { FormSubmitEvent } from '@nuxt/ui'
import { LazyProjectSlideoverExportTarget, LazyUiConfirmSlideover } from '#components'
import type {
  ProjectPackageCreateReleaseRequest,
  ProjectPackageEmbeddedOutputRequest,
  ProjectPackageRelease,
  ProjectReleaseDocxOptions,
  ProjectReleaseExportFormat,
  ProjectReleaseSpreadsheetProfile
} from '@/types/project-package-release'

type ExportDialogResult = {
  targetPageXmlVersion: string
  embeddedOutputs: ProjectPackageEmbeddedOutputRequest[]
}

const PAGE_XML_PRIMARY_VERSION = '2019-07-15'

const props = defineProps<{
  projectId: string
  suggestedTag?: string
}>()

const emit = defineEmits<{ close: [string | null] }>()

const toast = useToast()
const overlay = useOverlay()
const exportTargetSlideover = overlay.create(LazyProjectSlideoverExportTarget)
const confirmSlideover = overlay.create(LazyUiConfirmSlideover)
const { selectedWorkspace } = await useWorkspaceBootstrap()

const schema = z.object({
  versionTag: z.string().max(128, { error: 'Release tag is too long' }).optional().or(z.literal('')),
  notes: z.string().max(4000, { error: 'Release notes are too long' }).optional().or(z.literal(''))
})

type Schema = z.output<typeof schema>

const state = reactive<Schema>({
  versionTag: props.suggestedTag || '',
  notes: ''
})

const formRef = ref<HTMLFormElement | null>(null)
const creating = ref(false)
const options = ref<ExportDialogResult>({
  targetPageXmlVersion: PAGE_XML_PRIMARY_VERSION,
  embeddedOutputs: []
})

const submit = () => formRef.value?.submit()

const embeddedSummary = computed(() => {
  if (options.value.embeddedOutputs.length === 0) return 'No embedded outputs'
  return options.value.embeddedOutputs.map(output => formatLabel(output.format)).join(', ')
})

async function openPackageOptions() {
  const selector = exportTargetSlideover.open({
    mode: 'package',
    title: 'Release Package Options',
    description: 'Releases always freeze the full project. Choose the PAGE XML target and optional embedded outputs.',
    initialTargetVersion: options.value.targetPageXmlVersion,
    initialEmbeddedFormats: options.value.embeddedOutputs.map(output => output.format),
    initialEmbeddedTxtPageDelimiters: textOutput()?.includePageDelimiters ?? false,
    initialEmbeddedTxtTextLevel: textOutput()?.textLevel ?? 'PAGE',
    initialEmbeddedTxtTextVariantIndex: Number.isFinite(textOutput()?.textVariantIndex) ? Number(textOutput()?.textVariantIndex) : 0,
    initialPdfProfile: pdfOutput()?.pdfProfile ?? 'SEARCHABLE',
    initialTeiProfile: teiOutput()?.teiProfile ?? 'STANDARD',
    initialSpreadsheetProfiles: spreadsheetOutput()?.spreadsheetProfiles ?? ['PAGE_METADATA'],
    initialDocxOptions: normalizeDocxOptions(docxOutput()?.docxOptions) ?? {
      preserveLineBreaks: true,
      forcePageBreaks: true,
      includeImageNames: false,
      markUnclearWords: false
    },
    confirmLabel: 'Use Options'
  })

  const result = await selector.result as {
    targetPageXmlVersion: string
    embeddedOutputs: ProjectPackageEmbeddedOutputRequest[]
  } | null

  if (!result) return
  options.value = {
    targetPageXmlVersion: result.targetPageXmlVersion || PAGE_XML_PRIMARY_VERSION,
    embeddedOutputs: normalizeEmbeddedOutputs(result.embeddedOutputs)
  }
}

function textOutput() {
  return options.value.embeddedOutputs.find(output => output.format === 'TXT')
}

function pdfOutput() {
  return options.value.embeddedOutputs.find(output => output.format === 'PDF')
}

function teiOutput() {
  return options.value.embeddedOutputs.find(output => output.format === 'TEI')
}

function spreadsheetOutput() {
  return options.value.embeddedOutputs.find(output => output.format === 'CSV' || output.format === 'XLSX')
}

function docxOutput() {
  return options.value.embeddedOutputs.find(output => output.format === 'DOCX')
}

function formatLabel(format: ProjectReleaseExportFormat) {
  return format.replaceAll('_', ' ')
}

function normalizeEmbeddedOutputs(outputs: ProjectPackageEmbeddedOutputRequest[]) {
  return outputs.map(output => ({
    format: output.format,
    includePageDelimiters: output.includePageDelimiters,
    textLevel: output.textLevel,
    textVariantIndex: Number.isFinite(output.textVariantIndex) ? Number(output.textVariantIndex) : 0,
    pdfProfile: output.pdfProfile,
    teiProfile: output.teiProfile,
    spreadsheetProfiles: normalizeSpreadsheetProfiles(output.spreadsheetProfiles),
    docxOptions: normalizeDocxOptions(output.docxOptions)
  }))
}

function normalizeSpreadsheetProfiles(profiles?: ProjectReleaseSpreadsheetProfile[] | null) {
  return Array.isArray(profiles) ? profiles : undefined
}

function normalizeDocxOptions(options?: ProjectReleaseDocxOptions | null) {
  if (!options) return undefined
  return {
    preserveLineBreaks: options.preserveLineBreaks ?? true,
    forcePageBreaks: options.forcePageBreaks ?? true,
    includeImageNames: options.includeImageNames ?? false,
    markUnclearWords: options.markUnclearWords ?? false
  }
}

async function confirmLegacyPageXmlVersion(): Promise<boolean> {
  if (options.value.targetPageXmlVersion === PAGE_XML_PRIMARY_VERSION) {
    return true
  }

  const confirmation = confirmSlideover.open({
    title: 'Confirm Legacy PAGE XML Export',
    message: 'Releasing with an older PAGE XML schema may drop PAGE 2019-only data. Continue anyway?',
    confirmLabel: 'Release anyway',
    confirmColor: 'warning',
    confirmIcon: 'i-lucide-triangle-alert'
  })

  return await confirmation.result as boolean
}

async function onSubmit(event: FormSubmitEvent<Schema>) {
  if (!selectedWorkspace.value) return
  if (!await confirmLegacyPageXmlVersion()) return

  creating.value = true
  const payload: ProjectPackageCreateReleaseRequest = {
    versionTag: event.data.versionTag?.trim() || null,
    notes: event.data.notes?.trim() || null,
    targetPageXmlVersion: options.value.targetPageXmlVersion,
    embeddedOutputs: options.value.embeddedOutputs
  }

  try {
    const release = await $fetch<ProjectPackageRelease>(`/api/workspaces/${selectedWorkspace.value}/projects/${props.projectId}/releases`, {
      method: 'POST',
      body: payload
    })

    toast.add({
      title: 'Release created',
      description: `${release.versionTag} is now frozen and downloadable.`,
      color: 'success'
    })

    emit('close', release.id)
  } catch (error: unknown) {
    toast.add({
      title: 'Release failed',
      description: extractApiErrorMessage(error, 'Failed to create release'),
      color: 'error'
    })
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <UiResponsiveSlideover
    :close="{ onClick: () => emit('close', null) }"
  >
    <template #header>
      <UiSlideoverHeader title="Create Release" icon="i-lucide-plus" />
    </template>

    <template #body>
      <div class="space-y-5">
        <UAlert
          color="primary"
          variant="soft"
          icon="i-lucide-box"
          title="Full-project snapshot"
          description="Releases always freeze the full project. Current page selection is ignored."
        />

        <UForm
          ref="formRef"
          :schema="schema"
          :state="state"
          class="space-y-5"
          @submit="onSubmit"
        >
          <UFormField
            label="Release tag"
            name="versionTag"
            hint="Leave blank to use the next version tag automatically."
          >
            <UInput
              v-model="state.versionTag"
              placeholder="e.g. v3 or training-baseline-2026-04"
            />
          </UFormField>

          <UFormField label="Release notes" name="notes">
            <UTextarea
              v-model="state.notes"
              :rows="6"
              placeholder="What changed in this release and what should downstream jobs know?"
            />
          </UFormField>
        </UForm>

        <section class="space-y-3 rounded-lg border border-default p-4">
          <div class="flex items-center justify-between gap-3">
            <div>
              <h3 class="font-medium text-highlighted">
                Package options
              </h3>
              <p class="text-sm text-muted">
                Target PAGE XML and optional embedded outputs for this frozen package.
              </p>
            </div>

            <UButton
              color="neutral"
              variant="outline"
              icon="i-lucide-sliders-horizontal"
              @click="openPackageOptions"
            >
              Configure
            </UButton>
          </div>

          <div class="grid gap-3 sm:grid-cols-2">
            <div class="rounded-lg border border-default p-3">
              <div class="text-xs uppercase tracking-wide text-muted">
                Target PAGE XML
              </div>
              <div class="mt-1 text-sm text-highlighted">
                {{ options.targetPageXmlVersion }}
              </div>
            </div>
            <div class="rounded-lg border border-default p-3">
              <div class="text-xs uppercase tracking-wide text-muted">
                Embedded outputs
              </div>
              <div class="mt-1 text-sm text-highlighted">
                {{ embeddedSummary }}
              </div>
            </div>
          </div>
        </section>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="emit('close', null)">
          Cancel
        </UButton>
        <UButton
          color="primary"
          icon="i-lucide-tag"
          :loading="creating"
          @click="submit"
        >
          Create Release
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
