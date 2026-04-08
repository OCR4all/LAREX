import type { Notification } from '~/types'

export function getNotificationLink(notification: Notification): string | undefined {
  if (notification.link) return notification.link
  if (!notification.relatedEntityId) return undefined
  if (notification.type === 'PROJECT_DELETED') return undefined

  switch (notification.type) {
    case 'PROJECT_CREATED':
    case 'UPLOAD_COMPLETED':
    case 'UPLOAD_FAILED':
    case 'IMPORT_COMPLETED':
    case 'IMPORT_FAILED':
      return notification.relatedEntityType === 'Project'
        ? `/project/${notification.relatedEntityId}`
        : undefined

    case 'PAGE_DELETED':
      return notification.relatedEntityType === 'Project'
        ? `/project/${notification.relatedEntityId}`
        : undefined

    case 'PAGE_CREATED':
      return undefined

    case 'TASK_ASSIGNED':
    case 'TASK_COMPLETED':
      return notification.relatedEntityType === 'Task'
        ? `/tasks?taskId=${encodeURIComponent(notification.relatedEntityId)}`
        : '/tasks'

    default:
      return undefined
  }
}
