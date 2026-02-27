<script setup lang="ts">
import type { TaskActivityLog, TaskActivityType } from '~/types/index'
import { formatDistanceToNow, parseISO, differenceInMinutes } from 'date-fns'
import { computed } from 'vue'

const props = defineProps<{
  activity: TaskActivityLog[]
}>()

type ActivityGroup = {
  id: string
  activityType: TaskActivityType
  userId: string
  user?: TaskActivityLog['user']
  count: number
  created: string
  logs: TaskActivityLog[]
}

const clusterableTypes = new Set<TaskActivityType>([
  'SUBTASK_ADDED',
  'SUBTASK_COMPLETED',
  'SUBTASK_DELETED'
])

const clusteredActivity = computed<ActivityGroup[]>(() => {
  if (!props.activity.length) return []

  const groups: ActivityGroup[] = []

  for (const log of props.activity) {
    const lastGroup = groups[groups.length - 1]
    const shouldCluster =
      lastGroup &&
      clusterableTypes.has(log.activityType) &&
      lastGroup.activityType === log.activityType &&
      lastGroup.userId === log.userId &&
      Math.abs(
        differenceInMinutes(
          parseISO(lastGroup.logs[lastGroup.logs.length - 1].created),
          parseISO(log.created)
        )
      ) <= 10

    if (shouldCluster) {
      lastGroup.count += 1
      lastGroup.logs.push(log)
      continue
    }

    groups.push({
      id: log.id,
      activityType: log.activityType,
      userId: log.userId,
      user: log.user,
      count: 1,
      created: log.created,
      logs: [log]
    })
  }

  return groups
})

function formatTime(dateString: string) {
  return formatDistanceToNow(parseISO(dateString), { addSuffix: true })
}

function getDisplayName(log: TaskActivityLog) {
  if (log.user?.firstName && log.user?.lastName) {
    return `${log.user.firstName} ${log.user.lastName}`
  }
  return log.user?.username || 'Unknown'
}

function getActivityIcon(type: TaskActivityType): string {
  switch (type) {
    case 'CREATED':
      return 'i-lucide-plus-circle'
    case 'TITLE_CHANGED':
      return 'i-lucide-type'
    case 'DESCRIPTION_CHANGED':
      return 'i-lucide-file-text'
    case 'STATUS_CHANGED':
      return 'i-lucide-git-branch'
    case 'PRIORITY_CHANGED':
      return 'i-lucide-flag'
    case 'DUE_DATE_CHANGED':
      return 'i-lucide-calendar'
    case 'ASSIGNEES_CHANGED':
      return 'i-lucide-users'
    case 'COMMENT_ADDED':
      return 'i-lucide-message-square-plus'
    case 'COMMENT_UPDATED':
      return 'i-lucide-message-square'
    case 'COMMENT_DELETED':
      return 'i-lucide-message-square-x'
    case 'SUBTASK_ADDED':
      return 'i-lucide-list-plus'
    case 'SUBTASK_COMPLETED':
      return 'i-lucide-check-square'
    case 'SUBTASK_DELETED':
      return 'i-lucide-list-x'
    case 'LINK_ADDED':
      return 'i-lucide-link'
    case 'LINK_REMOVED':
      return 'i-lucide-unlink'
    default:
      return 'i-lucide-activity'
  }
}

function getActivityDescription(log: TaskActivityLog): string {
  const name = getDisplayName(log)
  const details = log.details

  const parseJson = <T>(value?: string | null): T | null => {
    if (!value) return null
    try {
      return JSON.parse(value) as T
    } catch {
      return null
    }
  }

  switch (log.activityType) {
    case 'CREATED':
      return `${name} created this task`
    case 'TITLE_CHANGED':
      return `${name} changed the title`
    case 'DESCRIPTION_CHANGED':
      return `${name} updated the description`
    case 'STATUS_CHANGED':
      return `${name} changed status from ${log.oldValue?.replace('_', ' ')} to ${log.newValue?.replace('_', ' ')}`
    case 'PRIORITY_CHANGED':
      return `${name} changed priority from ${log.oldValue} to ${log.newValue}`
    case 'DUE_DATE_CHANGED':
      if (!log.oldValue && log.newValue) {
        return `${name} set due date`
      } else if (log.oldValue && !log.newValue) {
        return `${name} removed due date`
      }
      return `${name} changed due date`
    case 'ASSIGNEES_CHANGED':
      {
        const parsed = parseJson<{ added?: string[]; removed?: string[] }>(details)
        const addedCount = parsed?.added?.length ?? 0
        const removedCount = parsed?.removed?.length ?? 0
        if (addedCount || removedCount) {
          return `${name} updated assignees (${addedCount} added, ${removedCount} removed)`
        }
        return `${name} updated assignees`
      }
    case 'COMMENT_ADDED':
      return `${name} added a comment`
    case 'COMMENT_UPDATED':
      return `${name} edited a comment`
    case 'COMMENT_DELETED':
      return `${name} deleted a comment`
    case 'SUBTASK_ADDED':
      return details ? `${name} added “${details}”` : `${name} added a subtask`
    case 'SUBTASK_COMPLETED':
      return details ? `${name} completed “${details}”` : `${name} completed a subtask`
    case 'SUBTASK_DELETED':
      return details ? `${name} deleted “${details}”` : `${name} deleted a subtask`
    case 'LINK_ADDED':
      {
        const parsed = parseJson<{ type?: string; id?: string }>(details)
        if (parsed?.type) {
          return `${name} linked ${parsed.type}`
        }
        return `${name} linked to a page/project`
      }
    case 'LINK_REMOVED':
      {
        const parsed = parseJson<{ type?: string; id?: string }>(details)
        if (parsed?.type) {
          return `${name} removed ${parsed.type}`
        }
        return `${name} removed a link`
      }
    default:
      return `${name} made a change`
  }
}

function getGroupedDescription(group: ActivityGroup): string {
  if (group.count <= 1) {
    return getActivityDescription(group.logs[0])
  }

  const name = getDisplayName(group.logs[0])

  switch (group.activityType) {
    case 'SUBTASK_ADDED':
      return `${name} added ${group.count} subtasks`
    case 'SUBTASK_COMPLETED':
      return `${name} completed ${group.count} subtasks`
    case 'SUBTASK_DELETED':
      return `${name} deleted ${group.count} subtasks`
    default:
      return `${name} made ${group.count} updates`
  }
}
</script>

<template>
  <div class="space-y-4">
    <div v-if="clusteredActivity.length > 0" class="space-y-3">
      <div
        v-for="group in clusteredActivity"
        :key="group.id"
        class="flex items-start gap-3"
      >
        <div class="flex-shrink-0 w-8 h-8 rounded-full bg-elevated/50 flex items-center justify-center">
          <UIcon :name="getActivityIcon(group.activityType)" class="size-4 text-muted" />
        </div>
        <div class="flex-1 min-w-0 pt-1">
          <p class="text-sm">{{ getGroupedDescription(group) }}</p>
          <p class="text-xs text-muted mt-0.5">{{ formatTime(group.created) }}</p>
        </div>
      </div>
    </div>

    <div v-else class="text-center py-8 text-muted">
      <UIcon name="i-lucide-activity" class="size-8 mb-2 mx-auto opacity-50" />
      <p class="text-sm">No activity yet</p>
    </div>
  </div>
</template>
