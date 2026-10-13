import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, ref } from 'vue'
import type { Config } from '@/types/config'

const catalog = vi.hoisted(() => vi.fn())
vi.mock('@/services/config', () => ({ queryChatGptModels: catalog }))
import { useChatGptModels, effectiveReasoningEffort } from '../useChatGptModels'

const models = [
  { id: 'chat-model', label: 'Chat', defaultModel: true, inputModalities: ['text'], reasoningEfforts: ['low', 'medium', 'high'] },
  { id: 'vision-model', label: 'Vision', defaultModel: false, inputModalities: ['text', 'image'], reasoningEfforts: ['none', 'low'] },
]

describe('ChatGPT account model catalog', () => {
  const scopes: ReturnType<typeof effectScope>[] = []
  function setup(initial: Partial<Config> = { configId: 7, apiUrl: 'http://localhost:8787/v1', modelType: 'chat' }) {
    const config = ref(initial)
    const active = ref(true)
    const scope = effectScope()
    scopes.push(scope)
    const state = scope.run(() => useChatGptModels(() => ({ ...config.value }), () => active.value))!
    return { config, active, state }
  }
  beforeEach(() => {
    vi.useFakeTimers()
    catalog.mockReset().mockResolvedValue({ code: 200, data: models })
  })
  afterEach(() => { scopes.splice(0).forEach(scope => scope.stop()); vi.useRealTimers() })

  it('uses the chosen model’s efforts and resolves the default alias without another request', async () => {
    const { state, config } = setup({ configId: 7, apiUrl: 'http://localhost:8787/v1', configName: 'codex-default' })
    await vi.advanceTimersByTimeAsync(350)
    expect(state.reasoningEfforts.value).toEqual(['low', 'medium', 'high'])
    config.value.configName = 'vision-model'
    await nextTick()
    expect(state.reasoningEfforts.value).toEqual(['none', 'low'])
    config.value.configName = 'removed-model'
    await nextTick()
    expect(state.reasoningEfforts.value).toEqual([])
    expect(catalog).toHaveBeenCalledTimes(1)
  })

  it('preserves explicit effort and maps legacy configurations to their previous behavior', () => {
    expect(effectiveReasoningEffort({ enableThinking: false })).toBe('low')
    expect(effectiveReasoningEffort({ enableThinking: true })).toBe('medium')
    expect(effectiveReasoningEffort({ enableThinking: true, reasoningEffort: 'none' })).toBe('none')
  })

  it('loads an edited configuration without requiring its saved token in the browser', async () => {
    const { state, config } = setup()
    await vi.advanceTimersByTimeAsync(350)
    expect(catalog).toHaveBeenCalledWith(expect.objectContaining({ configId: 7, apiKey: undefined }))
    expect(state.options.value.map(model => model.value)).toEqual(['codex-default', 'chat-model', 'vision-model'])
    config.value.configName = 'vision-model'
    await nextTick()
    await vi.advanceTimersByTimeAsync(500)
    expect(catalog).toHaveBeenCalledTimes(1)
  })

  it('requires credentials for a new configuration and only shows image models for Vision', async () => {
    const { config, state } = setup({ apiUrl: 'http://localhost:8787/v1', modelType: 'vision' })
    await vi.advanceTimersByTimeAsync(500)
    expect(catalog).not.toHaveBeenCalled()
    config.value.apiKey = 'new-private-gateway-token'
    await nextTick()
    await vi.advanceTimersByTimeAsync(350)
    expect(state.options.value.map(model => model.value)).toEqual(['vision-model'])
  })

  it('discards responses from a previously edited configuration', async () => {
    let finish!: (value: unknown) => void
    catalog.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    const { config, state } = setup()
    await vi.advanceTimersByTimeAsync(350)
    config.value.configId = 8
    await nextTick()
    await vi.advanceTimersByTimeAsync(350)
    finish({ code: 200, data: [{ ...models[0], id: 'wrong-account-model' }] })
    await nextTick()
    expect(state.models.value).toEqual(models)
  })

  it('keeps the saved model on failure and supports an explicit retry', async () => {
    catalog.mockRejectedValueOnce(new Error('gateway offline'))
    const { state } = setup({ configId: 7, apiUrl: 'http://localhost:8787/v1', configName: 'saved-model' })
    await vi.advanceTimersByTimeAsync(350)
    expect(state.error.value).toBe(true)
    expect(state.options.value.some(model => model.value === 'saved-model')).toBe(true)
    await state.refresh()
    expect(state.error.value).toBe(false)
    expect(state.models.value).toEqual(models)
  })
})
