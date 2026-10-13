import { computed, onScopeDispose, ref, watch } from 'vue'
import { queryChatGptModels } from '@/services/config'
import type { AvailableChatGptModel, Config, LlmModelOption } from '@/types/config'

export function effectiveReasoningEffort(config: Partial<Config>): string {
  return config.reasoningEffort || (config.enableThinking ? 'medium' : 'low')
}

/** Catalogs belong to the current gateway configuration, never to another edited row. */
export function useChatGptModels(getConfig: () => Partial<Config>, isActive: () => boolean) {
  const models = ref<AvailableChatGptModel[]>([])
  const loading = ref(false)
  const error = ref(false)
  let ticket = 0
  let timer: ReturnType<typeof setTimeout> | undefined
  const canLoad = computed(() => isActive() && !!getConfig().apiUrl?.trim()
    && (!!getConfig().apiKey?.trim() || getConfig().configId !== undefined))

  async function refresh() {
    clearTimeout(timer)
    const seq = ++ticket
    error.value = false
    loading.value = false
    if (!canLoad.value) return
    const config = getConfig()
    loading.value = true
    try {
      const result = await queryChatGptModels({
        configId: config.configId, configType: 'llm', provider: 'codex-chatgpt',
        configName: 'codex-default', apiUrl: config.apiUrl, apiKey: config.apiKey,
      })
      if (seq !== ticket) return
      if (result.code !== 200 || !Array.isArray(result.data) || result.data.length === 0) {
        error.value = true
        return
      }
      models.value = result.data
    } catch {
      if (seq === ticket) error.value = true
    } finally {
      if (seq === ticket) loading.value = false
    }
  }

  watch([isActive, () => getConfig().configId, () => getConfig().apiUrl, () => getConfig().apiKey], () => {
    ++ticket
    clearTimeout(timer)
    models.value = []
    error.value = false
    loading.value = canLoad.value
    if (canLoad.value) timer = setTimeout(() => { void refresh() }, 350)
  }, { immediate: true })

  const options = computed<LlmModelOption[]>(() => {
    const config = getConfig()
    const visible = config.modelType === 'vision'
      ? models.value.filter(model => model.inputModalities.includes('image'))
      : config.modelType === 'embedding' ? [] : models.value
    const choices = visible.map(model => ({ value: model.id,
      label: model.label === model.id ? model.id : `${model.label} (${model.id})` }))
    if (config.modelType !== 'embedding' && (config.modelType !== 'vision'
      || visible.some(model => model.defaultModel))) {
      choices.unshift({ value: 'codex-default', label: 'codex-default' })
    }
    // A saved model stays visible even if it has since left the account's catalog.
    if (config.configName && !choices.some(choice => choice.value === config.configName)) {
      choices.push({ value: config.configName, label: config.configName })
    }
    return choices
  })

  const selectedModel = computed(() => getConfig().configName === 'codex-default'
    ? models.value.find(model => model.defaultModel) ?? models.value[0]
    : models.value.find(model => model.id === getConfig().configName))
  const reasoningEfforts = computed(() => selectedModel.value?.reasoningEfforts ?? [])

  onScopeDispose(() => { ++ticket; clearTimeout(timer) })
  return { models, options, selectedModel, reasoningEfforts, loading, error, canLoad, refresh }
}
