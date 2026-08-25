export type ProjectDefaultPropagationScope = 'FUTURE_ONLY' | 'UNSET_ONLY' | 'ALL'

export type ProjectDefaultKey = 'CODEC'
  | 'LABEL_SET'
  | 'DICTIONARY'
  | 'TAG_SET'
  | 'NORMALIZATION_PROFILE'
  | 'VALIDATION_RULESET'
  | 'TEXT_INDICES'

export interface ProjectDefaultsImpact {
  affectedProjects: number
  skippedLockedProjects: number
}

export interface ProjectDefaultsPreview {
  changedDefaults: ProjectDefaultKey[]
  unsetOnly: ProjectDefaultsImpact
  all: ProjectDefaultsImpact
}

export interface ProjectDefaultsPropagationResult {
  scope: ProjectDefaultPropagationScope
  updatedProjects: number
  skippedLockedProjects: number
}
