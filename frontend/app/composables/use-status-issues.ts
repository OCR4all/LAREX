export type StatusIssueSeverity = 'error' | 'warning' | 'info'

export interface StatusIssue {
  id: string
  source: string
  title: string
  message: string
  severity: StatusIssueSeverity
  retryLabel?: string
  updatedAt: string
}

export interface StatusIssueInput {
  id: string
  source: string
  title: string
  message: string
  severity?: StatusIssueSeverity
  retryLabel?: string
  retry?: () => Promise<unknown> | unknown
}

const retryHandlers = new Map<string, () => Promise<unknown> | unknown>()

function severityRank(severity: StatusIssueSeverity): number {
  if (severity === 'error') return 0
  if (severity === 'warning') return 1
  return 2
}

function sortIssues(issues: StatusIssue[]): StatusIssue[] {
  return [...issues].sort((left, right) => {
    const severityDelta = severityRank(left.severity) - severityRank(right.severity)
    if (severityDelta !== 0) return severityDelta
    return Date.parse(right.updatedAt) - Date.parse(left.updatedAt)
  })
}

export function useStatusIssues() {
  const issues = useState<StatusIssue[]>('app.statusIssues', () => [])
  const retryingIds = useState<string[]>('app.statusIssues.retrying', () => [])

  const hasIssues = computed(() => issues.value.length > 0)

  function reportIssue(input: StatusIssueInput) {
    const nextIssue: StatusIssue = {
      id: input.id,
      source: input.source,
      title: input.title,
      message: input.message,
      severity: input.severity ?? 'warning',
      retryLabel: input.retryLabel,
      updatedAt: new Date().toISOString()
    }

    if (input.retry) {
      retryHandlers.set(input.id, input.retry)
    } else {
      retryHandlers.delete(input.id)
    }

    const existing = issues.value.filter(issue => issue.id !== input.id)
    issues.value = sortIssues([nextIssue, ...existing])
  }

  function resolveIssue(issueId: string) {
    retryHandlers.delete(issueId)
    issues.value = issues.value.filter(issue => issue.id !== issueId)
    retryingIds.value = retryingIds.value.filter(id => id !== issueId)
  }

  function isRetrying(issueId: string): boolean {
    return retryingIds.value.includes(issueId)
  }

  async function retryIssue(issueId: string): Promise<boolean> {
    const retryHandler = retryHandlers.get(issueId)
    if (!retryHandler || isRetrying(issueId)) {
      return false
    }

    retryingIds.value = [...retryingIds.value, issueId]
    try {
      await retryHandler()
      return true
    } finally {
      retryingIds.value = retryingIds.value.filter(id => id !== issueId)
    }
  }

  function clearAllIssues() {
    for (const issue of issues.value) {
      retryHandlers.delete(issue.id)
    }
    issues.value = []
    retryingIds.value = []
  }

  return {
    issues: readonly(issues),
    hasIssues,
    reportIssue,
    resolveIssue,
    retryIssue,
    isRetrying,
    clearAllIssues
  }
}
