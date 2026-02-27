export function isExpectedDisconnectError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error ?? '')
  const normalizedMessage = message.toLowerCase()

  return [
    'closed',
    'econnreset',
    'epipe',
    'aborted',
    'socket hang up',
    'premature close',
    'invalid state: writable stream is closed'
  ].some(fragment => normalizedMessage.includes(fragment))
}
