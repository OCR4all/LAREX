<script setup lang="ts">
type ExportFormat = 'PAGE_XML' | 'TXT' | 'PDF' | 'DOCX' | 'TEI'
type TextLevel = 'PAGE' | 'REGION' | 'TEXT_LINE'
type ExportDialogMode = 'page' | 'project' | 'package'

type ExportDialogResult = {
  format: ExportFormat | null
  targetPageXmlVersion: string
  includePageDelimiters: boolean
  textLevel: TextLevel
  textVariantIndex: number
  embeddedOutputs: Array<{
    format: Exclude<ExportFormat, 'PAGE_XML'>
    includePageDelimiters?: boolean
    textLevel?: TextLevel
    textVariantIndex?: number
  }>
}

const PAGE_XML_PRIMARY_VERSION = '2019-07-15'

const versionOptions = [
  { label: 'PAGE XML 2010-03-19', value: '2010-03-19' },
  { label: 'PAGE XML 2013-07-15', value: '2013-07-15' },
  { label: 'PAGE XML 2016-07-15', value: '2016-07-15' },
  { label: 'PAGE XML 2017-07-15', value: '2017-07-15' },
  { label: 'PAGE XML 2018-07-15', value: '2018-07-15' },
  { label: 'PAGE XML 2019-07-15', value: '2019-07-15' }
]

const pageFormatOptions = [
  { label: 'PAGE XML', value: 'PAGE_XML' },
  { label: 'Text (.txt)', value: 'TXT' },
  { label: 'PDF (.pdf)', value: 'PDF' },
  { label: 'DOCX (.docx)', value: 'DOCX' },
  { label: 'TEI (.tei.xml)', value: 'TEI' }
] as const

const renderedFormatOptions = [
  { label: 'Text (.txt)', value: 'TXT' },
  { label: 'PDF (.pdf)', value: 'PDF' },
  { label: 'DOCX (.docx)', value: 'DOCX' },
  { label: 'TEI (.tei.xml)', value: 'TEI' }
] as const

const textLevelOptions = [
  { label: 'Page', value: 'PAGE' },
  { label: 'Region', value: 'REGION' },
  { label: 'Text line', value: 'TEXT_LINE' }
] as const

const props = withDefaults(defineProps<{
  title?: string
  description?: string
  mode?: ExportDialogMode
  initialFormat?: ExportFormat | null
  initialTargetVersion?: string
  initialIncludePageDelimiters?: boolean
  initialTextLevel?: TextLevel
  initialTextVariantIndex?: number
  initialEmbeddedFormats?: Array<Exclude<ExportFormat, 'PAGE_XML'>>
  initialEmbeddedTxtPageDelimiters?: boolean
  initialEmbeddedTxtTextLevel?: TextLevel
  initialEmbeddedTxtTextVariantIndex?: number
  confirmLabel?: string
}>(), {
  title: 'Export',
  description: 'Choose export options.',
  mode: 'page',
  initialFormat: null,
  initialTargetVersion: PAGE_XML_PRIMARY_VERSION,
  initialIncludePageDelimiters: false,
  initialTextLevel: 'PAGE',
  initialTextVariantIndex: 0,
  initialEmbeddedFormats: () => [],
  initialEmbeddedTxtPageDelimiters: false,
  initialEmbeddedTxtTextLevel: 'PAGE',
  initialEmbeddedTxtTextVariantIndex: 0,
  confirmLabel: 'Continue'
})

const emit = defineEmits<{
  close: [result: ExportDialogResult | null]
}>()

const formatOptions = computed(() => props.mode === 'page' ? pageFormatOptions : renderedFormatOptions)
const selectedFormat = ref<ExportFormat | null>(
  props.mode === 'package'
    ? null
    : (props.initialFormat ?? (formatOptions.value[0]?.value ?? null))
)
const targetVersion = ref(props.initialTargetVersion)
const includePageDelimiters = ref(Boolean(props.initialIncludePageDelimiters))
const textLevel = ref<TextLevel>(props.initialTextLevel)
const textVariantIndex = ref<number>(props.initialTextVariantIndex)

const embeddedSelection = reactive<Record<'TXT' | 'PDF' | 'DOCX' | 'TEI', boolean>>({
  TXT: props.initialEmbeddedFormats.includes('TXT'),
  PDF: props.initialEmbeddedFormats.includes('PDF'),
  DOCX: props.initialEmbeddedFormats.includes('DOCX'),
  TEI: props.initialEmbeddedFormats.includes('TEI')
})
const embeddedTxtPageDelimiters = ref(Boolean(props.initialEmbeddedTxtPageDelimiters))
const embeddedTxtTextLevel = ref<TextLevel>(props.initialEmbeddedTxtTextLevel)
const embeddedTxtTextVariantIndex = ref<number>(props.initialEmbeddedTxtTextVariantIndex)

const usesPageXmlOptions = computed(() =>
  props.mode === 'package' || selectedFormat.value === 'PAGE_XML'
)

const showDirectTxtDelimiter = computed(() =>
  props.mode === 'project' && selectedFormat.value === 'TXT'
)

const showDirectTxtOptions = computed(() =>
  props.mode !== 'package' && selectedFormat.value === 'TXT'
)

const showEmbeddedTxtDelimiter = computed(() =>
  props.mode === 'package' && embeddedSelection.TXT
)

const showEmbeddedTxtOptions = computed(() =>
  props.mode === 'package' && embeddedSelection.TXT
)

const isLegacyTarget = computed(() => targetVersion.value !== PAGE_XML_PRIMARY_VERSION)

