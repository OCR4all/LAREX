// @vitest-environment happy-dom
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useRealtimeSocket } from '../use-realtime-socket'

const sockets: TestWebSocket[] = []

class TestWebSocket {
  static readonly OPEN = 1
  readyState = 0
  onopen?: () => void
  onmessage?: (event: MessageEvent) => void
  onclose?: (event: CloseEvent) => void
  send = vi.fn()
  close = vi.fn(() => { this.readyState = 2 })

  constructor(readonly url: string) {
    sockets.push(this)
  }

  open() {
    this.readyState = TestWebSocket.OPEN
    this.onopen?.()
  }

  receive(type: string) {
    this.onmessage?.(new MessageEvent('message', { data: JSON.stringify({ type }) }))
  }

  finishClose() {
    this.readyState = 3
    this.onclose?.(new CloseEvent('close'))
  }
}

const loggedIn = ref(true)
let page: EventTarget

beforeEach(() => {
  vi.useFakeTimers()
  sockets.length = 0
  loggedIn.value = true
  const app = {}
  page = Object.assign(new EventTarget(), {
    location: { protocol: 'https:', host: 'larex.example' }
  })
  vi.stubGlobal('window', page)
  vi.stubGlobal('document', Object.assign(new EventTarget(), { visibilityState: 'visible' }))
  vi.stubGlobal('WebSocket', TestWebSocket)
  vi.stubGlobal('useNuxtApp', () => app)
  vi.stubGlobal('useUserSession', () => ({ loggedIn }))
})

afterEach(() => {
  vi.clearAllTimers()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

it('shares one transport and waits for the application acknowledgement without buffering sends', () => {
  const realtime = useRealtimeSocket()
  const otherConsumer = useRealtimeSocket()
  const subscriber = vi.fn()
  const unsubscribe = otherConsumer.subscribe(subscriber)

  realtime.connect()
  otherConsumer.connect()

  expect(sockets).toHaveLength(1)
  const socket = sockets[0]!
  expect(socket.url).toBe('wss://larex.example/_ws')
  expect(realtime.send({ type: 'OFFLINE' })).toBe(false)
  socket.open()
  expect(otherConsumer.connectionStatus.value).toBe('connecting')
  expect(socket.send).not.toHaveBeenCalled()

  socket.receive('CONNECTED')
  expect(otherConsumer.connectionStatus.value).toBe('connected')
  expect(subscriber).toHaveBeenCalledWith({ type: 'CONNECTED' })
  expect(realtime.send({ type: 'ONLINE' })).toBe(true)
  expect(socket.send).toHaveBeenCalledExactlyOnceWith('{"type":"ONLINE"}')

  unsubscribe()
  socket.receive('UPDATE')
  expect(subscriber).toHaveBeenCalledTimes(1)
})

it('reconnects after a network close and checks the session when the retry fires', () => {
  const realtime = useRealtimeSocket()
  realtime.connect()
  sockets[0]!.finishClose()

  expect(realtime.connectionStatus.value).toBe('disconnected')
  vi.advanceTimersByTime(4999)
  expect(sockets).toHaveLength(1)
  vi.advanceTimersByTime(1)
  expect(sockets).toHaveLength(2)
  expect(realtime.connectionStatus.value).toBe('connecting')

  sockets[1]!.finishClose()
  loggedIn.value = false
  vi.advanceTimersByTime(5000)
  expect(sockets).toHaveLength(2)
})

it('closes on pagehide and ignores a delayed close after pageshow', () => {
  const realtime = useRealtimeSocket()
  realtime.connect()
  const previous = sockets[0]!
  previous.open()
  previous.receive('CONNECTED')

  page.dispatchEvent(new Event('pagehide'))
  expect(previous.close).toHaveBeenCalledWith(1000, 'page unload')
  expect(realtime.isPageVisible.value).toBe(false)
  expect(realtime.connectionStatus.value).toBe('idle')

  page.dispatchEvent(new Event('pageshow'))
  expect(realtime.isPageVisible.value).toBe(true)
  expect(sockets).toHaveLength(2)
  sockets[1]!.open()
  sockets[1]!.receive('CONNECTED')
  previous.finishClose()

  vi.advanceTimersByTime(5000)
  expect(realtime.connectionStatus.value).toBe('connected')
  expect(sockets).toHaveLength(2)
})
