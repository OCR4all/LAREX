<script setup lang="ts">
type PdfFileInfo = {
  fileName: string
  defaultPrefix: string
}

type PdfPreflightInfo = {
  ready: boolean
  withinQuota: boolean
  quotaEnforced: boolean
  renderDpi: number
  pdfFileCount: number
  pdfPageCount: number
  renderedPixels: number
  estimatedPdfBytes: number
  estimatedStorageBytes: number
  reservedBytes: number
  availableBytes: number
  availableBytesAfterReservation: number
  message: string
}

type PdfUploadSettings = {
  prefixes: Record<string, string>
  renderDpi: number
}

const props = defineProps<{
  files: PdfFileInfo[]
  mode?: 'configure' | 'review'
  initialRenderDpi?: number
  initialPrefixes?: Record<string, string>
  preflight?: PdfPreflightInfo | null
  recalculate?: (renderDpi: number) => Promise<PdfPreflightInfo>
}>()

const emit = defineEmits<{
  close: [result: PdfUploadSettings | null]
}>()

const formId = useId()
type PdfPrefixState = {
  useFileName: boolean
  customPrefix: string
}

const stateByFileName = ref<Record<string, PdfPrefixState>>(
  Object.fromEntries(
    props.files.map(f => [
      f.fileName,
      {
        useFileName: !props.initialPrefixes?.[f.fileName] || props.initialPrefixes?.[f.fileName] === f.defaultPrefix,
        customPrefix: props.initialPrefixes?.[f.fileName] ?? f.defaultPrefix
      }
    ])
  )
)

const renderDpi = ref(props.preflight?.renderDpi ?? props.initialRenderDpi ?? 250)
const isReview = computed(() => props.mode === 'review')
const reviewPreflight = ref<PdfPreflightInfo | null>(props.preflight ?? null)
const isRecalculating = ref(false)
const preflightError = ref<string | null>(null)
const isDpiChanged = computed(() => (
  isReview.value
  && reviewPreflight.value != null
  && Number(renderDpi.value) !== reviewPreflight.value.renderDpi
))
const isQuotaCheckDisabled = computed(() => (
  isReview.value
  && reviewPreflight.value != null
  && !reviewPreflight.value.withinQuota
  && !isDpiChanged.value
))
const dpiOptions = [
  { label: '72 DPI — smallest output', value: 72 },
  { label: '150 DPI — balanced', value: 150 },
  { label: '200 DPI — high quality', value: 200 },
  { label: '250 DPI — current default', value: 250 },
  { label: '300 DPI — largest output', value: 300 }
]

const resolvedPrefixes = computed<Record<string, string>>(() => {
  const out: Record<string, string> = {}
  for (const f of props.files) {
    const st = stateByFileName.value[f.fileName]
    const prefix = st?.useFileName ? f.defaultPrefix : (st?.customPrefix ?? '').trim()
    out[f.fileName] = prefix
  }
  return out
})

const hasInvalidPrefix = computed(() => {
  for (const f of props.files) {
    const prefix = resolvedPrefixes.value[f.fileName]
    if (!prefix) return true
  }
  return false
})

function ensureFileState(fileName: string): PdfPrefixState {
  const existing = stateByFileName.value[fileName]
  if (existing) return existing

  const file = props.files.find(entry => entry.fileName === fileName)
  const nextState: PdfPrefixState = {
    useFileName: true,
    customPrefix: file?.defaultPrefix ?? ''
  }
  stateByFileName.value[fileName] = nextState
  return nextState
}

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes >= Number.MAX_SAFE_INTEGER) return 'unlimited'
  if (bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let value = bytes
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  return `${value.toFixed(value >= 10 || unit === 0 ? 0 : 1)} ${units[unit]}`
}

const submitLabel = computed(() => {
  if (!isReview.value) return 'Continue'
  if (isRecalculating.value) return 'Recalculating…'
  if (isDpiChanged.value) return 'Recalculate estimate'
  return reviewPreflight.value?.withinQuota ? 'Start conversion' : 'Select a different DPI'
})

