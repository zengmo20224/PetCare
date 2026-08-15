/**
 * sendMessageStream SSE 消费契约（M8.5 回归守卫）。
 *
 * 2026-08-15 验收缺陷：后端发完 done 事件后若代理层不结束响应流
 * （vite/node http-proxy 对无 chunked 终止块的响应不触发 reader done），
 * 前端 fetch reader 永远挂起，聊天页卡"正在思考"且输入框锁死。
 * 契约：收到 done 事件必须立即结束回调并主动 cancel reader，不等待流关闭。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

vi.mock('@/api/request', () => ({
  http: { post: vi.fn(), get: vi.fn() },
  getRequestBaseUrls: () => [''],
}))

import { sendMessageStream } from '@/api/ai'

function sseFrame(event: string, data: string): Uint8Array {
  return new TextEncoder().encode(`event:${event}\ndata:${data}\n\n`)
}

/** 构造一个发出给定帧后永远不结束的 reader（复现缺陷场景）。 */
function hangingReader(frames: Uint8Array[]) {
  let i = 0
  return {
    read: async (): Promise<ReadableStreamReadResult<Uint8Array>> => {
      if (i < frames.length) {
        return { done: false, value: frames[i++] }
      }
      return new Promise(() => {}) // 永远 pending：模拟代理不结束流
    },
    cancel: vi.fn(async () => {}),
  }
}

describe('sendMessageStream SSE 契约', () => {
  const originalFetch = globalThis.fetch
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    ;(globalThis as any).uni = { getStorageSync: () => 'test-token' }
    fetchMock = vi.fn()
    ;(globalThis as any).fetch = fetchMock
  })

  afterEach(() => {
    ;(globalThis as any).fetch = originalFetch
    delete (globalThis as any).uni
  })

  it('收到 done 事件立即触发 onDone 并 cancel reader，不等流关闭', async () => {
    const reader = hangingReader([sseFrame('chunk', '你好'), sseFrame('done', '[DONE]')])
    fetchMock.mockResolvedValue({ ok: true, status: 200, body: { getReader: () => reader } })

    const onDone = vi.fn()
    await sendMessageStream('1', '几点开门', {
      onChunk: vi.fn(),
      onDone,
      onError: vi.fn(),
    })

    expect(onDone).toHaveBeenCalledTimes(1)
    expect(reader.cancel).toHaveBeenCalled()
  })

  it('收到 error 事件立即触发 onError 并 cancel reader', async () => {
    const reader = hangingReader([sseFrame('error', 'AI 暂时无法回复')])
    fetchMock.mockResolvedValue({ ok: true, status: 200, body: { getReader: () => reader } })

    const onError = vi.fn()
    await sendMessageStream('1', '查询', {
      onChunk: vi.fn(),
      onDone: vi.fn(),
      onError,
    })

    expect(onError).toHaveBeenCalledTimes(1)
    expect(reader.cancel).toHaveBeenCalled()
  })

  it('chunk 帧文本完整透传 onChunk', async () => {
    const reader = hangingReader([
      sseFrame('chunk', '第一段。'),
      sseFrame('chunk', '第二段。'),
      sseFrame('done', '[DONE]'),
    ])
    fetchMock.mockResolvedValue({ ok: true, status: 200, body: { getReader: () => reader } })

    const onChunk = vi.fn()
    await sendMessageStream('1', 'hi', { onChunk, onDone: vi.fn(), onError: vi.fn() })

    expect(onChunk).toHaveBeenCalledTimes(2)
    expect(onChunk).toHaveBeenNthCalledWith(1, '第一段。')
    expect(onChunk).toHaveBeenNthCalledWith(2, '第二段。')
  })

  it('流自然关闭（无 done 帧）仍触发 onDone 兜底', async () => {
    let i = 0
    const frames = [sseFrame('chunk', '部分')]
    const reader = {
      read: async () => (i < frames.length ? { done: false, value: frames[i++] } : { done: true as const }),
      cancel: vi.fn(async () => {}),
    }
    fetchMock.mockResolvedValue({ ok: true, status: 200, body: { getReader: () => reader } })

    const onDone = vi.fn()
    await sendMessageStream('1', 'hi', { onChunk: vi.fn(), onDone, onError: vi.fn() })

    expect(onDone).toHaveBeenCalledTimes(1)
  })

  it('HTTP 非 2xx 触发 onError', async () => {
    fetchMock.mockResolvedValue({ ok: false, status: 503, body: null })
    const onError = vi.fn()
    await sendMessageStream('1', 'hi', { onChunk: vi.fn(), onDone: vi.fn(), onError })
    expect(onError).toHaveBeenCalledWith(expect.stringContaining('503'))
  })
})
