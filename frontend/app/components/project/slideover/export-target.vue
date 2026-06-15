<script setup lang="ts">
type ExportFormat = 'PAGE_XML' | 'ALTO_XML' | 'TXT' | 'PDF' | 'DOCX' | 'TEI' | 'CSV' | 'XLSX'
type TextLevel = 'PAGE' | 'REGION' | 'TEXT_LINE'
type SpreadsheetProfile = 'PAGE_METADATA' | 'TAGS' | 'REGIONS'
type PdfProfile = 'SEARCHABLE' | 'IMAGES_ONLY' | 'TEXT_PAGES' | 'PDFA_SEARCHABLE'
type TeiProfile = 'STANDARD' | 'LAYOUT'
type ExportDialogMode = 'page' | 'project' | 'basic' | 'package'

type DocxOptions = {
  preserveLineBreaks: boolean
  forcePageBreaks: boolean
  includeImageNames: boolean
  markUnclearWords: boolean
}

type EmbeddedOutputRequest = {
  format: Exclude<ExportFormat, 'PAGE_XML'>
  includePageDelimiters?: boolean
  textLevel?: TextLevel
  textVariantIndex?: number
  pdfProfile?: PdfProfile
  teiProfile?: TeiProfile
  spreadsheetProfiles?: SpreadsheetProfile[]
  docxOptions?: DocxOptions
}

