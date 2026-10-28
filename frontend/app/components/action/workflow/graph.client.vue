<script setup lang="ts">
import { ConnectionMode, Handle, Position, VueFlow, MarkerType, useVueFlow } from '@vue-flow/core'
import type { Node as FlowNode, Edge, NodeDragEvent } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import type { ActionDefinition } from '~/types/action'
import type { ActionWorkflowStep } from '~/types/action-workflow'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/controls/dist/style.css'

const props = defineProps<{
  steps: ActionWorkflowStep[]
  processors: ActionDefinition[]
  selected: number
  nodeKeys?: string[]
  editable?: boolean
  inlineSettings?: boolean
  workspaceId?: string
  projectId?: string
  workflowId?: string
  disabled?: boolean
}>()
const emit = defineEmits<{
  select: [index: number]
  move: [index: number, direction: -1 | 1]
  reorder: [from: number, to: number]
  remove: [index: number]
  insert: [processorId: string, index: number]
  update: [index: number, step: ActionWorkflowStep]
}>()
const flowId = useId()
const { fitView, zoomIn, zoomOut, onNodesInitialized, screenToFlowCoordinate, getNodes, updateNode, dimensions, setViewport } = useVueFlow(flowId)
const widths = ref<Record<string, number>>({})
const dropIndex = ref<number | null>(null)
let viewportInitialized = false
const dragDestination = ref<number | null>(null)
const key = (index: number) => props.nodeKeys?.[index] ?? String(index)
const positions = computed(() => {
  let x = 60
  return props.steps.map((_, index) => {
    const position = { x, y: 60 }
    x += (widths.value[key(index)] ?? (props.inlineSettings ? 352 : 288)) + 96
    return position
  })
})
watch(() => getNodes.value.map(node => [node.id, node.dimensions.width] as const), (dimensions) => {
  const measured = Object.fromEntries(dimensions.filter(([, width]) => width > 0))
  if (JSON.stringify(measured) !== JSON.stringify(widths.value)) widths.value = measured
}, { deep: true })
function initializeViewport() {
  if (viewportInitialized || !props.steps.length) return
  viewportInitialized = true
  if (props.inlineSettings && props.projectId) {
    const node = getNodes.value.find(node => node.id === key(props.selected))
    if (node?.dimensions.width && node.dimensions.height && dimensions.value.width && dimensions.value.height) {
      // Start run forms at the leading edge so the next steps remain visible.
      const zoom = Math.min(1, (dimensions.value.width - 48) / node.dimensions.width, (dimensions.value.height - 88) / node.dimensions.height)
      void setViewport({ x: 24 - node.position.x * zoom, y: 64 - node.position.y * zoom, zoom })
      return
    }
  }
  void fitView({
    nodes: props.inlineSettings
      ? [key(props.selected)]
      : props.steps.length > 3
        ? [props.selected - 1, props.selected, props.selected + 1].filter(index => index >= 0 && index < props.steps.length).map(key)
        : undefined,
    padding: props.inlineSettings ? 0.4 : 0.2, maxZoom: 1, duration: 0
  })
}
onNodesInitialized(initializeViewport)
async function navigateStep(index: number) {
  if (props.disabled || !Number.isInteger(index) || index < 0 || index >= props.steps.length) return
  emit('select', index)
  await nextTick()
  const reducedMotion = typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
  void fitView({ nodes: [key(index)], padding: { top: '64px', right: '24px', bottom: '24px', left: '24px' }, maxZoom: 1, duration: reducedMotion ? 0 : 240 })
}
const nodes = computed<FlowNode[]>(() => props.steps.map((step, index) => {
  const processor = props.processors.find(item => item.id === step.processorDefinitionId)
  return {
    id: key(index),
    type: 'processor',
    position: positions.value[index]!,
    dragHandle: '.workflow-node-header',
    data: { index, processor },
    selected: props.selected === index,
    ariaLabel: `Step ${index + 1}: ${processor?.name ?? 'Unavailable processor'}${processor?.executeRole === 'CURATOR' ? ', curators only' : ''}`
  }
}))
const edges = computed<Edge[]>(() => props.steps.slice(1).map((_, index) => ({
  id: `${key(index)}-${key(index + 1)}`,
  source: key(index),
  sourceHandle: 'output',
  target: key(index + 1),
  targetHandle: 'input',
  type: 'smoothstep',
  markerEnd: MarkerType.ArrowClosed,
  style: { stroke: 'var(--ui-primary)', strokeWidth: 2 }
})))
function insertionAt(x: number, excluding?: number) {
  return positions.value.filter((_, index) => index !== excluding).filter((position) => {
    const index = positions.value.indexOf(position)
    return x > position.x + (widths.value[key(index)] ?? (props.inlineSettings ? 352 : 288)) / 2
  }).length
}
function dragOver(event: DragEvent) {
  if (!props.editable || props.disabled || props.steps.length >= 30 || !event.dataTransfer?.types.includes('application/larex-action')) return
  event.preventDefault()
  event.dataTransfer.dropEffect = 'copy'
  dropIndex.value = insertionAt(screenToFlowCoordinate({ x: event.clientX, y: event.clientY }).x)
}
function drop(event: DragEvent) {
  const id = event.dataTransfer?.getData('application/larex-action')
  if (!id || dropIndex.value === null || props.disabled) return
  event.preventDefault()
  emit('insert', id, dropIndex.value)
  dropIndex.value = null
}
function nodeDrag({ node }: NodeDragEvent) {
  dragDestination.value = insertionAt(node.position.x + node.dimensions.width / 2, node.data.index)
}
async function nodeDragStop({ node }: NodeDragEvent) {
  const destination = insertionAt(node.position.x + node.dimensions.width / 2, node.data.index)
  emit('reorder', node.data.index, destination)
  dragDestination.value = null
  await nextTick()
  const index = props.nodeKeys?.indexOf(node.id) ?? Number(node.id)
  if (positions.value[index]) updateNode(node.id, { position: positions.value[index]! })
}
</script>

