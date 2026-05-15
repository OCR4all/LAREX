export type AdminUserOnboardingState = 'ACTIVE' | 'PENDING_SETUP' | 'DISABLED' | 'SERVICE_ACCOUNT'
export type AdminUserStatusFilter = 'ALL' | 'ACTIVE' | 'PENDING_SETUP' | 'DISABLED'
export type AdminUserAuditAction =
  | 'CREATE'
  | 'ENABLE'
  | 'DISABLE'
  | 'RESEND_SETUP_EMAIL'
  | 'GLOBAL_CURATOR_GRANT'
  | 'GLOBAL_CURATOR_REVOKE'
export type AdminUserAuditOutcome = 'SUCCESS' | 'FAILURE'
export type AdminUserIdentitySource = 'LOCAL' | 'LDAP' | 'SERVICE_ACCOUNT'

export interface AdminUser {
  id: string
  username: string
  email?: string | null
  firstName?: string | null
  lastName?: string | null
  avatar?: string | null
  enabled: boolean
  emailVerified: boolean
  serviceAccount: boolean
  externallyManaged: boolean
  identitySource: AdminUserIdentitySource
  onboardingState: AdminUserOnboardingState
  createdTimestamp?: string | null
}

export interface AdminUserPage {
  items: AdminUser[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  creationAllowed: boolean
  setupEmailAllowed: boolean
}

export interface AdminUserAuditEvent {
  id: string
  action: AdminUserAuditAction
  outcome: AdminUserAuditOutcome
  actorUserId: string
  actorUsername: string
  created?: string | null
  details?: string | null
}

export interface AdminGlobalRoles {
  globalAdmin: boolean
  globalCurator: boolean
}

export interface ErrorResponseData {
  code?: string
  message?: string
  details?: string[]
}