type ExportDialogResult = {
  format: ExportFormat | null
  targetPageXmlVersion: string
  includePageDelimiters: boolean
  textLevel: TextLevel
  textVariantIndex: number
  pdfProfile: PdfProfile
  teiProfile: TeiProfile
  spreadsheetProfiles: SpreadsheetProfile[]
  docxOptions: DocxOptions
  embeddedOutputs: EmbeddedOutputRequest[]
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

const pageFormatOptions: Array<{ label: string, value: ExportFormat }> = [
  { label: 'PAGE XML', value: 'PAGE_XML' },
  { label: 'ALTO XML (.alto.xml)', value: 'ALTO_XML' },
  { label: 'Text (.txt)', value: 'TXT' },
  { label: 'PDF (.pdf)', value: 'PDF' },
  { label: 'DOCX (.docx)', value: 'DOCX' },
  { label: 'TEI (.tei.xml)', value: 'TEI' }
]

const projectFormatOptions: Array<{ label: string, value: Exclude<ExportFormat, 'PAGE_XML'> }> = [
  { label: 'ALTO XML (.alto.zip)', value: 'ALTO_XML' },
  { label: 'Text (.txt)', value: 'TXT' },
  { label: 'PDF (.pdf)', value: 'PDF' },
  { label: 'DOCX (.docx)', value: 'DOCX' },
  { label: 'TEI (.tei.xml)', value: 'TEI' },
  { label: 'CSV (.csv / .zip)', value: 'CSV' },
  { label: 'XLSX (.xlsx / .zip)', value: 'XLSX' }
]

const textLevelOptions: Array<{ label: string, value: TextLevel }> = [
  { label: 'Page', value: 'PAGE' },
  { label: 'Region', value: 'REGION' },
  { label: 'Text line', value: 'TEXT_LINE' }
]

const pdfProfileOptions: Array<{ label: string, value: PdfProfile }> = [
  { label: 'Searchable', value: 'SEARCHABLE' },
  { label: 'Images only', value: 'IMAGES_ONLY' },
  { label: 'Text pages', value: 'TEXT_PAGES' },
  { label: 'PDF/A searchable', value: 'PDFA_SEARCHABLE' }
]

const teiProfileOptions: Array<{ label: string, value: TeiProfile }> = [
  { label: 'Standard', value: 'STANDARD' },
  { label: 'Layout-aware', value: 'LAYOUT' }
]

const spreadsheetProfileOptions: Array<{ label: string, value: SpreadsheetProfile }> = [
  { label: 'Page metadata', value: 'PAGE_METADATA' },
  { label: 'Tags', value: 'TAGS' },
  { label: 'Regions', value: 'REGIONS' }
]

const props = withDefaults(defineProps<{
  title?: string
  description?: string
  mode?: ExportDialogMode
  initialFormat?: ExportFormat | null
  initialTargetVersion?: string
  initialIncludePageDelimiters?: boolean
  initialTextLevel?: TextLevel
  initialTextVariantIndex?: number
  initialPdfProfile?: PdfProfile
  initialTeiProfile?: TeiProfile
  initialSpreadsheetProfiles?: SpreadsheetProfile[]
  initialDocxOptions?: Partial<DocxOptions>
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
  initialPdfProfile: 'SEARCHABLE',
  initialTeiProfile: 'STANDARD',
  initialSpreadsheetProfiles: () => ['PAGE_METADATA'],
  initialDocxOptions: () => ({
    preserveLineBreaks: true,
    forcePageBreaks: true,
    includeImageNames: false,
    markUnclearWords: false
  }),
  initialEmbeddedFormats: () => [],
  initialEmbeddedTxtPageDelimiters: false,
  initialEmbeddedTxtTextLevel: 'PAGE',
  initialEmbeddedTxtTextVariantIndex: 0,
  confirmLabel: 'Continue'
})

const emit = defineEmits<{
  close: [result: ExportDialogResult | null]
}>()

const formatOptions = computed(() => props.mode === 'page' ? pageFormatOptions : projectFormatOptions)
const isArchiveMode = computed(() => props.mode === 'basic' || props.mode === 'package')
const selectedFormat = ref<ExportFormat | undefined>(
  isArchiveMode.value
    ? undefined
    : (props.initialFormat ?? formatOptions.value[0]?.value)
)
const targetVersion = ref(props.initialTargetVersion)
const includePageDelimiters = ref(Boolean(props.initialIncludePageDelimiters))
const textLevel = ref<TextLevel>(props.initialTextLevel)
const textVariantIndex = ref<number>(props.initialTextVariantIndex)
const pdfProfile = ref<PdfProfile>(props.initialPdfProfile)
const teiProfile = ref<TeiProfile>(props.initialTeiProfile)
const spreadsheetSelection = reactive<Record<SpreadsheetProfile, boolean>>({
  PAGE_METADATA: props.initialSpreadsheetProfiles.includes('PAGE_METADATA'),
  TAGS: props.initialSpreadsheetProfiles.includes('TAGS'),
  REGIONS: props.initialSpreadsheetProfiles.includes('REGIONS')
})
const docxOptions = reactive<DocxOptions>({
  preserveLineBreaks: props.initialDocxOptions.preserveLineBreaks ?? true,
  forcePageBreaks: props.initialDocxOptions.forcePageBreaks ?? true,
  includeImageNames: props.initialDocxOptions.includeImageNames ?? false,
  markUnclearWords: props.initialDocxOptions.markUnclearWords ?? false
})

const embeddedSelection = reactive<Record<Exclude<ExportFormat, 'PAGE_XML'>, boolean>>({
  ALTO_XML: props.initialEmbeddedFormats.includes('ALTO_XML'),
  TXT: props.initialEmbeddedFormats.includes('TXT'),
  PDF: props.initialEmbeddedFormats.includes('PDF'),
  DOCX: props.initialEmbeddedFormats.includes('DOCX'),
  TEI: props.initialEmbeddedFormats.includes('TEI'),
  CSV: props.initialEmbeddedFormats.includes('CSV'),
  XLSX: props.initialEmbeddedFormats.includes('XLSX')
})
const embeddedTxtPageDelimiters = ref(Boolean(props.initialEmbeddedTxtPageDelimiters))
const embeddedTxtTextLevel = ref<TextLevel>(props.initialEmbeddedTxtTextLevel)
const embeddedTxtTextVariantIndex = ref<number>(props.initialEmbeddedTxtTextVariantIndex)

const usesPageXmlOptions = computed(() =>
  isArchiveMode.value || selectedFormat.value === 'PAGE_XML'
)

const showDirectTxtDelimiter = computed(() =>
  props.mode === 'project' && selectedFormat.value === 'TXT'
)

const showDirectTxtOptions = computed(() =>
  props.mode !== 'package' && selectedFormat.value === 'TXT'
)

const showDirectPdfOptions = computed(() =>
  props.mode !== 'package' && selectedFormat.value === 'PDF'
)

const showDirectTeiOptions = computed(() =>
  props.mode !== 'package' && selectedFormat.value === 'TEI'
)

const showDirectSpreadsheetOptions = computed(() =>
  props.mode === 'project' && (selectedFormat.value === 'CSV' || selectedFormat.value === 'XLSX')
)

const showDirectDocxOptions = computed(() =>
  props.mode !== 'package' && selectedFormat.value === 'DOCX'
)

const showEmbeddedTxtOptions = computed(() =>
  isArchiveMode.value && embeddedSelection.TXT
)

const showEmbeddedPdfOptions = computed(() =>
  isArchiveMode.value && embeddedSelection.PDF
)

const showEmbeddedTeiOptions = computed(() =>
  isArchiveMode.value && embeddedSelection.TEI
)

const showEmbeddedSpreadsheetOptions = computed(() =>
  isArchiveMode.value && (embeddedSelection.CSV || embeddedSelection.XLSX)
)

const showEmbeddedDocxOptions = computed(() =>
  isArchiveMode.value && embeddedSelection.DOCX
)

const isLegacyTarget = computed(() => targetVersion.value !== PAGE_XML_PRIMARY_VERSION)

function selectedSpreadsheetProfiles(): SpreadsheetProfile[] {
  return spreadsheetProfileOptions
    .map(option => option.value)
    .filter(value => spreadsheetSelection[value])
}

function closeWithResult() {
  const spreadsheetProfiles = selectedSpreadsheetProfiles()
  const embeddedOutputs: ExportDialogResult['embeddedOutputs'] = []

  if (isArchiveMode.value) {
    if (embeddedSelection.ALTO_XML) embeddedOutputs.push({ format: 'ALTO_XML' })
    if (embeddedSelection.TXT) {
      embeddedOutputs.push({
        format: 'TXT',
        includePageDelimiters: embeddedTxtPageDelimiters.value,
        textLevel: embeddedTxtTextLevel.value,
        textVariantIndex: Number.isFinite(embeddedTxtTextVariantIndex.value) ? embeddedTxtTextVariantIndex.value : 0
      })
    }
    if (embeddedSelection.PDF) embeddedOutputs.push({ format: 'PDF', pdfProfile: pdfProfile.value })
    if (embeddedSelection.DOCX) {
      embeddedOutputs.push({
        format: 'DOCX',
        docxOptions: { ...docxOptions }
      })
    }
    if (embeddedSelection.TEI) embeddedOutputs.push({ format: 'TEI', teiProfile: teiProfile.value })
    if (embeddedSelection.CSV) embeddedOutputs.push({ format: 'CSV', spreadsheetProfiles })
    if (embeddedSelection.XLSX) embeddedOutputs.push({ format: 'XLSX', spreadsheetProfiles })
  }

  emit('close', {
    format: isArchiveMode.value ? null : (selectedFormat.value ?? null),
    targetPageXmlVersion: targetVersion.value,
    includePageDelimiters: includePageDelimiters.value,
    textLevel: textLevel.value,
    textVariantIndex: Number.isFinite(textVariantIndex.value) ? textVariantIndex.value : 0,
    pdfProfile: pdfProfile.value,
    teiProfile: teiProfile.value,
    spreadsheetProfiles,
    docxOptions: { ...docxOptions },
    embeddedOutputs
  })
}
</script>

<template>
  <UiResponsiveSlideover
    side="right"
    :close="{ onClick: () => emit('close', null) }"
  >
    <template #header>
      <UiSlideoverHeader
        :title="props.title"
        :icon="props.title.toLowerCase().includes('package') ? 'i-lucide-file-archive' : 'i-lucide-file-output'"
      />
    </template>

    <template #body>
      <div class="space-y-5">
        <p class="text-sm text-muted">
          {{ props.description }}
        </p>

        <UFormField
          v-if="!isArchiveMode"
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
          v-if="showDirectPdfOptions"
          class="space-y-4 rounded-lg border border-default p-3"
        >
          <UFormField
            label="PDF profile"
            name="pdfProfile"
          >
            <USelect
              v-model="pdfProfile"
              :items="pdfProfileOptions"
              value-key="value"
              class="w-full"
            />
          </UFormField>
        </div>

        <div
          v-if="showDirectTeiOptions"
          class="space-y-4 rounded-lg border border-default p-3"
        >
          <UFormField
            label="TEI profile"
            name="teiProfile"
          >
            <USelect
              v-model="teiProfile"
              :items="teiProfileOptions"
              value-key="value"
              class="w-full"
            />
          </UFormField>
        </div>

        <div
          v-if="showDirectSpreadsheetOptions"
          class="space-y-4 rounded-lg border border-default p-3"
        >
          <p class="text-sm font-medium">
            Spreadsheet profiles
          </p>
          <div class="space-y-2">
            <UCheckbox
              v-for="option in spreadsheetProfileOptions"
              :key="option.value"
              :model-value="spreadsheetSelection[option.value]"
              :label="option.label"
              @update:model-value="spreadsheetSelection[option.value] = ($event === true)"
            />
          </div>
        </div>

        <div
          v-if="showDirectDocxOptions"
          class="space-y-3 rounded-lg border border-default p-3"
        >
          <UCheckbox
            :model-value="docxOptions.preserveLineBreaks"
            label="Preserve line breaks"
            @update:model-value="docxOptions.preserveLineBreaks = ($event === true)"
          />
          <UCheckbox
            v-if="props.mode === 'project'"
            :model-value="docxOptions.forcePageBreaks"
            label="Force page breaks between pages"
            @update:model-value="docxOptions.forcePageBreaks = ($event === true)"
          />
          <UCheckbox
            :model-value="docxOptions.includeImageNames"
            label="Include image file names"
            @update:model-value="docxOptions.includeImageNames = ($event === true)"
          />
          <UCheckbox
            :model-value="docxOptions.markUnclearWords"
            label="Mark unclear words"
            @update:model-value="docxOptions.markUnclearWords = ($event === true)"
          />
        </div>

        <div
          v-if="isArchiveMode"
          class="space-y-3 rounded-lg border border-default p-4"
        >
          <div>
            <p class="text-sm font-medium">
              {{ props.mode === 'package' ? 'Package extras' : 'Converted outputs' }}
            </p>
            <p class="text-xs text-muted mt-1">
              {{ props.mode === 'package'
                ? 'METS and the structured import manifest are included automatically. Optional outputs are added to the package.'
                : 'Images and XML files are included automatically. Optional outputs are added to the same flat zip.' }}
            </p>
          </div>

          <div class="space-y-2">
            <UCheckbox
              :model-value="embeddedSelection.ALTO_XML"
              label="ALTO XML (.alto.zip)"
              @update:model-value="embeddedSelection.ALTO_XML = ($event === true)"
            />
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
            <UCheckbox
              :model-value="embeddedSelection.CSV"
              label="CSV (.csv / .zip)"
              @update:model-value="embeddedSelection.CSV = ($event === true)"
            />
            <UCheckbox
              :model-value="embeddedSelection.XLSX"
              label="XLSX (.xlsx / .zip)"
              @update:model-value="embeddedSelection.XLSX = ($event === true)"
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
              :model-value="embeddedTxtPageDelimiters"
              label="Insert page delimiters in embedded TXT export"
              @update:model-value="embeddedTxtPageDelimiters = ($event === true)"
            />
          </div>

          <div
            v-if="showEmbeddedPdfOptions"
            class="space-y-4 rounded-lg border border-default p-3"
          >
            <UFormField
              label="PDF profile"
              name="embeddedPdfProfile"
            >
              <USelect
                v-model="pdfProfile"
                :items="pdfProfileOptions"
                value-key="value"
                class="w-full"
              />
            </UFormField>
          </div>

          <div
            v-if="showEmbeddedTeiOptions"
            class="space-y-4 rounded-lg border border-default p-3"
          >
            <UFormField
              label="TEI profile"
              name="embeddedTeiProfile"
            >
              <USelect
                v-model="teiProfile"
                :items="teiProfileOptions"
                value-key="value"
                class="w-full"
              />
            </UFormField>
          </div>

          <div
            v-if="showEmbeddedSpreadsheetOptions"
            class="space-y-4 rounded-lg border border-default p-3"
          >
            <p class="text-sm font-medium">
              Spreadsheet profiles
            </p>
            <div class="space-y-2">
              <UCheckbox
                v-for="option in spreadsheetProfileOptions"
                :key="option.value"
                :model-value="spreadsheetSelection[option.value]"
                :label="option.label"
                @update:model-value="spreadsheetSelection[option.value] = ($event === true)"
              />
            </div>
          </div>

          <div
            v-if="showEmbeddedDocxOptions"
            class="space-y-3 rounded-lg border border-default p-3"
          >
            <UCheckbox
              :model-value="docxOptions.preserveLineBreaks"
              label="Preserve line breaks"
              @update:model-value="docxOptions.preserveLineBreaks = ($event === true)"
            />
            <UCheckbox
              :model-value="docxOptions.forcePageBreaks"
              label="Force page breaks between pages"
              @update:model-value="docxOptions.forcePageBreaks = ($event === true)"
            />
            <UCheckbox
              :model-value="docxOptions.includeImageNames"
              label="Include image file names"
              @update:model-value="docxOptions.includeImageNames = ($event === true)"
            />
            <UCheckbox
              :model-value="docxOptions.markUnclearWords"
              label="Mark unclear words"
              @update:model-value="docxOptions.markUnclearWords = ($event === true)"
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
          :disabled="!isArchiveMode && !selectedFormat"
          @click="closeWithResult"
        >
          {{ props.confirmLabel }}
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