<template>
  <div
    class="workflow-graph relative overflow-hidden bg-muted/30"
    :class="inlineSettings ? 'h-full' : 'h-[440px] rounded-lg border border-default'"
    aria-label="Linear workflow"
    @dragover="dragOver"
    @drop="drop"
    @dragleave="event => { if (!(event.currentTarget as HTMLElement).contains(event.relatedTarget as Node | null)) dropIndex = null }"
  >
    <VueFlow
      :id="flowId"
      :nodes="nodes"
      :edges="edges"
      :nodes-draggable="editable && !disabled"
      :nodes-connectable="false"
      :connection-mode="ConnectionMode.Strict"
      :edges-updatable="false"
      :edges-focusable="false"
      :selection-key-code="null"
      :delete-key-code="null"
      :min-zoom="0.2"
      :max-zoom="1.4"
      :fit-view-on-init="false"
      :fit-view-params="{ padding: 0.2, maxZoom: 1 }"
      @node-click="({ node }) => !disabled && emit('select', node.data.index)"
      @node-drag="nodeDrag"
      @node-drag-stop="nodeDragStop"
    >
      <Background :gap="20" pattern-color="var(--ui-border-accented)" />
      <Controls :show-interactive="false">
        <template #control-zoom-in>
          <UButton
            icon="i-lucide-plus"
            aria-label="Zoom in"
            size="xs"
            color="neutral"
            variant="ghost"
            class="rounded-none"
            @click="zoomIn()"
          />
        </template>
        <template #control-zoom-out>
          <UButton
            icon="i-lucide-minus"
            aria-label="Zoom out"
            size="xs"
            color="neutral"
            variant="ghost"
            class="rounded-none"
            @click="zoomOut()"
          />
        </template>
        <template #control-fit-view>
          <UButton
            icon="i-lucide-maximize"
            aria-label="Fit workflow"
            size="xs"
            color="neutral"
            variant="ghost"
            class="rounded-none"
            @click="fitView({ padding: 0.2, maxZoom: 1 })"
          />
        </template>
      </Controls>
      <template #node-processor="{ data }">
        <div class="overflow-hidden rounded-xl border bg-default shadow-md shadow-black/5" :class="[inlineSettings ? 'w-88' : 'w-72', selected === data.index ? 'border-primary ring-2 ring-primary/15' : 'border-default']">
          <Handle
            id="input"
            type="target"
            :position="Position.Left"
            :connectable="false"
            :class="{ 'opacity-0': data.index === 0 }"
          />
          <div class="workflow-node-header relative isolate flex items-center gap-3 overflow-hidden border-b border-default px-4 py-3" :class="editable && !disabled ? 'cursor-grab active:cursor-grabbing' : ''">
            <UiDitherWaves v-if="selected === data.index" />
            <UIcon v-if="editable" name="i-lucide-grip-vertical" class="size-4 shrink-0 text-muted" />
            <span class="flex size-7 shrink-0 items-center justify-center rounded-md bg-primary/10 text-xs font-semibold text-primary">{{ data.index + 1 }}</span>
            <div class="min-w-0 flex-1 space-y-1">
              <button
                type="button"
                class="block w-full text-left text-sm font-semibold text-highlighted"
                :disabled="disabled"
                :aria-pressed="selected === data.index"
                @click.stop="emit('select', data.index)"
              >
                {{ data.processor?.name ?? 'Unavailable processor' }}
              </button>
              <ActionExecuteRoleBadge :role="data.processor?.executeRole" size="xs" />
            </div>
          </div>
          <div class="px-4 py-3">
            <p class="text-xs leading-relaxed text-muted" :class="{ truncate: !inlineSettings }">
              {{ data.processor?.description || 'Page-processing Action' }}
            </p>
            <div
              v-if="inlineSettings && data.processor"
              class="nodrag nopan nowheel mt-4 border-t border-default pt-4"
              @pointerdown.stop
              @click.stop
              @wheel.stop
            >
              <ActionWorkflowStepSettings
                :step="steps[data.index]!"
                :processor="data.processor"
                :workspace-id="workspaceId"
                :project-id="projectId"
                :workflow-id="workflowId"
                :disabled="disabled"
                :compact="editable"
                @update="emit('update', data.index, $event)"
              />
            </div>
            <div v-if="editable" class="nodrag mt-3 flex items-center justify-between border-t border-default pt-2">
              <span class="text-[10px] font-medium tracking-wide text-dimmed uppercase">{{ data.index === 0 ? 'Start' : `After step ${data.index}` }}</span>
              <div class="flex gap-1">
                <UButton
                  icon="i-lucide-arrow-left"
                  aria-label="Move step earlier"
                  size="xs"
                  color="neutral"
                  variant="ghost"
                  :disabled="disabled || data.index === 0"
                  @click.stop="emit('move', data.index, -1)"
                />
                <UButton
                  icon="i-lucide-arrow-right"
                  aria-label="Move step later"
                  size="xs"
                  color="neutral"
                  variant="ghost"
                  :disabled="disabled || data.index === steps.length - 1"
                  @click.stop="emit('move', data.index, 1)"
                />
                <UButton
                  icon="i-lucide-trash-2"
                  aria-label="Remove step"
                  size="xs"
                  color="error"
                  variant="ghost"
                  :disabled="disabled"
                  @click.stop="emit('remove', data.index)"
                />
              </div>
            </div>
          </div>
          <Handle
            id="output"
            type="source"
            :position="Position.Right"
            :connectable="false"
            :class="{ 'opacity-0': data.index === steps.length - 1 }"
          />
        </div>
      </template>
    </VueFlow>
    <div
      v-if="inlineSettings && steps.length"
      role="group"
      aria-label="Workflow step navigation"
      class="absolute inset-x-4 top-4 flex justify-end gap-1.5"
    >
      <UButton
        icon="i-lucide-chevron-left"
        aria-label="Previous workflow step"
        title="Previous workflow step"
        color="neutral"
        variant="outline"
        size="sm"
        class="shrink-0"
        :disabled="disabled || selected <= 0"
        @click="navigateStep(selected - 1)"
      />
      <USelect
        :model-value="selected"
        :items="steps.map((step, index) => ({ value: index, label: `${index + 1}. ${processors.find(item => item.id === step.processorDefinitionId)?.name ?? 'Unavailable processor'}` }))"
        aria-label="Selected workflow step"
        class="min-w-0 w-56"
        size="sm"
        :disabled="disabled"
        @update:model-value="navigateStep(Number($event))"
      />
      <UButton
        icon="i-lucide-chevron-right"
        aria-label="Next workflow step"
        title="Next workflow step"
        color="neutral"
        variant="outline"
        size="sm"
        class="shrink-0"
        :disabled="disabled || selected >= steps.length - 1"
        @click="navigateStep(selected + 1)"
      />
    </div>
    <div v-if="!steps.length" class="pointer-events-none absolute inset-0 flex flex-col items-center justify-center gap-4 p-8 text-center">
      <div class="flex size-16 items-center justify-center rounded-2xl border border-dashed border-primary/40 bg-primary/5 text-primary">
        <UIcon name="i-lucide-workflow" class="size-7" />
      </div>
      <div>
        <p class="font-semibold text-highlighted">
          Build your processing pipeline
        </p><p class="mt-2 max-w-64 text-sm leading-relaxed text-muted">
          {{ editable ? 'Drag a processor here to start. Every new step connects automatically.' : 'This workflow has no steps.' }}
        </p>
      </div>
    </div>
    <div v-if="dropIndex !== null || dragDestination !== null" class="pointer-events-none absolute inset-x-0 top-4 flex justify-center" role="status">
      <span class="rounded-full border border-primary/30 bg-default px-4 py-2 text-xs font-medium text-primary shadow-sm">{{ dropIndex !== null ? `Insert as step ${dropIndex + 1}` : `Move to step ${dragDestination! + 1}` }}</span>
    </div>
    <div v-if="dropIndex !== null" class="pointer-events-none absolute inset-1 rounded-lg ring-2 ring-primary/40" />
  </div>
</template>

<style scoped>
.workflow-graph {
  --vf-node-color: var(--ui-text);
  --vf-handle: var(--ui-primary);
  --vf-connection-path: var(--ui-primary);
}
.workflow-graph :deep(.vue-flow__controls) {
  overflow: hidden;
  border: 1px solid var(--ui-border);
  border-radius: 8px;
  box-shadow: none;
}
.workflow-graph :deep(.vue-flow__controls-button) {
  color: var(--ui-text);
  background: var(--ui-bg);
  border-color: var(--ui-border);
}
.workflow-graph :deep(.vue-flow__handle) {
  /* Keep ports aligned across cards with different parameter counts. End ports
     stay mounted (with zero opacity) so Vue Flow can always measure them. */
  top: 28px;
  width: 9px;
  height: 9px;
  background: var(--ui-primary);
  border: 2px solid var(--ui-bg);
}
</style>
