type VisibilityGuard = boolean | null | undefined | (() => boolean | null | undefined)

export function useActionVisibility() {
  const allow = (guard: VisibilityGuard): boolean => {
    const resolved = typeof guard === 'function' ? guard() : guard
    return resolved === true
  }

  const compactGroups = <T>(groups: T[][]): T[][] => groups.filter(group => group.length > 0)

  return {
    allow,
    compactGroups
  }
}
