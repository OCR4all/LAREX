import type { TaskCapabilities } from './capabilities'

export * from './editor/rendering'
export * from './capabilities'
export * from './action'
export * from './dataset'

export type WorkspaceMemberRole = 'CURATOR' | 'EDITOR' | 'ADMINISTRATOR' | 'MEMBER'

export interface WorkspaceMember {
  id: string
  userId: string
  role: WorkspaceMemberRole
  invitationStatus: 'PENDING' | 'ACCEPTED' | 'DECLINED'
  created: string
  updated: string
  workspaceId: string
  username?: string
  email?: string
  firstName?: string
  lastName?: string
  displayName?: string
  avatar?: string
}

export type Member = WorkspaceMember

export interface WorkspaceInvitation {
  id: string
  workspaceId: string
  workspaceName: string
  role: WorkspaceMemberRole
  invitedAt: string
}

export const ROLE_LABELS: Record<WorkspaceMemberRole, string> = {
  CURATOR: 'Curator',
  EDITOR: 'Editor',
  ADMINISTRATOR: 'Curator',
  MEMBER: 'Editor'
}

export interface InviteUserRequest {
  userId: string
  role: 'CURATOR' | 'EDITOR'
}

export interface UpdateMemberRoleRequest {
  role: 'CURATOR' | 'EDITOR'
}

export interface UserProfile {
  id: string
  username: string
  email?: string
  firstName?: string
  lastName?: string
  avatar?: string
}

export interface UpdateUserProfileRequest {
  firstName?: string
  lastName?: string
  avatar?: string
}

export interface RecentProject {
  id: string
  projectId: string
  projectName: string
  projectDescription?: string
  projectTags: string[]
  workspaceId: string
  workspaceName: string
  accessType: 'CREATED' | 'VIEWED' | 'EDITED'
  lastAccessed: string
  created: string
}

export interface RecentProjectsResponse {
  projects: RecentProject[]
  totalCount: number
  limit: number
  workspaceId?: string
}

export interface RecordAccessRequest {
  projectId: string
  accessType: 'CREATED' | 'VIEWED' | 'EDITED'
}

export type NotificationType
  = | 'WORKSPACE_INVITATION'
    | 'TASK_ASSIGNED'
    | 'TASK_COMPLETED'
    | 'TASK_REMINDER'
    | 'TASK_MENTIONED'
    | 'TASK_UPDATED'
    | 'TASK_DUE_SOON'
    | 'TASK_OVERDUE'
    | 'TASK_COMMENT_ADDED'
    | 'PROJECT_CREATED'
    | 'PROJECT_DELETED'
    | 'PAGE_CREATED'
    | 'PAGE_DELETED'
    | 'WORKSPACE_WATCH'
    | 'PROJECT_WATCH'
    | 'UPLOAD_COMPLETED'
    | 'UPLOAD_FAILED'
    | 'IMPORT_COMPLETED'
    | 'IMPORT_FAILED'
    | 'COLLAB_TAKEOVER_REQUESTED'
    | 'COLLAB_TAKEOVER_GRANTED'
    | 'COLLAB_TAKEOVER_DECLINED'
    | 'COLLAB_TAKEOVER_FORCED'
    | 'COLLAB_LEASE_EXPIRED'

export interface Notification {
  id: string
  userId: string
  title: string
  message: string
  type: NotificationType
  read: boolean
  relatedEntityId?: string
  relatedEntityType?: string
  link?: string
  created: string
  updated: string
  readAt?: string
}

export interface NotificationGroup {
  id: string
  type: NotificationType
  relatedEntityType?: string
  items: Notification[]
  isExpanded: boolean
  latestCreated: string
}

export interface NotificationPreference {
  notificationType: NotificationType
  emailEnabled: boolean
  desktopEnabled: boolean
  inAppEnabled: boolean
}

export interface NotificationTypeInfo {
  type: NotificationType
  label: string
  description: string
}

export interface UpdatePreferenceRequest {
  notificationType: NotificationType
  emailEnabled?: boolean
  desktopEnabled?: boolean
  inAppEnabled?: boolean
}

export interface BulkUpdatePreferencesRequest {
  preferences: UpdatePreferenceRequest[]
}

export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export interface Task {
  id: string
  title: string
  description: string | null
  createdByUserId: string
  createdBy?: UserProfile | null
  assignedUserIds: string[]
  assignedUsers: UserProfile[]
  status: TaskStatus
  priority: TaskPriority
  dueDate: string | null
  created: string
  updated: string
  completedAt: string | null
  completedByUserId: string | null
  workspaceId: string
  syncLinkedPageStates: boolean
  capabilities?: TaskCapabilities
}

export type TaskActivityType
  = | 'CREATED'
    | 'TITLE_CHANGED'
    | 'DESCRIPTION_CHANGED'
    | 'STATUS_CHANGED'
    | 'PRIORITY_CHANGED'
    | 'DUE_DATE_CHANGED'
    | 'ASSIGNEES_CHANGED'
    | 'COMMENT_ADDED'
    | 'COMMENT_UPDATED'
    | 'COMMENT_DELETED'
    | 'SUBTASK_ADDED'
    | 'SUBTASK_COMPLETED'
    | 'SUBTASK_DELETED'
    | 'LINK_ADDED'
    | 'LINK_REMOVED'

export interface TaskActivityLog {
  id: string
  taskId: string
  userId: string
  user?: UserProfile | null
  activityType: TaskActivityType
  oldValue?: string | null
  newValue?: string | null
  details?: string | null
  created: string
}

export interface TaskComment {
  id: string
  taskId: string
  userId: string
  user?: UserProfile | null
  content: string
  mentionedUserIds: string[]
  mentionedUsers: UserProfile[]
  created: string
  updated: string
}

export type TaskPageLinkType = 'MANUAL' | 'BY_TAG'

export interface TaskProjectLink {
  id: string
  taskId: string
  projectId: string
  projectName: string
  createdByUserId?: string
  created: string
}

export interface TaskPageLink {
  id: string
  taskId: string
  pageId: string
  pageName: string
  projectId?: string
  projectName?: string
  linkType: TaskPageLinkType
  tagFilter?: string
  createdByUserId?: string
  created: string
}

export interface TaskLinks {
  projectLinks: TaskProjectLink[]
  pageLinks: TaskPageLink[]
}

export interface LinkedTask {
  id: string
  title: string
  status: TaskStatus
  priority: TaskPriority
  dueDate?: string | null
  assignedUsers: UserProfile[]
}

export interface TaskReminder {
  id: string
  taskId: string
  userId: string
  reminderTime: string
  sent: boolean
  created: string
}

export interface Subtask {
  id: string
  taskId: string
  title: string
  description?: string | null
  taskDescription?: string | null
  completed: boolean
  sortOrder: number
  completedAt?: string | null
  completedByUserId?: string | null
  completedBy?: UserProfile | null
  created: string
  pageId?: string | null
  pageName?: string | null
  projectId?: string | null
  projectName?: string | null
  assignedUserId?: string | null
  assignedTo?: UserProfile | null
}

export interface SubtaskProgress {
  total: number
  completed: number
  percentage: number
}

export interface BulkOperationResponse {
  successCount: number
  failedCount: number
  failedTaskIds: string[]
  errors: string[]
}
