import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { Modal, message } from 'ant-design-vue'
import type { ModalFuncProps } from 'ant-design-vue'

const configApiMock = vi.hoisted(() => ({
  queryConfigs: vi.fn(),
  addConfig: vi.fn(),
  updateConfig: vi.fn(),
  deleteConfig: vi.fn(),
  testConfig: vi.fn(),
  queryChatGptModels: vi.fn(),
  testSttConfig: vi.fn(),
  testTtsConfig: vi.fn(),
  testVisionConfig: vi.fn(),
  queryPlatformConfig: vi.fn(),
  addPlatformConfig: vi.fn(),
  updatePlatformConfig: vi.fn(),
}))

vi.mock('@/services/config', () => configApiMock)

import ConfigManager from '../ConfigManager.vue'
import type { Config, ConfigType } from '@/types/config'

const confirmMock = vi.mocked(Modal.confirm)

/** 让下一次 Modal.confirm 立刻按用户点了「确定」/「取消」返回 */
function answerConfirmOnce(accepted: boolean) {
  confirmMock.mockImplementationOnce(((props: ModalFuncProps) => {
    if (accepted) {
      props.onOk?.()
    } else {
      props.onCancel?.()
    }
    return { destroy: vi.fn(), update: vi.fn() }
  }) as unknown as typeof Modal.confirm)
}

function lastConfirmProps(): ModalFuncProps {
  const calls = confirmMock.mock.calls as unknown as ModalFuncProps[][]
  const last = calls[calls.length - 1]
  if (!last) {
    throw new Error('Modal.confirm 未被调用')
  }
  return last[0] as ModalFuncProps
}

// <script setup> 的绑定不能经 vm 写回，改值要直接落到 setupState
interface ConfigManagerState {
  formRef: { validate: () => Promise<void>; resetFields: () => void } | undefined
  formData: Partial<Config>
  editingConfigId: number | undefined
  activeTabKey: string
  handleSubmit: () => Promise<void>
  handleEdit: (record: Config) => void
  chatGptCatalog: { refresh: () => Promise<void> }
  handleTest: () => Promise<void>
  handleTestTts: () => Promise<void>
  onTestAudioChange: (event: Event) => void
  onTestImageChange: (event: Event) => void
  clearTestImage: () => void
  testImageFile: File | undefined
  testVoiceName: string
  testSampleText: string
  testResult?: { detail: string; audioUrl?: string; success: boolean }
}

async function mountManager(configType: ConfigType = 'tts') {
  const wrapper = shallowMount(ConfigManager, {
    props: { configType },
    global: { directives: { permission: {} } },
  })
  await flushPromises()
  const state = (wrapper.vm.$ as unknown as { setupState: ConfigManagerState }).setupState

  // 表单方法来自 a-form 实例，shallowMount 下的替身没有，这里给一份直接通过的
  state.formRef = { validate: vi.fn().mockResolvedValue(undefined), resetFields: vi.fn() }
  state.formData = { provider: 'aliyun', configName: '默认音色', isDefault: false }
  return state
}