async function submit() {
  if (isDpiChanged.value && props.recalculate) {
    isRecalculating.value = true
    preflightError.value = null
    try {
      reviewPreflight.value = await props.recalculate(Number(renderDpi.value))
    } catch (error) {
      preflightError.value = error instanceof Error ? error.message : 'Could not recalculate the PDF estimate.'
    } finally {
      isRecalculating.value = false
    }
    return
  }

  emit('close', { prefixes: resolvedPrefixes.value, renderDpi: Number(renderDpi.value) })
}
</script>

<template>
  <UiResponsiveSlideover
    side="right"
    :close="{ onClick: () => emit('close', null) }"
  >
    <template #header>
      <UiSlideoverHeader
        :title="isReview ? 'Review PDF conversion' : 'PDF conversion settings'"
        icon="i-lucide-file-type"
      />
    </template>

    <template #body>
      <UForm :id="formId" class="space-y-4" @submit="submit">
        <div class="text-sm text-muted">
          {{ isReview
            ? 'The PDF has been uploaded and analyzed. Review the estimated conversion size before processing.'
            : 'Choose how pages created from the PDF should be named and select the conversion resolution.' }}
        </div>

        <UAlert
          v-if="isReview && reviewPreflight"
          :color="reviewPreflight.withinQuota ? 'success' : 'warning'"
          :icon="reviewPreflight.withinQuota ? 'i-lucide-circle-check' : 'i-lucide-hard-drive'"
          :title="reviewPreflight.withinQuota ? 'Enough workspace storage is available' : 'Workspace storage is too small for this setting'"
        >
          <template #description>
            <div class="space-y-1">
              <div>{{ reviewPreflight.pdfPageCount }} PDF pages · estimated output {{ formatBytes(reviewPreflight.estimatedPdfBytes) }}</div>
              <div>Estimated project storage after conversion: {{ formatBytes(reviewPreflight.estimatedStorageBytes) }}</div>
              <div v-if="reviewPreflight.quotaEnforced">
                Available before reservation: {{ formatBytes(reviewPreflight.availableBytes) }}
              </div>
              <div v-if="reviewPreflight.quotaEnforced && reviewPreflight.withinQuota">
                Remaining after reservation: {{ formatBytes(reviewPreflight.availableBytesAfterReservation) }}
              </div>
              <div>{{ reviewPreflight.message }}</div>
            </div>
          </template>
        </UAlert>

        <UAlert
          v-if="preflightError"
          color="error"
          icon="i-lucide-circle-alert"
          title="Could not recalculate estimate"
          :description="preflightError"
        />

        <div class="rounded-sm border border-default p-3 space-y-2">
          <div class="text-sm font-medium">
            PDF render resolution
          </div>
          <div class="text-sm text-muted">
            Higher DPI improves detail but can make the generated page images much larger.
            <span v-if="isReview">Changing DPI recalculates the estimate before you can start conversion.</span>
          </div>
          <USelect v-model="renderDpi" :items="dpiOptions" />
        </div>

        <div class="space-y-3">
          <div
            v-for="f in props.files"
            :key="f.fileName"
            class="rounded-sm border border-default p-3 space-y-2"
          >
            <div class="text-sm font-medium truncate">
              {{ f.fileName }}
            </div>

            <div v-if="!isReview" class="flex items-center justify-between gap-3">
              <div class="text-sm">
                Use file name
              </div>
              <USwitch v-model="ensureFileState(f.fileName).useFileName" />
            </div>

            <div v-if="!isReview && !ensureFileState(f.fileName).useFileName" class="space-y-1">
              <div class="text-sm text-muted">
                Prefix
              </div>
              <UInput v-model="ensureFileState(f.fileName).customPrefix" placeholder="Enter prefix..." />
            </div>

            <div v-if="!isReview" class="text-xs text-muted">
              Pages will be created as {{ resolvedPrefixes[f.fileName] || '…' }}_001, {{ resolvedPrefixes[f.fileName] || '…' }}_002, …
            </div>
          </div>
        </div>
      </UForm>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" @click="emit('close', null)">
          Cancel
        </UButton>
        <UButton
          type="submit"
          :form="formId"
          color="primary"
          variant="solid"
          :loading="isRecalculating"
          :disabled="hasInvalidPrefix || isQuotaCheckDisabled || isRecalculating"
        >
          {{ submitLabel }}
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
