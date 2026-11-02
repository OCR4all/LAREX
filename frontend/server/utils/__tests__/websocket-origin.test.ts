import { describe, expect, it } from 'vitest'
import { isSameOriginWebSocket } from '../websocket-origin'

describe('isSameOriginWebSocket', () => {
  it('accepts handshakes from the instance itself', () => {
    expect(isSameOriginWebSocket(new Headers({ origin: 'https://larex.example.org', host: 'larex.example.org' }))).toBe(true)
    expect(isSameOriginWebSocket(new Headers({ 'origin': 'http://larex.localhost', 'host': 'frontend:3000', 'x-forwarded-host': 'larex.localhost' }))).toBe(true)
    expect(isSameOriginWebSocket(new Headers({ origin: 'http://localhost:3000', host: 'localhost:3000' }))).toBe(true)
  })

  it('rejects handshakes from other sites', () => {
    expect(isSameOriginWebSocket(new Headers({ origin: 'https://evil.example', host: 'larex.example.org' }))).toBe(false)
    expect(isSameOriginWebSocket(new Headers({ origin: 'https://larex.example.org.evil.example', host: 'larex.example.org' }))).toBe(false)
    expect(isSameOriginWebSocket(new Headers({ origin: 'null', host: 'larex.example.org' }))).toBe(false)
  })

  it('leaves non-browser clients to the session check', () => {
    expect(isSameOriginWebSocket(new Headers({ host: 'larex.example.org' }))).toBe(true)
  })
})