function closeWithResult() {
  const embeddedOutputs: ExportDialogResult['embeddedOutputs'] = []
  if (props.mode === 'package') {
    if (embeddedSelection.TXT) {
      embeddedOutputs.push({
        format: 'TXT',
        includePageDelimiters: embeddedTxtPageDelimiters.value,
        textLevel: embeddedTxtTextLevel.value,
        textVariantIndex: Number.isFinite(embeddedTxtTextVariantIndex.value) ? embeddedTxtTextVariantIndex.value : 0
      })
    }
    if (embeddedSelection.PDF) embeddedOutputs.push({ format: 'PDF' })
    if (embeddedSelection.DOCX) embeddedOutputs.push({ format: 'DOCX' })
    if (embeddedSelection.TEI) embeddedOutputs.push({ format: 'TEI' })
  }

  emit('close', {
    format: props.mode === 'package' ? null : selectedFormat.value,
    targetPageXmlVersion: targetVersion.value,
    includePageDelimiters: includePageDelimiters.value,
    textLevel: textLevel.value,
    textVariantIndex: Number.isFinite(textVariantIndex.value) ? textVariantIndex.value : 0,
    embeddedOutputs
  })
}
</script>

<template>
  <USlideover
    side="right"
    :title="props.title"
    :close="{ onClick: () => emit('close', null) }"
  >
    <template #body>
      <div class="space-y-5">
        <p class="text-sm text-muted">
          {{ props.description }}
        </p>

        <UFormField
          v-if="props.mode !== 'package'"
          label="Output format"
          name="format"
        >
          <USelect
            v-model="selectedFormat"
            :items="formatOptions"
            value-key="value"
            class="w-full"
          />
        </UFormField>

        <UFormField
          v-if="usesPageXmlOptions"
          label="Target PAGE XML version"
          name="targetVersion"
        >
          <USelect
            v-model="targetVersion"
            :items="versionOptions"
            value-key="value"
            class="w-full"
          />
        </UFormField>

        <UAlert
          v-if="usesPageXmlOptions && isLegacyTarget"
          color="warning"
          variant="soft"
          icon="i-lucide-triangle-alert"
          title="Legacy export can lose data"
          description="Older PAGE XML schemas do not support all 2019 features."
        />

        <div
          v-if="showDirectTxtOptions"
          class="space-y-4 rounded-lg border border-default p-3"
        >
          <UFormField
            label="Text level"
            name="textLevel"
          >
            <USelect
              v-model="textLevel"
              :items="textLevelOptions"
              value-key="value"
              class="w-full"
            />
          </UFormField>

          <UFormField
            label="Text index"
            name="textVariantIndex"
            description="Uses index 0 by default."
          >
            <UInput
              v-model.number="textVariantIndex"
              type="number"
              min="0"
              step="1"
              class="w-full"
            />
          </UFormField>

          <UCheckbox
            v-if="showDirectTxtDelimiter"
            :model-value="includePageDelimiters"
            label="Insert page delimiters in project TXT export"
            @update:model-value="includePageDelimiters = ($event === true)"
          />
        </div>

        <div
          v-if="props.mode === 'package'"
          class="space-y-3 rounded-lg border border-default p-4"
        >
          <div>
            <p class="text-sm font-medium">
              Embed auxiliary outputs in ZIP
            </p>
            <p class="text-xs text-muted mt-1">
              These files are added under <code>exports/</code> in the package.
            </p>
          </div>

          <div class="space-y-2">
            <UCheckbox
              :model-value="embeddedSelection.TXT"
              label="Text (.txt)"
              @update:model-value="embeddedSelection.TXT = ($event === true)"
            />
            <UCheckbox
              :model-value="embeddedSelection.PDF"
              label="PDF (.pdf)"
              @update:model-value="embeddedSelection.PDF = ($event === true)"
            />
            <UCheckbox
              :model-value="embeddedSelection.DOCX"
              label="DOCX (.docx)"
              @update:model-value="embeddedSelection.DOCX = ($event === true)"
            />
            <UCheckbox
              :model-value="embeddedSelection.TEI"
              label="TEI (.tei.xml)"
              @update:model-value="embeddedSelection.TEI = ($event === true)"
            />
          </div>

          <div
            v-if="showEmbeddedTxtOptions"
            class="space-y-4 rounded-lg border border-default p-3"
          >
            <UFormField
              label="Text level"
              name="embeddedTxtTextLevel"
            >
              <USelect
                v-model="embeddedTxtTextLevel"
                :items="textLevelOptions"
                value-key="value"
                class="w-full"
              />
            </UFormField>

            <UFormField
              label="Text index"
              name="embeddedTxtTextVariantIndex"
              description="Uses index 0 by default."
            >
              <UInput
                v-model.number="embeddedTxtTextVariantIndex"
                type="number"
                min="0"
                step="1"
                class="w-full"
              />
            </UFormField>

            <UCheckbox
              v-if="showEmbeddedTxtDelimiter"
              :model-value="embeddedTxtPageDelimiters"
              label="Insert page delimiters in embedded TXT export"
              @update:model-value="embeddedTxtPageDelimiters = ($event === true)"
            />
          </div>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton
          color="neutral"
          variant="ghost"
          @click="emit('close', null)"
        >
          Cancel
        </UButton>
        <UButton
          color="primary"
          variant="solid"
          :disabled="props.mode !== 'package' && !selectedFormat"
          @click="closeWithResult"
        >
          {{ props.confirmLabel }}
        </UButton>
      </div>
    </template>
  </USlideover>
</template>
