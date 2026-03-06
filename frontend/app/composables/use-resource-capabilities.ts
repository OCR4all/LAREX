import type {
  ProjectCapabilities,
  ResourceCapabilities,
  TaskCapabilities
} from '@/types/capabilities'
import {
  DEFAULT_PROJECT_CAPABILITIES,
  DEFAULT_RESOURCE_CAPABILITIES,
  DEFAULT_TASK_CAPABILITIES
} from '@/types/capabilities'

type CapabilityKind = 'project' | 'task' | 'resource'

type CapabilityByKind<T extends CapabilityKind> =
  T extends 'project'
    ? ProjectCapabilities
    : T extends 'task'
      ? TaskCapabilities
      : ResourceCapabilities

type ResourceWithCapabilities<T extends CapabilityKind> = {
  capabilities?: Partial<CapabilityByKind<T>> | null
} | null | undefined

export function useResourceCapabilities<T extends CapabilityKind>(
  resource: MaybeRef<ResourceWithCapabilities<T>>,
  kind: T
) {
  const defaults = (() => {
    switch (kind) {
      case 'project':
        return DEFAULT_PROJECT_CAPABILITIES
      case 'task':
        return DEFAULT_TASK_CAPABILITIES
      case 'resource':
      default:
        return DEFAULT_RESOURCE_CAPABILITIES
    }
  })() as CapabilityByKind<T>

  return computed<CapabilityByKind<T>>(() => ({
    ...defaults,
    ...(toValue(resource)?.capabilities ?? {})
  }))
}