describe('ConfigManager 表单提交', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    configApiMock.queryChatGptModels.mockResolvedValue({ code: 200, data: [{
      id: 'chat-model', label: 'Chat', defaultModel: true, inputModalities: ['text', 'image'],
      reasoningEfforts: ['low', 'medium', 'high'], defaultReasoningEffort: 'low',
    }] })
    configApiMock.queryConfigs.mockResolvedValue({
      code: 200,
      message: '',
      data: { list: [], total: 0 },
    })
  })

  it('sends the chosen reasoning effort in both a test and a saved configuration', async () => {
    const state = await mountManager('llm')
    state.handleEdit({ configId: 7, configType: 'llm', provider: 'codex-chatgpt', configName: 'codex-default',
      apiUrl: 'http://localhost:8787/v1', enableThinking: false })
    expect(state.formData.reasoningEffort).toBe('low')
    await flushPromises()
    await state.chatGptCatalog.refresh()
    state.formData.reasoningEffort = 'high'
    configApiMock.testConfig.mockResolvedValue({ code: 200, data: { detail: 'ok', elapsedMillis: 1 } })
    await state.handleTest()
    expect(configApiMock.testConfig).toHaveBeenCalledWith(expect.objectContaining({ reasoningEffort: 'high' }))
    configApiMock.updateConfig.mockResolvedValue({ code: 200, data: null })
    await state.handleSubmit()
    expect(configApiMock.updateConfig).toHaveBeenCalledWith(expect.objectContaining({ reasoningEffort: 'high' }), false)
  })

  it('blocks testing and saving an effort unsupported by the selected model', async () => {
    const state = await mountManager('llm')
    state.handleEdit({ configId: 7, configType: 'llm', provider: 'codex-chatgpt', configName: 'codex-default',
      apiUrl: 'http://localhost:8787/v1', reasoningEffort: 'ultra' })
    await flushPromises()
    await state.chatGptCatalog.refresh()
    await state.handleTest()
    await state.handleSubmit()
    expect(configApiMock.testConfig).not.toHaveBeenCalled()
    expect(configApiMock.updateConfig).not.toHaveBeenCalled()
    expect(state.formData.reasoningEffort).toBe('ultra')
  })

  it('新增成功：响应 data 为 null 也判成功，回到列表页并重新拉列表', async () => {
    const state = await mountManager()
    const queriesBefore = configApiMock.queryConfigs.mock.calls.length
    // 写接口的成功响应体是 ApiResponse<Void>，data 恒为 null
    configApiMock.addConfig.mockResolvedValue({ code: 200, data: null, message: '' })

    await state.handleSubmit()
    await flushPromises()

    expect(configApiMock.addConfig).toHaveBeenCalledTimes(1)
    expect(message.success).toHaveBeenCalledWith('config.createSuccess')
    expect(state.activeTabKey).toBe('1')
    expect(configApiMock.queryConfigs.mock.calls.length).toBe(queriesBefore + 1)
  })

  it('换对象存储被拦下：弹确认框展示后端原文，确定后带确认参数重发一次', async () => {
    const state = await mountManager('oss')
    const warning = '当前对象存储上还有 1234 条历史音频/文件，切换后这些内容将永久无法访问'
    // 4090 是后端 ResultStatus.CONFIRM_REQUIRED，写死在这里，改动后端码值时这条会先红
    configApiMock.addConfig
      .mockResolvedValueOnce({ code: 4090, data: null, message: warning })
      .mockResolvedValueOnce({ code: 200, data: null, message: '' })
    answerConfirmOnce(true)

    await state.handleSubmit()
    await flushPromises()

    expect(String(lastConfirmProps().content)).toBe(warning)
    expect(configApiMock.addConfig).toHaveBeenCalledTimes(2)
    expect(configApiMock.addConfig.mock.calls[0]![1]).toBe(false)
    expect(configApiMock.addConfig.mock.calls[1]![1]).toBe(true)
    expect(message.success).toHaveBeenCalledWith('config.createSuccess')
    expect(message.error).not.toHaveBeenCalled()
  })

  it('换对象存储被拦下：取消后什么都不做，不重发也不弹错误', async () => {
    const state = await mountManager('oss')
    configApiMock.addConfig.mockResolvedValueOnce({ code: 4090, data: null, message: '还有历史文件' })
    answerConfirmOnce(false)

    await state.handleSubmit()
    await flushPromises()

    expect(configApiMock.addConfig).toHaveBeenCalledTimes(1)
    expect(message.error).not.toHaveBeenCalled()
    expect(message.success).not.toHaveBeenCalled()
  })

  it('提交失败：弹后端原文，不切回列表页也不重新拉列表', async () => {
    const state = await mountManager()
    const queriesBefore = configApiMock.queryConfigs.mock.calls.length
    state.activeTabKey = '2'
    configApiMock.addConfig.mockResolvedValue({ code: 500, data: null, message: '配置名称重复' })

    await state.handleSubmit()
    await flushPromises()

    expect(message.error).toHaveBeenCalledWith('配置名称重复')
    expect(message.success).not.toHaveBeenCalled()
    expect(state.activeTabKey).toBe('2')
    expect(configApiMock.queryConfigs.mock.calls.length).toBe(queriesBefore)
  })

  it('STT response for a replaced sample cannot overwrite the current test', async () => {
    const state = await mountManager('stt')
    state.formData = { provider: 'openai', configName: 'speech-model', apiUrl: 'https://speech.example/v1' }
    await flushPromises()
    const sample = (name: string) => ({ target: { files: [new File(['RIFF'], name)] } }) as unknown as Event
    state.onTestAudioChange(sample('first.wav'))
    let resolve!: (value: unknown) => void
    configApiMock.testSttConfig.mockReturnValueOnce(new Promise(r => { resolve = r }))
    const first = state.handleTest(); await flushPromises()
    state.onTestAudioChange(sample('second.wav'))
    resolve({ code: 200, message: 'old', data: { success: true, detail: 'old transcript', elapsedMillis: 1 } })
    await first
    expect(state.testResult).toBeUndefined()
    expect(configApiMock.addConfig).not.toHaveBeenCalled()
    configApiMock.testSttConfig.mockResolvedValueOnce({ code: 200, message: 'ok', data: { success: true, detail: 'new transcript', elapsedMillis: 2 } })
    await state.handleTest()
    expect(state.testResult?.detail).toBe('new transcript')
    expect(configApiMock.testSttConfig.mock.calls[1]![1].name).toBe('second.wav')
  })

  it('TTS preview uses the unsaved voice and clears on provider change', async () => {
    const state = await mountManager('tts')
    state.formData = { provider: 'openai', configName: 'speech-model' }
    state.testVoiceName = 'custom-voice'; state.testSampleText = 'Xin chào Ken'
    await flushPromises()
    configApiMock.testTtsConfig.mockResolvedValueOnce({ code: 200, message: 'ok', data: { audioUrl: '/uploads/preview.mp3?sign=fixture' } })
    await state.handleTestTts()
    expect(configApiMock.testTtsConfig).toHaveBeenCalledWith(expect.objectContaining({ provider: 'openai', voiceName: 'custom-voice', sampleText: 'Xin chào Ken' }))
    expect(state.testResult?.audioUrl).toContain('preview.mp3')
    state.formData.provider = 'edge'; await flushPromises()
    expect(state.testResult).toBeUndefined()
    expect(configApiMock.addConfig).not.toHaveBeenCalled()
  })

  it('LLM test displays the actual answer to the entered question', async () => {
    const state = await mountManager('llm')
    state.testSampleText = 'Bạn tên gì?'; await flushPromises()
    configApiMock.testConfig.mockResolvedValueOnce({ code: 200, message: 'ok', data: { success: true, detail: 'Tôi là Ken', elapsedMillis: 5 } })
    await state.handleTest()
    expect(configApiMock.testConfig).toHaveBeenCalledWith(expect.objectContaining({ sampleText: 'Bạn tên gì?' }))
    expect(state.testResult?.detail).toBe('Tôi là Ken')
  })

  it('Vision sends the unsaved question and attached file, then uses text only when removed', async () => {
    const state = await mountManager('llm')
    state.formData = { provider: 'openai', configName: 'vision-model', modelType: 'vision' }
    state.testSampleText = 'Hãy đọc chữ trong ảnh bằng tiếng Việt.'
    await flushPromises()
    const image = new File(['image'], 'sample.png', { type: 'image/png' })
    const input = { files: [image], value: 'sample.png' }
    state.onTestImageChange({ target: input } as unknown as Event)
    configApiMock.testVisionConfig.mockResolvedValueOnce({ code: 200, data: { detail: 'Chữ trong ảnh', elapsedMillis: 5 } })

    await state.handleTest()
    expect(configApiMock.testVisionConfig).toHaveBeenCalledWith(expect.objectContaining({
      modelType: 'vision', configName: 'vision-model', sampleText: 'Hãy đọc chữ trong ảnh bằng tiếng Việt.',
    }), image)
    expect(input.value).toBe('sample.png')
    expect(state.testResult?.detail).toBe('Chữ trong ảnh')
    expect(configApiMock.testConfig).not.toHaveBeenCalled()

    state.clearTestImage()
    expect(state.testImageFile).toBeUndefined()
    expect(state.testResult).toBeUndefined()
    configApiMock.testConfig.mockResolvedValueOnce({ code: 200, data: { detail: 'Văn bản', elapsedMillis: 1 } })
    await state.handleTest()
    expect(configApiMock.testConfig).toHaveBeenCalledWith(expect.objectContaining({ sampleText: 'Hãy đọc chữ trong ảnh bằng tiếng Việt.' }))
    expect(configApiMock.addConfig).not.toHaveBeenCalled()
    expect(configApiMock.updateConfig).not.toHaveBeenCalled()
  })

  it('Vision ignores a pending result after replacing the attached image', async () => {
    const state = await mountManager('llm')
    state.formData.modelType = 'vision'
    await flushPromises()
    const sample = (name: string) => ({ target: { files: [new File(['image'], name, { type: 'image/png' })], value: name } }) as unknown as Event
    state.onTestImageChange(sample('first.png'))
    let resolve!: (value: unknown) => void
    configApiMock.testVisionConfig.mockReturnValueOnce(new Promise(r => { resolve = r }))
    const first = state.handleTest()
    await flushPromises()
    state.onTestImageChange(sample('second.png'))
    resolve({ code: 200, data: { detail: 'old image', elapsedMillis: 1 } })
    await first
    expect(state.testResult).toBeUndefined()
    state.formData.modelType = 'chat'
    await flushPromises()
    expect(state.testImageFile).toBeUndefined()
  })

  it('Vision rejects unsupported, empty and oversized attachments and blank content', async () => {
    const state = await mountManager('llm')
    state.formData.modelType = 'vision'
    await flushPromises()
    const oversized = new File(['image'], 'large.png', { type: 'image/png' })
    Object.defineProperty(oversized, 'size', { value: 5 * 1024 * 1024 + 1 })
    for (const file of [new File(['document'], 'doc.pdf', { type: 'application/pdf' }), new File([], 'empty.png'), oversized]) {
      const input = { files: [file], value: file.name }
      state.onTestImageChange({ target: input } as unknown as Event)
      expect(state.testImageFile).toBeUndefined()
      expect(input.value).toBe('')
    }
    state.testSampleText = '  '
    await flushPromises()
    await state.handleTest()
    expect(configApiMock.testConfig).not.toHaveBeenCalled()
    expect(configApiMock.testVisionConfig).not.toHaveBeenCalled()
  })
})
