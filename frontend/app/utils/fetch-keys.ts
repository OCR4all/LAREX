type KeyPart = string | number | null | undefined

const normalize = (part: KeyPart) => {
  if (part == null) return ''
  return String(part)
}

export const wsKey = (workspaceId: string, ...parts: KeyPart[]) => {
  const tail = parts
    .map(normalize)
    .filter(Boolean)
    .join(':')

  return tail ? `ws:${workspaceId}:${tail}` : `ws:${workspaceId}`
}

export const globalKey = (...parts: KeyPart[]) => {
  const tail = parts
    .map(normalize)
    .filter(Boolean)
    .join(':')

  return tail ? `global:${tail}` : 'global'
}
